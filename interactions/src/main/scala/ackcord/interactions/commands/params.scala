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

/**
  * A parameter that can be used in a slash command.
  * @tparam Orig
  *   The type the parameter originally has.
  * @tparam A
  *   The type the parameter has after a bit of processing.
  * @tparam F
  *   The context of the parameter, either Id or Option currently.
  */
sealed trait Param[Orig, A, F[_]] {

  /** Type of the parameter. */
  def tpe: ApplicationCommandOptionType.Aux[Orig]

  /** Name of the parameter. */
  def name: String

  private[commands] def fTransformer: Param.FTransformer[F]
  private[commands] def isRequired: Boolean = fTransformer == Param.FTransformer.Required
  private[commands] def optionToFa(
      opt: Option[Orig],
      resolved: ApplicationCommandInteractionDataResolved
  ): Either[String, F[A]]

  /** imap the parameter with the resolved data. */
  def imapWithResolve[B](map: (A, ApplicationCommandInteractionDataResolved) => Option[B])(
      contramap: B => A
  ): Param[Orig, B, F]

  /** imap the parameter. */
  def imap[B](map: A => B)(contramap: B => A): Param[Orig, B, F]

  /** Chain this parameter together with another. */
  def ~[B, G[_]](other: Param[_, B, G]): ParamList[F[A] ~ G[B]] = ParamList.ParamListStart(this) ~ other

  /** Convert this parameter to an [[ApplicationCommandOption]] */
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
      override def apply[A](opt: Option[A]): Either[String, Option[A]] = Right(opt)
    }
  }
}

/**
  * A parameter that allows multiple choices for the user.
  * @param tpe
  *   Type of the parameter.
  * @param name
  *   Name of the parameter.
  * @param description
  *   Description of the parameter.
  * @param choices
  *   The choices of the parameter. Can't be specified at the same time as
  *   autocomplete.
  * @param autocomplete
  *   An function to return autocomplete options to the user as they type. Can't
  *   be used at the same time as choices.
  * @param minValue
  *   The minimum value of the parameter.
  * @param maxValue
  *   The maximum value of the parameter.
  * @tparam Orig
  *   The type the parameter originally has.
  * @tparam A
  *   The type the parameter has after a bit of processing.
  * @tparam F
  *   The context of the parameter, either Id or Option currently.
  */
case class ChoiceParam[Orig, A, F[_]] private[interactions] (
    tpe: ApplicationCommandOptionType.Aux[Orig],
    name: String,
    description: String,
    fTransformer: Param.FTransformer[F],
    choices: Map[String, Orig],
    autocomplete: Option[String => Seq[Orig]],
    minValue: Option[Either[Int, Double]],
    maxValue: Option[Either[Int, Double]],
    makeOptionChoice: (String, Orig) => ApplicationCommandOptionChoice,
    map: (Orig, ApplicationCommandInteractionDataResolved) => Option[A],
    contramap: A => Orig
) extends Param[Orig, A, F] {
  require(choices.nonEmpty && autocomplete.isEmpty || choices.isEmpty, "Can't use both autocomplete and static choices")

  /**
    * Sets the choices for the parameter. Can't be set at the same time as
    * autocomplete.
    */
  def withChoices(choices: Map[String, A]): ChoiceParam[Orig, A, F] =
    copy(choices = choices.map(t => t._1 -> contramap(t._2)))

  /**
    * Sets the choices for the parameter. Can't be set at the same time as
    * autocomplete.
    */
  def withChoices(choices: Seq[String])(implicit ev: String =:= A): ChoiceParam[Orig, A, F] =
    withChoices(choices.map(s => s -> ev(s)).toMap)

  /** Sets the parameter as required. */
  def required: ChoiceParam[Orig, A, Id] = copy(fTransformer = Param.FTransformer.Required)

  /** Sets the parameter as optional. */
  def notRequired: ChoiceParam[Orig, A, Option] = copy(fTransformer = Param.FTransformer.Optional)

  /**
    * Sets the autocomplete to use for the parameter. Can't be set at the same
    * time as choices.
    */
  def withAutocomplete(complete: String => Seq[A]): ChoiceParam[Orig, A, F] =
    copy(autocomplete = Some(complete.andThen(_.map(contramap))))
  def noAutocomplete: ChoiceParam[Orig, A, F] = copy(autocomplete = None)

  override def imapWithResolve[B](
      map: (A, ApplicationCommandInteractionDataResolved) => Option[B]
  )(contramap: B => A): ChoiceParam[Orig, B, F] =
    copy(
      map = (orig, resolved) => this.map(orig, resolved).flatMap(a => map(a, resolved)),
      contramap = this.contramap.compose(contramap)
    )

  override def imap[B](map: A => B)(contramap: B => A): ChoiceParam[Orig, B, F] =
    imapWithResolve((a, _) => Some(map(a)))(contramap)

  override private[commands] def optionToFa(
      opt: Option[Orig],
      resolved: ApplicationCommandInteractionDataResolved
  ): Either[String, F[A]] = {
    val mappedOpt = opt.traverse(orig => map(orig, resolved).toRight(s"Unknown object $orig"))
    mappedOpt.flatMap(fTransformer(_))
  }

  override def toCommandOption: ApplicationCommandOption = ApplicationCommandOption(
    tpe,
    name,
    description,
    Some(fTransformer == Param.FTransformer.Required),
    Some(choices.map(t => makeOptionChoice(t._1, t._2)).toSeq),
    Some(autocomplete.isDefined),
    Some(Nil),
    None,
    minValue,
    maxValue
  )
}
object ChoiceParam {
  private[interactions] def default[A](
      tpe: ApplicationCommandOptionType.Aux[A],
      name: String,
      description: String,
      minValue: Option[Either[Int, Double]],
      maxValue: Option[Either[Int, Double]],
      makeOptionChoice: (String, A) => ApplicationCommandOptionChoice
  ): ChoiceParam[A, A, Id] =
    ChoiceParam(
      tpe,
      name,
      description,
      fTransformer = Param.FTransformer.Required,
      Map.empty,
      None,
      minValue,
      maxValue,
      makeOptionChoice,
      (a, _) => Some(a),
      identity
    )
}

/**
  * A parameter with a normal value..
  * @param tpe
  *   Type of the parameter.
  * @param name
  *   Name of the parameter.
  * @param description
  *   Description of the parameter.
  * @param channelTypes
  *   The channel types to accept, if this is a channel parameter.
  * @tparam Orig
  *   The type the parameter originally has.
  * @tparam A
  *   The type the parameter has after a bit of processing.
  * @tparam F
  *   The context of the parameter, either Id or Option currently.
  */
case class ValueParam[Orig, A, F[_]] private[interactions] (
    tpe: ApplicationCommandOptionType.Aux[Orig],
    name: String,
    description: String,
    fTransformer: Param.FTransformer[F],
    channelTypes: Seq[ChannelType],
    map: (Orig, ApplicationCommandInteractionDataResolved) => Option[A],
    contramap: A => Orig
) extends Param[Orig, A, F] {

  /** Sets the parameter as required. */
  def required: ValueParam[Orig, A, Id] = copy(fTransformer = Param.FTransformer.Required)

  /** Sets the parameter as optional. */
  def notRequired: ValueParam[Orig, A, Option] = copy(fTransformer = Param.FTransformer.Optional)

  override def imapWithResolve[B](
      map: (A, ApplicationCommandInteractionDataResolved) => Option[B]
  )(contramap: B => A): ValueParam[Orig, B, F] =
    copy(
      map = (orig, resolved) => this.map(orig, resolved).flatMap(a => map(a, resolved)),
      contramap = this.contramap.compose(contramap)
    )

  override def imap[B](map: A => B)(contramap: B => A): ValueParam[Orig, B, F] =
    imapWithResolve((a, _) => Some(map(a)))(contramap)

  override private[commands] def optionToFa(
      opt: Option[Orig],
      resolved: ApplicationCommandInteractionDataResolved
  ): Either[String, F[A]] = {
    val mappedOpt = opt.traverse(orig => map(orig, resolved).toRight(s"Unknown object $orig"))
    mappedOpt.flatMap(fTransformer(_))
  }

  override def toCommandOption: ApplicationCommandOption = ApplicationCommandOption(
    tpe,
    name,
    description,
    Some(fTransformer == Param.FTransformer.Required),
    Some(Nil),
    None,
    Some(Nil),
    Some(channelTypes),
    None,
    None
  )
}
object ValueParam {
  private[interactions] def default[A](
      tpe: ApplicationCommandOptionType.Aux[A],
      name: String,
      description: String,
      channelTypes: Seq[ChannelType]
  ): ValueParam[A, A, Id] =
    ValueParam(
      tpe,
      name,
      description,
      fTransformer = Param.FTransformer.Required,
      channelTypes,
      (a, _) => Some(a),
      identity
    )
}

sealed trait ParamList[A] {

  /**
    * Adds the given parameter to the end of this parameter list.
    * @param param
    *   The parameter to add.
    */
  def ~[B, G[_]](param: Param[_, B, G]): ParamList[A ~ G[B]] = ParamList.ParamListBranch(this, param)

  /** Map all the parameters in this parameter list. */
  def map[B](f: Param[_, _, Any] => B): List[B] = foldRight(Nil: List[B])(f(_) :: _)

  protected def constructParam[Orig, A1, F[_]](
      param: Param[Orig, A1, F],
      dataOption: Option[ApplicationCommandInteractionDataOption[_]],
      resolved: ApplicationCommandInteractionDataResolved
  ): Either[String, F[A1]] = {
    dataOption match {
      case Some(dataOption) if dataOption.tpe == param.tpe =>
        val castedDataOption = dataOption.asInstanceOf[ApplicationCommandInteractionDataOption[Orig]]
        param.optionToFa(castedDataOption.value, resolved)

      case Some(_) => Left("Got invalid parameter type")

      case None => param.optionToFa(None, resolved)
    }
  }

  protected def constructAutocomplete[Orig, A1, F[_]](
      param: Param[Orig, A1, F],
      dataOption: Option[ApplicationCommandInteractionDataOption[_]]
  ): Seq[ApplicationCommandOptionChoice] = {
    val res = for {
      choiceParam <- param match {
        case param: ChoiceParam[Orig, A1, F] => Some(param)
        case _                               => None
      }
      completeFunction <- choiceParam.autocomplete
      option           <- dataOption
      if option.focused.contains(true)
    } yield completeFunction(option.value.fold("")(_.toString)).map(orig =>
      choiceParam.makeOptionChoice(orig.toString, orig)
    )

    res.toSeq.flatten
  }

  /** Construct values from the specified interaction data. */
  def constructValues(
      options: Map[String, ApplicationCommandInteractionDataOption[_]],
      resolved: ApplicationCommandInteractionDataResolved
  ): Either[String, A]

  /** Runs autocomplete with the specified interaction data. */
  def runAutocomplete(
      options: Map[String, ApplicationCommandInteractionDataOption[_]]
  ): Seq[ApplicationCommandOptionChoice]

  /**
    * Fold this parameter list.
    */
  def foldRight[B](start: B)(f: (Param[_, _, Any], B) => B): B = {
    @tailrec
    def inner(list: ParamList[_], b: B): B = list match {
      case startParam: ParamList.ParamListStart[_, _, Any @unchecked] => f(startParam.leaf, b)
      case branchParam: ParamList.ParamListBranch[_, _, Any @unchecked] =>
        inner(branchParam.left, f(branchParam.right, b))
    }

    inner(this, start)
  }
}
object ParamList {
  implicit def paramToParamList[A, F[_]](param: Param[_, A, F]): ParamList[F[A]] = ParamListStart(param)

  case class ParamListStart[Orig, A, F[_]](leaf: Param[Orig, A, F]) extends ParamList[F[A]] {
    override def constructValues(
        options: Map[String, ApplicationCommandInteractionDataOption[_]],
        resolved: ApplicationCommandInteractionDataResolved
    ): Either[String, F[A]] =
      constructParam(leaf, options.get(leaf.name.toLowerCase(Locale.ROOT)), resolved)

    override def runAutocomplete(
        options: Map[String, ApplicationCommandInteractionDataOption[_]]
    ): Seq[ApplicationCommandOptionChoice] =
      constructAutocomplete(leaf, options.get(leaf.name.toLowerCase(Locale.ROOT)))
  }

  case class ParamListBranch[T, B, G[_]](left: ParamList[T], right: Param[_, B, G]) extends ParamList[T ~ G[B]] {
    override def constructValues(
        options: Map[String, ApplicationCommandInteractionDataOption[_]],
        resolved: ApplicationCommandInteractionDataResolved
    ): Either[String, (T, G[B])] =
      for {
        left  <- left.constructValues(options, resolved)
        right <- constructParam(right, options.get(right.name.toLowerCase(Locale.ROOT)), resolved)
      } yield (left, right)

    override def runAutocomplete(
        options: Map[String, ApplicationCommandInteractionDataOption[_]]
    ): Seq[ApplicationCommandOptionChoice] =
      left.runAutocomplete(options) ++ constructAutocomplete(right, options.get(right.name.toLowerCase(Locale.ROOT)))
  }
}
