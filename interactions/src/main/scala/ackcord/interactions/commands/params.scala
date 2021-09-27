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
import cats.instances.either._
import cats.instances.option._
import cats.syntax.all._

sealed trait Param[Orig, A, F[_]] {
  def tpe: ApplicationCommandOptionType.Aux[Orig]
  def name: String

  private[commands] def fTransformer: Param.FTransformer[F]
  private[commands] def isRequired: Boolean =
    fTransformer == Param.FTransformer.Required
  private[commands] def optionToFa(
      opt: Option[Orig],
      resolved: ApplicationCommandInteractionDataResolved
  ): Either[String, F[A]]

  def mapWithResolve[B](
      f: (Orig, ApplicationCommandInteractionDataResolved) => Option[B]
  ): Param[Orig, B, F]

  def ~[B, G[_]](other: Param[Orig, B, G]): ParamList[F[A] ~ G[B]] =
    ParamList.ParamListStart(this) ~ other

  def toCommandOption: ApplicationCommandOption
}
object Param {
  sealed private[commands] trait FTransformer[F[_]] {
    def apply[A](opt: Option[A]): Either[String, F[A]]
  }
  object FTransformer {
    case object Required extends FTransformer[Id] {
      override def apply[A](opt: Option[A]): Either[String, A] = opt match {
        case Some(value) => Right(value)
        case None        => Left("Missing required parameter")
      }
    }
    case object Optional extends FTransformer[Option] {
      override def apply[A](opt: Option[A]): Either[String, Option[A]] = Right(
        opt
      )
    }
  }
}

case class ChoiceParam[Orig, A, F[_]] private[interactions] (
    tpe: ApplicationCommandOptionType.Aux[Orig],
    name: String,
    description: String,
    fTransformer: Param.FTransformer[F],
    choices: Map[String, A],
    makeOptionChoice: (String, Orig) => ApplicationCommandOptionChoice,
    map: (Orig, ApplicationCommandInteractionDataResolved) => Option[A],
    contramap: A => Orig
) extends Param[Orig, A, F] {

  def withChoices(choices: Map[String, A]): ChoiceParam[Orig, A, F] =
    copy(choices = choices)
  def withChoices(choices: Seq[String])(implicit
      ev: String =:= A
  ): ChoiceParam[Orig, A, F] =
    withChoices(choices.map(s => s -> ev(s)).toMap)

  def required: ChoiceParam[Orig, A, Id] =
    copy(fTransformer = Param.FTransformer.Required)
  def notRequired: ChoiceParam[Orig, A, Option] =
    copy(fTransformer = Param.FTransformer.Optional)

  def map[B](f: A => B): ValueParam[Orig, B, F] = ValueParam(
    tpe,
    name,
    description,
    fTransformer,
    map = (orig, resolved) => this.map(orig, resolved).map(f)
  )

  def imap[B](map: A => B)(contramap: B => A): ChoiceParam[Orig, B, F] =
    copy(
      choices = choices.map(t => t._1 -> map(t._2)),
      map = (orig, resolved) => this.map(orig, resolved).map(map),
      contramap = this.contramap.compose(contramap)
    )

  override def mapWithResolve[B](
      f: (Orig, ApplicationCommandInteractionDataResolved) => Option[B]
  ): ValueParam[Orig, B, F] = ValueParam(
    tpe,
    name,
    description,
    fTransformer,
    map = f
  )

  override private[commands] def optionToFa(
      opt: Option[Orig],
      resolved: ApplicationCommandInteractionDataResolved
  ): Either[String, F[A]] = {
    val mappedOpt =
      opt.traverse(orig => map(orig, resolved).toRight(s"Unknown object $orig"))
    mappedOpt.flatMap(fTransformer(_))
  }

  override def toCommandOption: ApplicationCommandOption =
    ApplicationCommandOption(
      tpe,
      name,
      description,
      Some(fTransformer == Param.FTransformer.Required),
      Some(choices.map(t => makeOptionChoice(t._1, contramap(t._2))).toSeq),
      Some(Nil)
    )
}
object ChoiceParam {
  private[interactions] def default[A](
      tpe: ApplicationCommandOptionType.Aux[A],
      name: String,
      description: String,
      makeOptionChoice: (String, A) => ApplicationCommandOptionChoice
  ): ChoiceParam[A, A, Id] =
    ChoiceParam(
      tpe,
      name,
      description,
      fTransformer = Param.FTransformer.Required,
      Map.empty,
      makeOptionChoice,
      (a, _) => Some(a),
      identity
    )
}

case class ValueParam[Orig, A, F[_]] private[interactions] (
    tpe: ApplicationCommandOptionType.Aux[Orig],
    name: String,
    description: String,
    fTransformer: Param.FTransformer[F],
    map: (Orig, ApplicationCommandInteractionDataResolved) => Option[A]
) extends Param[Orig, A, F] {
  def required: ValueParam[Orig, A, Id] =
    copy(fTransformer = Param.FTransformer.Required)
  def notRequired: ValueParam[Orig, A, Option] =
    copy(fTransformer = Param.FTransformer.Optional)
  def map[B](f: A => B): ValueParam[Orig, B, F] =
    copy(map = (orig, resolve) => this.map(orig, resolve).map(f))

  override def mapWithResolve[B](
      f: (Orig, ApplicationCommandInteractionDataResolved) => Option[B]
  ): ValueParam[Orig, B, F] =
    copy(map = f)

  override private[commands] def optionToFa(
      opt: Option[Orig],
      resolved: ApplicationCommandInteractionDataResolved
  ): Either[String, F[A]] = {
    val mappedOpt =
      opt.traverse(orig => map(orig, resolved).toRight(s"Unknown object $orig"))
    mappedOpt.flatMap(fTransformer(_))
  }

  override def toCommandOption: ApplicationCommandOption =
    ApplicationCommandOption(
      tpe,
      name,
      description,
      Some(fTransformer == Param.FTransformer.Required),
      Some(Nil),
      Some(Nil)
    )
}
object ValueParam {
  private[interactions] def default[A](
      tpe: ApplicationCommandOptionType.Aux[A],
      name: String,
      description: String
  ): ValueParam[A, A, Id] =
    ValueParam(
      tpe,
      name,
      description,
      fTransformer = Param.FTransformer.Required,
      (a, _) => Some(a)
    )
}

sealed trait ParamList[A] {
  def ~[B, G[_]](param: Param[_, B, G]): ParamList[A ~ G[B]] =
    ParamList.ParamListBranch(this, param)
  def map[B](f: Param[_, _, Any] => B): List[B] =
    foldRight(Nil: List[B])(f(_) :: _)

  protected def constructParam[Orig, A1, F[_]](
      param: Param[Orig, A1, F],
      dataOption: Option[ApplicationCommandInteractionDataOption[_]],
      resolved: ApplicationCommandInteractionDataResolved
  ): Either[String, F[A1]] = {
    dataOption match {
      case Some(dataOption) if dataOption.tpe == param.tpe =>
        val castedDataOption =
          dataOption.asInstanceOf[ApplicationCommandInteractionDataOption[Orig]]
        param.optionToFa(castedDataOption.value, resolved)

      case Some(_) => Left("Got invalid parameter type")

      case None => param.optionToFa(None, resolved)
    }
  }

  def constructValues(
      options: Map[String, ApplicationCommandInteractionDataOption[_]],
      resolved: ApplicationCommandInteractionDataResolved
  ): Either[String, A]

  def foldRight[B](start: B)(f: (Param[_, _, Any], B) => B): B = {
    @tailrec
    def inner(list: ParamList[_], b: B): B = list match {
      case startParam: ParamList.ParamListStart[_, _, Any @unchecked] =>
        f(startParam.leaf, b)
      case branchParam: ParamList.ParamListBranch[_, _, Any @unchecked] =>
        inner(branchParam.left, f(branchParam.right, b))
    }

    inner(this, start)
  }
}
object ParamList {
  implicit def paramToParamList[A, F[_]](
      param: Param[_, A, F]
  ): ParamList[F[A]] = ParamListStart(param)

  case class ParamListStart[Orig, A, F[_]](leaf: Param[Orig, A, F])
      extends ParamList[F[A]] {
    override def constructValues(
        options: Map[String, ApplicationCommandInteractionDataOption[_]],
        resolved: ApplicationCommandInteractionDataResolved
    ): Either[String, F[A]] =
      constructParam(
        leaf,
        options.get(leaf.name.toLowerCase(Locale.ROOT)),
        resolved
      )
  }

  case class ParamListBranch[T, B, G[_]](
      left: ParamList[T],
      right: Param[_, B, G]
  ) extends ParamList[T ~ G[B]] {
    override def constructValues(
        options: Map[String, ApplicationCommandInteractionDataOption[_]],
        resolved: ApplicationCommandInteractionDataResolved
    ): Either[String, (T, G[B])] =
      for {
        left <- left.constructValues(options, resolved)
        right <- constructParam(
          right,
          options.get(right.name.toLowerCase(Locale.ROOT)),
          resolved
        )
      } yield (left, right)
  }
}
