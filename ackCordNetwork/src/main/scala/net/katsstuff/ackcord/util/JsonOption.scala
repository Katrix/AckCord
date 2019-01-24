/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2019 Katrix
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
package net.katsstuff.ackcord.util

import io.circe._

object JsonOption {

  implicit def decodeRestOption[A](implicit decodeOpt: Decoder[Option[A]]): Decoder[JsonOption[A]] =
    Decoder.withReattempt { c =>
      if (c.succeeded) c.as[Option[A]].right.map(fromOptionWithNull) else Right(JsonUndefined)
    }

  def fromOptionWithNull[A](opt: Option[A]): JsonOption[A] = opt.fold[JsonOption[A]](JsonNull)(JsonSome.apply)

  def fromOptionWithUndefined[A](opt: Option[A]): JsonOption[A] = opt.fold[JsonOption[A]](JsonUndefined)(JsonSome.apply)

  def removeUndefined[A](seq: Seq[(String, JsonOption[Json])]): Seq[(String, Json)] = seq.flatMap {
    case (name, JsonSome(json)) => Some(name -> json)
    case (name, JsonNull)       => Some(name -> Json.Null)
    case (_, JsonUndefined)     => None
  }

  def removeUndefinedToObj(seq: (String, JsonOption[Json])*): Json = Json.obj(removeUndefined(seq): _*)
}
sealed trait JsonOption[+A] {

  def isNull: Boolean
  def isUndefined: Boolean
  def isEmpty: Boolean
  def nonEmpty: Boolean = !isEmpty

  def toOption: Option[A]

  def fold[B](ifNull: => B, ifUndefined: => B)(f: A => B): B

  def map[B](f: A => B): JsonOption[B]
  def flatMap[B](f: A => JsonOption[B]): JsonOption[B]

  def contains[A1 >: A](value: A1): Boolean
  def exists[A1 >: A](f: A1 => Boolean): Boolean
  def forall[A1 >: A](f: A1 => Boolean): Boolean

  def foreach[A1 >: A](f: A1 => Unit): Unit

  def getOrElse[B >: A](other: => B): B
  def getOrElseIfUndefined[B >: A](other: => B): Option[B]
  def orElse[B >: A](other: => JsonOption[B]): JsonOption[B]
  def orElseIfUndefined[B >: A](other: => Option[B]): Option[B]

  def toList[A1 >: A]: List[A]
}
case class JsonSome[A](value: A) extends JsonOption[A] {
  override def isNull: Boolean      = false
  override def isUndefined: Boolean = false
  override def isEmpty: Boolean     = false

  override def toOption: Option[A] = Some(value)

  override def fold[B](ifNull: => B, ifUndefined: => B)(f: A => B): B = f(value)

  override def map[B](f: A => B): JsonOption[B]                 = JsonSome(f(value))
  override def flatMap[B](f: A => JsonOption[B]): JsonOption[B] = f(value)

  override def contains[A1 >: A](value: A1): Boolean      = this.value == value
  override def exists[A1 >: A](f: A1 => Boolean): Boolean = f(value)
  override def forall[A1 >: A](f: A1 => Boolean): Boolean = f(value)

  override def foreach[A1 >: A](f: A1 => Unit): Unit = f(value)

  override def getOrElse[B >: A](other: => B): B                         = value
  override def getOrElseIfUndefined[B >: A](other: => B): Option[B]      = Some(value)
  override def orElse[B >: A](other: => JsonOption[B]): JsonOption[B]    = this
  override def orElseIfUndefined[B >: A](other: => Option[B]): Option[B] = Some(value)

  override def toList[A1 >: A]: List[A] = List(value)
}

case object JsonNull extends JsonOption[Nothing] {
  override def isNull: Boolean      = true
  override def isUndefined: Boolean = false
  override def isEmpty: Boolean     = true

  override def toOption: Option[Nothing] = None

  override def fold[B](ifNull: => B, ifUndefined: => B)(f: Nothing => B): B = ifNull

  override def map[B](f: Nothing => B): JsonOption[B]                 = this
  override def flatMap[B](f: Nothing => JsonOption[B]): JsonOption[B] = this

  override def contains[A1 >: Nothing](value: A1): Boolean      = false
  override def exists[A1 >: Nothing](f: A1 => Boolean): Boolean = false
  override def forall[A1 >: Nothing](f: A1 => Boolean): Boolean = true

  override def foreach[A1 >: Nothing](f: A1 => Unit): Unit = ()

  override def getOrElse[B >: Nothing](other: => B): B                         = other
  override def getOrElseIfUndefined[B >: Nothing](other: => B): Option[B]      = None
  override def orElse[B >: Nothing](other: => JsonOption[B]): JsonOption[B]    = other
  override def orElseIfUndefined[B >: Nothing](other: => Option[B]): Option[B] = None

  override def toList[A1 >: Nothing]: List[Nothing] = Nil
}

case object JsonUndefined extends JsonOption[Nothing] {
  override def isNull: Boolean      = false
  override def isUndefined: Boolean = true
  override def isEmpty: Boolean     = true

  override def toOption: Option[Nothing] = None

  override def fold[B](ifNull: => B, ifUndefined: => B)(f: Nothing => B): B = ifUndefined

  override def map[B](f: Nothing => B): JsonOption[B]                 = this
  override def flatMap[B](f: Nothing => JsonOption[B]): JsonOption[B] = this

  override def contains[A1 >: Nothing](value: A1): Boolean      = false
  override def exists[A1 >: Nothing](f: A1 => Boolean): Boolean = false
  override def forall[A1 >: Nothing](f: A1 => Boolean): Boolean = true

  override def foreach[A1 >: Nothing](f: A1 => Unit): Unit = ()

  override def getOrElse[B >: Nothing](other: => B): B                         = other
  override def getOrElseIfUndefined[B >: Nothing](other: => B): Option[B]      = Some(other)
  override def orElse[B >: Nothing](other: => JsonOption[B]): JsonOption[B]    = other
  override def orElseIfUndefined[B >: Nothing](other: => Option[B]): Option[B] = other

  override def toList[A1 >: Nothing]: List[Nothing] = Nil
}
