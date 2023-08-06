package ackcord.interactions

import ackcord.data._
import ackcord.gateway.Context
import ackcord.interactions.data.ApplicationCommand.ApplicationCommandOption.{
  ApplicationCommandOptionChoice,
  ApplicationCommandOptionType
}
import ackcord.interactions.data.{IntOrDouble, StringOrIntOrDouble, StringOrIntOrDoubleOrBoolean}
import ackcord.requests.Requests
import cats.syntax.all._
import cats.{Id, MonadError}

/** Base class for application commands controllers. */
trait ApplicationCommandController[F[_]] extends InteractionHandlerOps[F] with InteractionsProcess[F] {

  override def F: MonadError[F, Throwable]

  override protected def doNothing: F[Unit] = F.unit

  val components: Components[F]

  def respondToPing: Boolean

  /** A builder to start making a new slash command. */
  val SlashCommand: SlashCommandBuilder[F, Unit] =
    new SlashCommandBuilder(
      Left(implicitly),
      Map.empty,
      defaultMemberPermissions = None,
      dmPermissions = true,
      nsfw = false
    )

  /** A builder to start making a new user command. */
  val UserCommand: UserCommandBuilder[F] =
    new UserCommandBuilder(Map.empty, defaultMemberPermissions = None, nsfw = false)

  /** A builder to start making a new message command. */
  val MessageCommand: MessageCommandBuilder[F] =
    new MessageCommandBuilder(Map.empty, defaultMemberPermissions = None, nsfw = false)

  /**
    * Create a string parameter.
    * @param name
    *   The name of the parameter.
    * @param description
    *   A description for the parameter.
    */
  def string(
      name: String,
      description: String,
      nameLocalizations: JsonOption[Map[String, String]] = JsonUndefined(),
      descriptionLocalizations: JsonOption[Map[String, String]] = JsonUndefined(),
      minLength: Option[Int] = None,
      maxLength: Option[Int] = None
  ): ChoiceParam[String, String, Id] =
    ChoiceParam.default(
      ApplicationCommandOptionType.STRING,
      name,
      nameLocalizations,
      description,
      descriptionLocalizations,
      None,
      None,
      minLength,
      maxLength,
      (name, nameLocalizations, str) =>
        ApplicationCommandOptionChoice.make20(name, nameLocalizations, StringOrIntOrDouble.OfString(str)),
      processAutoComplete = _.filter(_.nonEmpty),
      unwrapWrapper = { case StringOrIntOrDoubleOrBoolean.OfString(s) =>
        s
      }
    )

  /**
    * Create an integer parameter.
    * @param name
    *   The name of the parameter.
    * @param description
    *   A description for the parameter.
    * @param minValue
    *   The minimum value of the parameter.
    * @param maxValue
    *   The maximum value of the parameter.
    */
  def int(
      name: String,
      description: String,
      nameLocalizations: JsonOption[Map[String, String]] = JsonUndefined(),
      descriptionLocalizations: JsonOption[Map[String, String]] = JsonUndefined(),
      minValue: Option[Int] = None,
      maxValue: Option[Int] = None
  ): ChoiceParam[Int, Int, Id] =
    ChoiceParam.default(
      ApplicationCommandOptionType.INTEGER,
      name,
      nameLocalizations,
      description,
      descriptionLocalizations,
      minValue.map(IntOrDouble.OfInt),
      maxValue.map(IntOrDouble.OfInt),
      None,
      None,
      (name, nameLocalizations, i) =>
        ApplicationCommandOptionChoice.make20(name, nameLocalizations, StringOrIntOrDouble.OfInt(i)),
      unwrapWrapper = { case StringOrIntOrDoubleOrBoolean.OfInt(i) =>
        i
      }
    )

  /**
    * Create an boolean parameter.
    * @param name
    *   The name of the parameter.
    * @param description
    *   A description for the parameter.
    */
  def bool(
      name: String,
      description: String,
      nameLocalizations: JsonOption[Map[String, String]] = JsonUndefined(),
      descriptionLocalizations: JsonOption[Map[String, String]] = JsonUndefined()
  ): ValueParam[Boolean, Boolean, Id] =
    ValueParam.default(
      ApplicationCommandOptionType.BOOLEAN,
      name,
      nameLocalizations,
      description,
      descriptionLocalizations,
      Nil,
      { case StringOrIntOrDoubleOrBoolean.OfBoolean(b) =>
        b
      }
    )

  /**
    * Create an unresolved user parameter.
    * @param name
    *   The name of the parameter.
    * @param description
    *   A description for the parameter.
    */
  def userUnresolved(
      name: String,
      description: String,
      nameLocalizations: JsonOption[Map[String, String]] = JsonUndefined(),
      descriptionLocalizations: JsonOption[Map[String, String]] = JsonUndefined()
  ): ValueParam[UserId, UserId, Id] =
    ValueParam.default(
      ApplicationCommandOptionType.USER,
      name,
      nameLocalizations,
      description,
      descriptionLocalizations,
      Nil,
      { case StringOrIntOrDoubleOrBoolean.OfString(s) =>
        UserId(s)
      }
    )

  /**
    * Create an user parameter.
    * @param name
    *   The name of the parameter.
    * @param description
    *   A description for the parameter.
    */
  def user(name: String, description: String): ValueParam[UserId, (User, GuildMember.Partial), Id] =
    userUnresolved(name, description).imapWithResolve { (userId, resolve) =>
      for {
        user   <- resolve.users.toOption.flatMap(_.get(userId))
        member <- resolve.members.toOption.flatMap(_.get(userId))
      } yield (user, member)
    }(_._1.id)

  /**
    * Create an unresolved channel parameter.
    * @param name
    *   The name of the parameter.
    * @param description
    *   A description for the parameter.
    * @param channelTypes
    *   The channel types to allow users to mention.
    */
  def channelUnresolved(
      name: String,
      description: String,
      nameLocalizations: JsonOption[Map[String, String]] = JsonUndefined(),
      descriptionLocalizations: JsonOption[Map[String, String]] = JsonUndefined(),
      channelTypes: Seq[Channel.ChannelType] = Nil
  ): ValueParam[GuildChannelId, GuildChannelId, Id] =
    ValueParam.default(
      ApplicationCommandOptionType.CHANNEL,
      name,
      nameLocalizations,
      description,
      descriptionLocalizations,
      channelTypes,
      { case StringOrIntOrDoubleOrBoolean.OfString(s) =>
        GuildChannelId(s)
      }
    )

  /**
    * Create a channel parameter.
    * @param name
    *   The name of the parameter.
    * @param description
    *   A description for the parameter.
    * @param channelTypes
    *   The channel types to allow users to mention.
    */
  def channel(
      name: String,
      description: String,
      nameLocalizations: JsonOption[Map[String, String]] = JsonUndefined(),
      descriptionLocalizations: JsonOption[Map[String, String]] = JsonUndefined(),
      channelTypes: Seq[Channel.ChannelType] = Nil
  ): ValueParam[GuildChannelId, Interaction.ResolvedChannel, Id] =
    channelUnresolved(name, description, nameLocalizations, descriptionLocalizations, channelTypes).imapWithResolve(
      (channelId, resolve) => resolve.channels.toOption.flatMap(_.get(channelId))
    )(_.id)

  /**
    * Create an unresolved role parameter.
    * @param name
    *   The name of the parameter.
    * @param description
    *   A description for the parameter.
    */
  def roleUnresolved(
      name: String,
      description: String,
      nameLocalizations: JsonOption[Map[String, String]] = JsonUndefined(),
      descriptionLocalizations: JsonOption[Map[String, String]] = JsonUndefined()
  ): ValueParam[RoleId, RoleId, Id] =
    ValueParam.default(
      ApplicationCommandOptionType.ROLE,
      name,
      nameLocalizations,
      description,
      descriptionLocalizations,
      Nil,
      { case StringOrIntOrDoubleOrBoolean.OfString(s) =>
        RoleId(s)
      }
    )

  /**
    * Create a role parameter.
    * @param name
    *   The name of the parameter.
    * @param description
    *   A description for the parameter.
    */
  def role(
      name: String,
      description: String,
      nameLocalizations: JsonOption[Map[String, String]] = JsonUndefined(),
      descriptionLocalizations: JsonOption[Map[String, String]] = JsonUndefined()
  ): ValueParam[RoleId, Role, Id] =
    roleUnresolved(name, description, nameLocalizations, descriptionLocalizations).imapWithResolve((roleId, resolve) =>
      resolve.roles.toOption.flatMap(_.get(roleId))
    )(_.id)

  /**
    * Create an unresolved mentionable parameter.
    * @param name
    *   The name of the parameter.
    * @param description
    *   A description for the parameter.
    */
  def mentionableUnresolved(
      name: String,
      description: String,
      nameLocalizations: JsonOption[Map[String, String]] = JsonUndefined(),
      descriptionLocalizations: JsonOption[Map[String, String]] = JsonUndefined()
  ): ValueParam[UserOrRoleId, UserOrRoleId, Id] =
    ValueParam.default(
      ApplicationCommandOptionType.MENTIONABLE,
      name,
      nameLocalizations,
      description,
      descriptionLocalizations,
      Nil,
      { case StringOrIntOrDoubleOrBoolean.OfString(s) =>
        UserOrRoleId(s)
      }
    )

  /**
    * Create a mentionable parameter.
    * @param name
    *   The name of the parameter.
    * @param description
    *   A description for the parameter.
    */
  def mentionable(
      name: String,
      description: String,
      nameLocalizations: JsonOption[Map[String, String]] = JsonUndefined(),
      descriptionLocalizations: JsonOption[Map[String, String]] = JsonUndefined()
  ): ValueParam[UserOrRoleId, Either[(User, GuildMember.Partial), Role], Id] =
    mentionableUnresolved(name, description, nameLocalizations, descriptionLocalizations).imapWithResolve {
      (id, resolve) =>
        resolve.roles.toOption.flatMap(_.get(RoleId(id))).map(Right(_)).orElse {
          for {
            user   <- resolve.users.toOption.flatMap(_.get(UserId(id)))
            member <- resolve.members.toOption.flatMap(_.get(UserId(id)))
          } yield Left((user, member))
        }
    }(_.fold(u => UserOrRoleId(u._1.id), r => UserOrRoleId(r.id)))

  /**
    * Create a number parameter.
    * @param name
    *   The name of the parameter.
    * @param description
    *   A description for the parameter.
    * @param minValue
    *   The minimum value of the parameter.
    * @param maxValue
    *   The maximum value of the parameter.
    */
  def number(
      name: String,
      description: String,
      nameLocalizations: JsonOption[Map[String, String]] = JsonUndefined(),
      descriptionLocalizations: JsonOption[Map[String, String]] = JsonUndefined(),
      minValue: Option[Double] = None,
      maxValue: Option[Double] = None
  ): ChoiceParam[Double, Double, Id] = ChoiceParam.default(
    ApplicationCommandOptionType.NUMBER,
    name,
    nameLocalizations,
    description,
    descriptionLocalizations,
    minValue.map(IntOrDouble.OfDouble),
    maxValue.map(IntOrDouble.OfDouble),
    None,
    None,
    (name, nameLocalizations, num) =>
      ApplicationCommandOptionChoice.make20(name, nameLocalizations, StringOrIntOrDouble.OfDouble(num)),
    { case StringOrIntOrDoubleOrBoolean.OfDouble(d) =>
      d
    }
  )

  //TODO: Attachment param

  val allCommands: Seq[CreatedApplicationCommand[F]]
  lazy val allCommandsByName: Map[String, CreatedApplicationCommand[F]] = allCommands.map(c => c.name -> c).toMap

  override def processCommandInteraction(
      interaction: Interaction,
      context: Context
  ): F[Option[HighInteractionResponse[F]]] = {
    implicit val monad: MonadError[F, Throwable] = F
    interaction.data match {
      case UndefOrSome(data: Interaction.ApplicationCommandData) =>
        allCommandsByName.get(data.name).traverse(_.handleRaw(interaction, context))
      case _ => F.pure(None)
    }
  }

  override def processComponentInteraction(
      interaction: Interaction,
      context: Context
  ): F[Option[HighInteractionResponse[F]]] =
    components.processComponentInteraction(interaction, context)
}
object ApplicationCommandController {
  abstract class Base[F[_]](val requests: Requests[F, Any], val components: Components[F], val respondToPing: Boolean)(
      implicit override val F: MonadError[F, Throwable]
  ) extends InteractionsProcess.Base[F]("")
      with ApplicationCommandController[F] {

    override val name: String = getClass.getSimpleName
  }
}
