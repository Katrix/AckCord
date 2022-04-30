package ackcord.interactions.commands

import ackcord.data._
import ackcord.data.raw.RawRole
import ackcord.interactions.{CommandInteraction, DataInteractionTransformer, InteractionHandlerOps}
import akka.NotUsed
import cats.Id

/** Base class for application commands controllers. */
trait ApplicationCommandControllerBase[BaseInteraction[A] <: CommandInteraction[A]] extends InteractionHandlerOps {

  /** The interaction transformer to start off with. */
  def defaultInteractionTransformer: DataInteractionTransformer[CommandInteraction, BaseInteraction]

  /** A builder to start making a new slash command. */
  def SlashCommand: SlashCommandBuilder[BaseInteraction, NotUsed] =
    new SlashCommandBuilder(true, defaultInteractionTransformer, Left(implicitly), Map.empty)

  /** A builder to start making a new user command. */
  def UserCommand: UserCommandBuilder[BaseInteraction] =
    new UserCommandBuilder(true, defaultInteractionTransformer, Map.empty)

  /** A builder to start making a new message command. */
  def MessageCommand: MessageCommandBuilder[BaseInteraction] =
    new MessageCommandBuilder(true, defaultInteractionTransformer, Map.empty)

  /**
    * Create a string parameter.
    * @param name
    *   The name of the parameter.
    * @param description
    *   A description for the parameter.
    */
  def string(name: String, description: String): ChoiceParam[String, String, Id] =
    ChoiceParam.default(
      ApplicationCommandOptionType.String,
      name,
      description,
      None,
      None,
      (name, str) => ApplicationCommandOptionChoiceString(name, str),
      processAutoComplete = _.filter(_.nonEmpty)
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
      minValue: Option[Int] = None,
      maxValue: Option[Int] = None
  ): ChoiceParam[Int, Int, Id] =
    ChoiceParam.default(
      ApplicationCommandOptionType.Integer,
      name,
      description,
      minValue.map(Left(_)),
      maxValue.map(Left(_)),
      (name, i) => ApplicationCommandOptionChoiceInteger(name, i)
    )

  /**
    * Create an boolean parameter.
    * @param name
    *   The name of the parameter.
    * @param description
    *   A description for the parameter.
    */
  def bool(name: String, description: String): ValueParam[Boolean, Boolean, Id] =
    ValueParam.default(ApplicationCommandOptionType.Boolean, name, description, Nil)

  /**
    * Create an unresolved user parameter.
    * @param name
    *   The name of the parameter.
    * @param description
    *   A description for the parameter.
    */
  def userUnresolved(name: String, description: String): ValueParam[UserId, UserId, Id] =
    ValueParam.default(ApplicationCommandOptionType.User, name, description, Nil)

  /**
    * Create an user parameter.
    * @param name
    *   The name of the parameter.
    * @param description
    *   A description for the parameter.
    */
  def user(name: String, description: String): ValueParam[UserId, InteractionGuildMember, Id] =
    userUnresolved(name, description).imapWithResolve { (userId, resolve) =>
      for {
        user   <- resolve.users.get(userId)
        member <- resolve.members.get(userId)
      } yield InteractionGuildMember(
        user,
        member.nick,
        member.avatar,
        member.roles,
        member.joinedAt,
        member.premiumSince,
        member.pending,
        member.communicationDisabledUntil
      )
    }(_.user.id)

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
      channelTypes: Seq[ChannelType] = Nil
  ): ValueParam[TextGuildChannelId, TextGuildChannelId, Id] =
    ValueParam.default(ApplicationCommandOptionType.Channel, name, description, channelTypes)

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
      channelTypes: Seq[ChannelType] = Nil
  ): ValueParam[TextGuildChannelId, InteractionChannel, Id] =
    channelUnresolved(name, description, channelTypes).imapWithResolve((channelId, resolve) =>
      resolve.channels.get(channelId)
    )(_.id)

  /**
    * Create an unresolved role parameter.
    * @param name
    *   The name of the parameter.
    * @param description
    *   A description for the parameter.
    */
  def roleUnresolved(name: String, description: String): ValueParam[RoleId, RoleId, Id] =
    ValueParam.default(ApplicationCommandOptionType.Role, name, description, Nil)

  /**
    * Create a role parameter.
    * @param name
    *   The name of the parameter.
    * @param description
    *   A description for the parameter.
    */
  def role(name: String, description: String): ValueParam[RoleId, RawRole, Id] =
    roleUnresolved(name, description).imapWithResolve((roleId, resolve) => resolve.roles.get(roleId))(_.id)

  /**
    * Create an unresolved mentionable parameter.
    * @param name
    *   The name of the parameter.
    * @param description
    *   A description for the parameter.
    */
  def mentionableUnresolved(name: String, description: String): ValueParam[UserOrRoleId, UserOrRoleId, Id] =
    ValueParam.default(ApplicationCommandOptionType.Mentionable, name, description, Nil)

  /**
    * Create a mentionable parameter.
    * @param name
    *   The name of the parameter.
    * @param description
    *   A description for the parameter.
    */
  def mentionable(
      name: String,
      description: String
  ): ValueParam[UserOrRoleId, Either[InteractionGuildMember, RawRole], Id] =
    mentionableUnresolved(name, description).imapWithResolve { (id, resolve) =>
      resolve.roles.get(RoleId(id)).map(Right(_)).orElse {
        for {
          user   <- resolve.users.get(UserId(id))
          member <- resolve.members.get(UserId(id))
        } yield Left(
          InteractionGuildMember(
            user,
            member.nick,
            member.avatar,
            member.roles,
            member.joinedAt,
            member.premiumSince,
            member.pending,
            member.communicationDisabledUntil
          )
        )
      }
    }(_.fold(u => UserOrRoleId(u.user.id), r => UserOrRoleId(r.id)))

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
      minValue: Option[Double] = None,
      maxValue: Option[Double] = None
  ): ChoiceParam[Double, Double, Id] = ChoiceParam.default(
    ApplicationCommandOptionType.Number,
    name,
    description,
    minValue.map(Right(_)),
    maxValue.map(Right(_)),
    (name, num) => ApplicationCommandOptionChoiceNumber(name, num)
  )
}
