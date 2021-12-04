package ackcord.interactions.commands

import ackcord.data._
import ackcord.data.raw.RawRole
import ackcord.interactions.{CommandInteraction, DataInteractionTransformer, InteractionHandlerOps}
import akka.NotUsed
import cats.Id

trait ApplicationCommandControllerBase[BaseInteraction[A] <: CommandInteraction[A]] extends InteractionHandlerOps {

  def defaultInteractionTransformer: DataInteractionTransformer[CommandInteraction, BaseInteraction]

  def SlashCommand: SlashCommandBuilder[BaseInteraction, NotUsed] =
    new SlashCommandBuilder(true, defaultInteractionTransformer, Left(implicitly), Map.empty)

  @deprecated("Prefer SlashCommand", since = "0.18")
  def Command: SlashCommandBuilder[BaseInteraction, NotUsed] = SlashCommand

  def UserCommand: UserCommandBuilder[BaseInteraction]       = new UserCommandBuilder(true, defaultInteractionTransformer, Map.empty)
  def MessageCommand: MessageCommandBuilder[BaseInteraction] = new MessageCommandBuilder(true, defaultInteractionTransformer, Map.empty)

  def string(name: String, description: String): ChoiceParam[String, String, Id] =
    ChoiceParam.default(
      ApplicationCommandOptionType.String,
      name,
      description,
      None,
      None,
      (name, str) => ApplicationCommandOptionChoice(name, Left(str))
    )

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
      minValue.map(_.toDouble),
      maxValue.map(_.toDouble),
      (name, i) => ApplicationCommandOptionChoice(name, Right(i))
    )

  def bool(name: String, description: String): ValueParam[Boolean, Boolean, Id] =
    ValueParam.default(ApplicationCommandOptionType.Boolean, name, description, Nil)

  def userUnresolved(name: String, description: String): ValueParam[UserId, UserId, Id] =
    ValueParam.default(ApplicationCommandOptionType.User, name, description, Nil)

  def user(name: String, description: String): ValueParam[UserId, InteractionGuildMember, Id] =
    userUnresolved(name, description).mapWithResolve { (userId, resolve) =>
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
        member.pending
      )
    }

  def channelUnresolved(
      name: String,
      description: String,
      channelTypes: Seq[ChannelType] = Nil
  ): ValueParam[TextGuildChannelId, TextGuildChannelId, Id] =
    ValueParam.default(ApplicationCommandOptionType.Channel, name, description, channelTypes)

  def channel(
      name: String,
      description: String,
      channelTypes: Seq[ChannelType] = Nil
  ): ValueParam[TextGuildChannelId, InteractionChannel, Id] =
    channelUnresolved(name, description, channelTypes).mapWithResolve((channelId, resolve) =>
      resolve.channels.get(channelId)
    )

  def roleUnresolved(name: String, description: String): ValueParam[RoleId, RoleId, Id] =
    ValueParam.default(ApplicationCommandOptionType.Role, name, description, Nil)

  def role(name: String, description: String): ValueParam[RoleId, RawRole, Id] =
    roleUnresolved(name, description).mapWithResolve((roleId, resolve) => resolve.roles.get(roleId))

  def mentionableUnresolved(name: String, description: String): ValueParam[UserOrRoleId, UserOrRoleId, Id] =
    ValueParam.default(ApplicationCommandOptionType.Mentionable, name, description, Nil)

  def mentionable(
      name: String,
      description: String
  ): ValueParam[UserOrRoleId, Either[InteractionGuildMember, RawRole], Id] =
    mentionableUnresolved(name, description).mapWithResolve { (id, resolve) =>
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
            member.pending
          )
        )
      }
    }

  def number(
      name: String,
      description: String,
      minValue: Option[Double] = None,
      maxValue: Option[Double] = None
  ): ChoiceParam[Double, Double, Id] = ChoiceParam.default(
    ApplicationCommandOptionType.Number,
    name,
    description,
    minValue,
    maxValue,
    (name, num) => ApplicationCommandOptionChoice(name, Right(num))
  )
}
