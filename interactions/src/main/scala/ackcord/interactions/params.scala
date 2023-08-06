//noinspection ScalaUnusedSymbol, ScalaWeakerAccess
package ackcord.interactions

import scala.language.implicitConversions

import java.util.Locale

import scala.annotation.tailrec

import ackcord.data._
import ackcord.interactions.data.ApplicationCommand.ApplicationCommandOption
import ackcord.interactions.data.ApplicationCommand.ApplicationCommandOption.{
  ApplicationCommandOptionChoice,
  ApplicationCommandOptionType
}
import ackcord.interactions.data.Interaction.ApplicationCommandInteractionDataOption
import ackcord.interactions.data.{IntOrDouble, Interaction, StringOrIntOrDoubleOrBoolean}
import cats.Id
import cats.syntax.all._

/**
  * A parameter that can be used in a slash command.
  *
  * @tparam Orig
  *   The type the parameter originally has.
  * @tparam A
  *   The type the parameter has after a bit of processing.
  * @tparam F
  *   The context of the parameter, either Id or Option currently.
  */
sealed trait Param[Orig, A, F[_]] {

  /** Type of the parameter. */
  def tpe: ApplicationCommandOptionType

  /** Name of the parameter. */
  def name: String

  private[interactions] def fTransformer: Param.FTransformer[F]
  private[interactions] def isRequired: Boolean = fTransformer == Param.FTransformer.Required
  private[interactions] def optionToFa(
      opt: Option[StringOrIntOrDoubleOrBoolean],
      resolved: Interaction.ResolvedData
  ): Either[String, F[A]]

  /** imap the parameter with the resolved data. */
  def imapWithResolve[B](map: (A, Interaction.ResolvedData) => Option[B])(
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
  sealed private[interactions] trait FTransformer[F[_]] {
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
  *
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
  * @param minLength
  *   The minimum length of the parameters.
  * @param maxLength
  *   The maximum length of the parameters.
  * @tparam Orig
  *   The type the parameter originally has.
  * @tparam A
  *   The type the parameter has after a bit of processing.
  * @tparam F
  *   The context of the parameter, either Id or Option currently.
  */
case class ChoiceParam[Orig, A, F[_]] private[interactions] (
    tpe: ApplicationCommandOptionType,
    name: String,
    nameLocalizations: JsonOption[Map[String, String]],
    description: String,
    descriptionLocalizations: JsonOption[Map[String, String]],
    fTransformer: Param.FTransformer[F],
    choices: Map[String, (JsonOption[Map[String, String]], Orig)],
    autocomplete: Option[String => Seq[Orig]],
    minValue: Option[IntOrDouble],
    maxValue: Option[IntOrDouble],
    minLength: Option[Int],
    maxLength: Option[Int],
    makeOptionChoice: (String, JsonOption[Map[String, String]], Orig) => ApplicationCommandOptionChoice,
    unwrapWrapper: StringOrIntOrDoubleOrBoolean => Option[Orig],
    map: (Orig, Interaction.ResolvedData) => Option[A],
    contramap: A => Orig,
    processAutoComplete: Seq[Orig] => Seq[Orig]
) extends Param[Orig, A, F] {
  require(choices.nonEmpty && autocomplete.isEmpty || choices.isEmpty, "Can't use both autocomplete and static choices")

  /**
    * Sets the choices for the parameter. Can't be set at the same time as
    * autocomplete.
    */
  def withLocalizedChoices(choices: Map[String, (JsonOption[Map[String, String]], A)]): ChoiceParam[Orig, A, F] =
    copy(choices = choices.map(t => t._1 -> (t._2._1, contramap(t._2._2))))

  /**
    * Sets the choices for the parameter. Can't be set at the same time as
    * autocomplete.
    */
  def withChoices(choices: Map[String, A]): ChoiceParam[Orig, A, F] =
    withLocalizedChoices(choices.map(t => t._1 -> (JsonUndefined(), t._2)))

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
    copy(autocomplete = Some(complete.andThen(_.map(contramap).distinct)))
  def noAutocomplete: ChoiceParam[Orig, A, F] = copy(autocomplete = None)

  override def imapWithResolve[B](
      map: (A, Interaction.ResolvedData) => Option[B]
  )(contramap: B => A): ChoiceParam[Orig, B, F] =
    copy(
      map = (orig, resolved) => this.map(orig, resolved).flatMap(a => map(a, resolved)),
      contramap = this.contramap.compose(contramap)
    )

  override def imap[B](map: A => B)(contramap: B => A): ChoiceParam[Orig, B, F] =
    imapWithResolve((a, _) => Some(map(a)))(contramap)

  override private[interactions] def optionToFa(
      opt: Option[StringOrIntOrDoubleOrBoolean],
      resolved: Interaction.ResolvedData
  ): Either[String, F[A]] = {
    val mappedOpt = opt
      .traverse(wrapper => unwrapWrapper(wrapper).toRight(s"Wrong parameter type $wrapper"))
      .flatMap(_.traverse(orig => map(orig, resolved).toRight(s"Unknown object $orig")))
    mappedOpt.flatMap(fTransformer(_))
  }

  override def toCommandOption: ApplicationCommandOption = ApplicationCommandOption.make20(
    tpe = tpe,
    name = name,
    nameLocalizations = nameLocalizations,
    description = description,
    descriptionLocalizations = descriptionLocalizations,
    required = UndefOr.someIfTrue(isRequired),
    choices = UndefOrSome(choices.map(t => makeOptionChoice(t._1, t._2._1, t._2._2)).toSeq),
    options = UndefOrUndefined(),
    channelTypes = UndefOrUndefined(),
    minValue = UndefOr.fromOption(minValue),
    maxValue = UndefOr.fromOption(maxValue),
    minLength = UndefOr.fromOption(minLength),
    maxLength = UndefOr.fromOption(maxLength),
    autocomplete = UndefOr.someIfTrue(autocomplete.isDefined)
  )
}
object ChoiceParam {
  //noinspection ConvertibleToMethodValue
  private[interactions] def default[A](
      tpe: ApplicationCommandOptionType,
      name: String,
      nameLocalizations: JsonOption[Map[String, String]],
      description: String,
      descriptionLocalizations: JsonOption[Map[String, String]],
      minValue: Option[IntOrDouble],
      maxValue: Option[IntOrDouble],
      minLength: Option[Int],
      maxLength: Option[Int],
      makeOptionChoice: (String, JsonOption[Map[String, String]], A) => ApplicationCommandOptionChoice,
      unwrapWrapper: PartialFunction[StringOrIntOrDoubleOrBoolean, A],
      processAutoComplete: Seq[A] => Seq[A] = identity[Seq[A]](_)
  ): ChoiceParam[A, A, Id] =
    ChoiceParam(
      tpe,
      name,
      nameLocalizations,
      description,
      descriptionLocalizations,
      fTransformer = Param.FTransformer.Required,
      Map.empty,
      None,
      minValue,
      maxValue,
      minLength,
      maxLength,
      makeOptionChoice,
      unwrapWrapper.lift,
      (a, _) => Some(a),
      identity,
      processAutoComplete
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
    tpe: ApplicationCommandOptionType,
    name: String,
    nameLocalizations: JsonOption[Map[String, String]],
    description: String,
    descriptionLocalizations: JsonOption[Map[String, String]],
    fTransformer: Param.FTransformer[F],
    channelTypes: Seq[Channel.ChannelType],
    unwrapWrapper: StringOrIntOrDoubleOrBoolean => Option[Orig],
    map: (Orig, Interaction.ResolvedData) => Option[A],
    contramap: A => Orig
) extends Param[Orig, A, F] {

  /** Sets the parameter as required. */
  def required: ValueParam[Orig, A, Id] = copy(fTransformer = Param.FTransformer.Required)

  /** Sets the parameter as optional. */
  def notRequired: ValueParam[Orig, A, Option] = copy(fTransformer = Param.FTransformer.Optional)

  override def imapWithResolve[B](
      map: (A, Interaction.ResolvedData) => Option[B]
  )(contramap: B => A): ValueParam[Orig, B, F] =
    copy(
      map = (orig, resolved) => this.map(orig, resolved).flatMap(a => map(a, resolved)),
      contramap = this.contramap.compose(contramap)
    )

  override def imap[B](map: A => B)(contramap: B => A): ValueParam[Orig, B, F] =
    imapWithResolve((a, _) => Some(map(a)))(contramap)

  override private[interactions] def optionToFa(
      opt: Option[StringOrIntOrDoubleOrBoolean],
      resolved: Interaction.ResolvedData
  ): Either[String, F[A]] = {
    val mappedOpt = opt
      .traverse(wrapper => unwrapWrapper(wrapper).toRight(s"Wrong parameter type $wrapper"))
      .flatMap(_.traverse(orig => map(orig, resolved).toRight(s"Unknown object $orig")))
    mappedOpt.flatMap(fTransformer(_))
  }

  override def toCommandOption: ApplicationCommandOption = ApplicationCommandOption.make20(
    tpe = tpe,
    name = name,
    nameLocalizations = nameLocalizations,
    description = description,
    descriptionLocalizations = descriptionLocalizations,
    choices = UndefOrUndefined(),
    required = UndefOr.someIfTrue(isRequired),
    options = UndefOrUndefined(),
    channelTypes = UndefOrSome(channelTypes),
    minValue = UndefOrUndefined(),
    maxValue = UndefOrUndefined(),
    minLength = UndefOrUndefined(),
    maxLength = UndefOrUndefined(),
    autocomplete = UndefOrUndefined()
  )
}
object ValueParam {
  private[interactions] def default[A](
      tpe: ApplicationCommandOptionType,
      name: String,
      nameLocalizations: JsonOption[Map[String, String]],
      description: String,
      descriptionLocalizations: JsonOption[Map[String, String]],
      channelTypes: Seq[Channel.ChannelType],
      unwrapWrapper: PartialFunction[StringOrIntOrDoubleOrBoolean, A]
  ): ValueParam[A, A, Id] =
    ValueParam(
      tpe,
      name,
      nameLocalizations,
      description,
      descriptionLocalizations,
      fTransformer = Param.FTransformer.Required,
      channelTypes,
      unwrapWrapper.lift,
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
      dataOption: Option[ApplicationCommandInteractionDataOption],
      resolved: Interaction.ResolvedData
  ): Either[String, F[A1]] = {
    dataOption match {
      case Some(dataOption) if dataOption.tpe == param.tpe =>
        param.optionToFa(dataOption.value.toOption, resolved)

      case Some(_) => Left("Got invalid parameter type")

      case None => param.optionToFa(None, resolved)
    }
  }

  protected def constructAutocomplete[Orig, A1, F[_]](
      param: Param[Orig, A1, F],
      dataOption: Option[ApplicationCommandInteractionDataOption]
  ): Seq[ApplicationCommandOptionChoice] = {
    val res = for {
      choiceParam <- param match {
        case param: ChoiceParam[Orig, A1, F] => Some(param)
        case _                               => None
      }
      completeFunction <- choiceParam.autocomplete
      option           <- dataOption
      if option.focused.contains(true)
    } yield choiceParam
      .processAutoComplete(completeFunction(option.value.fold("")(_.toString)))
      .map(orig =>
        choiceParam.makeOptionChoice(orig.toString, JsonUndefined(), orig)
      ) // TODO: Explore if these can be localized

    res.toSeq.flatten
  }

  /** Construct values from the specified interaction data. */
  def constructValues(
      options: Map[String, ApplicationCommandInteractionDataOption],
      resolved: Interaction.ResolvedData
  ): Either[String, A]

  /** Runs autocomplete with the specified interaction data. */
  def runAutocomplete(
      options: Map[String, ApplicationCommandInteractionDataOption]
  ): Seq[ApplicationCommandOptionChoice]

  /** Fold this parameter list. */
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
        options: Map[String, ApplicationCommandInteractionDataOption],
        resolved: Interaction.ResolvedData
    ): Either[String, F[A]] =
      constructParam(leaf, options.get(leaf.name.toLowerCase(Locale.ROOT)), resolved)

    override def runAutocomplete(
        options: Map[String, ApplicationCommandInteractionDataOption]
    ): Seq[ApplicationCommandOptionChoice] =
      constructAutocomplete(leaf, options.get(leaf.name.toLowerCase(Locale.ROOT)))
  }

  case class ParamListBranch[T, B, G[_]](left: ParamList[T], right: Param[_, B, G]) extends ParamList[T ~ G[B]] {
    override def constructValues(
        options: Map[String, ApplicationCommandInteractionDataOption],
        resolved: Interaction.ResolvedData
    ): Either[String, (T, G[B])] =
      for {
        left  <- left.constructValues(options, resolved)
        right <- constructParam(right, options.get(right.name.toLowerCase(Locale.ROOT)), resolved)
      } yield (left, right)

    override def runAutocomplete(
        options: Map[String, ApplicationCommandInteractionDataOption]
    ): Seq[ApplicationCommandOptionChoice] =
      left.runAutocomplete(options) ++ constructAutocomplete(right, options.get(right.name.toLowerCase(Locale.ROOT)))
  }
}
