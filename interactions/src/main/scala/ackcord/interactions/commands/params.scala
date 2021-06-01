/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2020 Katrix
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
package ackcord.interactions.commands

import scala.language.implicitConversions

import java.util.Locale

import scala.annotation.tailrec

import ackcord.data._
import ackcord.interactions.~
import cats.Id
import cats.arrow.FunctionK
import cats.syntax.either._
import io.circe.{DecodingFailure, Json}

sealed trait Param[A, F[_]] {
  def name: String
  def isRequired: Boolean

  def ~[B, G[_]](other: Param[B, G]): ParamList[F[A] ~ G[B]] = ParamList.ParamListStart(this) ~ other

  def decode(payload: Option[Json]): Either[String, F[A]]
  def toCommandOption: ApplicationCommandOption
}
object Param {
  type DecodePayloadFunction[F[_]] = FunctionK[λ[B => Json => Either[DecodingFailure, B]], λ[B => Either[String, F[B]]]]

  private[interactions] val decodePayloadRequired: Option[Json] => DecodePayloadFunction[Id] = {
    case Some(json) =>
      new DecodePayloadFunction[Id] {
        override def apply[A](f: Json => Either[DecodingFailure, A]): Either[String, Id[A]] = f(json).leftMap(_.message)
      }
    case None =>
      new DecodePayloadFunction[Id] {
        override def apply[A](fa: Json => Either[DecodingFailure, A]): Either[String, Id[A]] =
          Left("Missing required parameter")
      }
  }

  private[interactions] val decodePayloadNotRequired: Option[Json] => DecodePayloadFunction[Option] = {
    case None =>
      new DecodePayloadFunction[Option] {
        override def apply[A](fa: Json => Either[DecodingFailure, A]): Either[String, Option[A]] = Right(None)
      }
    case Some(value) =>
      new DecodePayloadFunction[Option] {
        override def apply[A](f: Json => Either[DecodingFailure, A]): Either[String, Option[A]] =
          f(value).map(Some(_)).leftMap(_.message)
      }
  }
}

case class ChoiceParam[Orig, A, F[_]] private[interactions](
    tpe: ApplicationCommandOptionType,
    name: String,
    description: String,
    isRequired: Boolean,
    choices: Map[String, A],
    makeOptionChoice: (String, Orig) => ApplicationCommandOptionChoice,
    decodePayloadInner: Json => Either[DecodingFailure, A],
    decodePayload: Option[Json] => Param.DecodePayloadFunction[F],
    map: Orig => A,
    contramap: A => Orig
) extends Param[A, F] {

  def withChoices(choices: Map[String, A]): ChoiceParam[Orig, A, F] = copy(choices = choices)
  def withChoices(choices: Seq[String])(implicit ev: String =:= A): ChoiceParam[Orig, A, F] =
    withChoices(choices.map(s => s -> ev(s)).toMap)

  def required: ChoiceParam[Orig, A, Id] = copy(
    isRequired = true,
    decodePayload = Param.decodePayloadRequired
  )
  def notRequired: ChoiceParam[Orig, A, Option] = copy(
    isRequired = false,
    decodePayload = Param.decodePayloadNotRequired
  )

  def map[B](f: A => B): ValueParam[Orig, B, F] = ValueParam(
    tpe,
    name,
    description,
    isRequired,
    decodePayloadInner.andThen(_.map(f)),
    decodePayload,
    this.map.andThen(f)
  )

  def imap[B](map: A => B)(contramap: B => A): ChoiceParam[Orig, B, F] =
    copy(
      choices = choices.map(t => t._1 -> map(t._2)),
      map = this.map.andThen(map),
      contramap = this.contramap.compose(contramap),
      decodePayloadInner = decodePayloadInner.andThen(_.map(map))
    )

  override def toCommandOption: ApplicationCommandOption = ApplicationCommandOption(
    tpe,
    name,
    description,
    Some(isRequired),
    Some(choices.map(t => makeOptionChoice(t._1, contramap(t._2))).toSeq),
    Some(Nil)
  )

  override def decode(payload: Option[Json]): Either[String, F[A]] = decodePayload(payload)(decodePayloadInner)
}
object ChoiceParam {
  private[interactions] def default[A](
      tpe: ApplicationCommandOptionType,
      name: String,
      description: String,
      makeOptionChoice: (String, A) => ApplicationCommandOptionChoice,
      decodePayloadInner: Json => Either[DecodingFailure, A]
  ): ChoiceParam[A, A, Id] =
    ChoiceParam(
      tpe,
      name,
      description,
      isRequired = true,
      Map.empty,
      makeOptionChoice,
      decodePayloadInner,
      Param.decodePayloadRequired,
      identity,
      identity
    )
}

case class ValueParam[Orig, A, F[_]] private[interactions](
    tpe: ApplicationCommandOptionType,
    name: String,
    description: String,
    isRequired: Boolean,
    decodePayloadInner: Json => Either[DecodingFailure, A],
    decodePayload: Option[Json] => Param.DecodePayloadFunction[F],
    map: Orig => A
) extends Param[A, F] {
  def required: ValueParam[Orig, A, Id] =
    copy(isRequired = true, decodePayload = Param.decodePayloadRequired)
  def notRequired: ValueParam[Orig, A, Option] =
    copy(isRequired = false, decodePayload = Param.decodePayloadNotRequired)

  def map[B](f: A => B): ValueParam[Orig, B, F] =
    copy(map = this.map.andThen(f), decodePayloadInner = decodePayloadInner.andThen(_.map(f)))

  override def toCommandOption: ApplicationCommandOption = ApplicationCommandOption(
    tpe,
    name,
    description,
    Some(isRequired),
    Some(Nil),
    Some(Nil)
  )

  override def decode(payload: Option[Json]): Either[String, F[A]] = decodePayload(payload)(decodePayloadInner)
}
object ValueParam {
  private[interactions] def default[A](
      tpe: ApplicationCommandOptionType,
      name: String,
      description: String,
      decodePayloadInner: Json => Either[DecodingFailure, A]
  ): ValueParam[A, A, Id] =
    ValueParam(
      tpe,
      name,
      description,
      isRequired = true,
      decodePayloadInner,
      Param.decodePayloadRequired,
      identity
    )
}

sealed trait ParamList[A] {
  def ~[B, G[_]](param: Param[B, G]): ParamList[A ~ G[B]] = ParamList.ParamListBranch(this, param)
  def map[B](f: Param[_, Any] => B): List[B]              = foldRight(Nil: List[B])(f(_) :: _)

  def constructValues(options: Map[String, Json]): Either[String, A] = this match {
    case start: ParamList.ParamListStart[a, f] =>
      start.leaf.decode(options.get(start.leaf.name.toLowerCase(Locale.ROOT))).map(_.asInstanceOf[A])

    case branch: ParamList.ParamListBranch[t, b, g] =>
      for {
        left  <- branch.left.constructValues(options)
        right <- branch.right.decode(options.get(branch.right.name.toLowerCase(Locale.ROOT)))
      } yield (left, right).asInstanceOf[A]
  }

  def foldRight[B](start: B)(f: (Param[_, Any], B) => B): B = {
    @tailrec
    def inner(list: ParamList[_], b: B): B = list match {
      case startParam: ParamList.ParamListStart[_, Any @unchecked] => f(startParam.leaf, b)
      case branchParam: ParamList.ParamListBranch[_, _, Any @unchecked] =>
        inner(branchParam.left, f(branchParam.right, b))
    }

    inner(this, start)
  }
}
object ParamList {
  implicit def paramToParamList[A, F[_]](param: Param[A, F]): ParamList[F[A]] = ParamListStart(param)

  case class ParamListStart[A, F[_]](leaf: Param[A, F])                          extends ParamList[F[A]]
  case class ParamListBranch[T, B, G[_]](left: ParamList[T], right: Param[B, G]) extends ParamList[T ~ G[B]]
}
