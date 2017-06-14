/*
 * This file is part of AkkaCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2017 Katrix
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package net.katsstuff.akkacord.http

import cats.data.Validated
import io.circe.Decoder.Result
import io.circe._

sealed trait JsonOption[+A] {
  def map[B](f: A => B): JsonOption[B] = this match {
    case JsonSome(a) => JsonSome(f(a))
    case JsonNull    => JsonNull
    case JsonNone    => JsonNone
  }

  def flatMap[B](f: A => JsonOption[B]): JsonOption[B] = this match {
    case JsonSome(a) => f(a)
    case JsonNull    => JsonNull
    case JsonNone    => JsonNone
  }

  def filter(f: A => Boolean): JsonOption[A] = this match {
    case JsonSome(a) => if (f(a)) JsonSome(a) else JsonNone
    case JsonNull    => JsonNull
    case JsonNone    => JsonNone
  }
}
object JsonOption {
  def apply[A](json: JsonObject, key: String)(implicit decoder: Decoder[A]): JsonOption[A] = {
    json(key) match {
      case None            => JsonNone
      case Some(Json.Null) => JsonNull
      case Some(v1) =>
        v1.as[A] match {
          case Right(v2) => JsonSome(v2)
          case Left(_)   => JsonNone
        }
    }
  }

  implicit def decoder[A: Decoder] = new Decoder[JsonOption[A]] {
    override def apply(c: HCursor): Result[JsonOption[A]] =
      if (c.value.isNull) Right(JsonNull)
      else implicitly[Decoder[A]].apply(c).map(JsonSome.apply)

    override def tryDecode(c: ACursor): Result[JsonOption[A]] = c match {
      case hc: HCursor   => apply(hc)
      case _ if c.failed => Right(JsonNone)
    }

    override def tryDecodeAccumulating(c: ACursor): AccumulatingDecoder.Result[JsonOption[A]] = c match {
      case hc: HCursor =>
        apply(hc) match {
          case Right(v) => Validated.valid(v)
          case Left(e)  => Validated.invalidNel(e)
        }
      case _ => Validated.valid(JsonNone)
    }
  }

  implicit def encoder[A: Encoder] = new Encoder[JsonOption[A]] {
    override def apply(a: JsonOption[A]): Json = a match {
      case JsonNull    => Json.Null
      case JsonNone    => Json.Null
      case JsonSome(v) => implicitly[Encoder[A]].apply(v)
    }
  }
}

case object JsonNull             extends JsonOption[Nothing]
case object JsonNone             extends JsonOption[Nothing]
case class JsonSome[A](value: A) extends JsonOption[A]
