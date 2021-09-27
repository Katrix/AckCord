package ackcord.interactions.commands

import ackcord.data._
import ackcord.interactions.{CommandInteraction, InteractionHandlerOps}
import akka.NotUsed
import cats.Id

trait SlashCommandControllerBase[BaseInteraction[A] <: CommandInteraction[A]]
    extends InteractionHandlerOps {

  def Command: CommandBuilder[BaseInteraction, NotUsed]

  def string(
      name: String,
      description: String
  ): ChoiceParam[String, String, Id] =
    ChoiceParam.default(
      ApplicationCommandOptionType.String,
      name,
      description,
      (name, str) => ApplicationCommandOptionChoice(name, Left(str))
    )

  def int(name: String, description: String): ChoiceParam[Int, Int, Id] =
    ChoiceParam.default(
      ApplicationCommandOptionType.Integer,
      name,
      description,
      (name, i) => ApplicationCommandOptionChoice(name, Right(i))
    )

  def bool(
      name: String,
      description: String
  ): ValueParam[Boolean, Boolean, Id] =
    ValueParam.default(ApplicationCommandOptionType.Boolean, name, description)

  def userUnresolved(
      name: String,
      description: String
  ): ValueParam[UserId, UserId, Id] =
    ValueParam.default(ApplicationCommandOptionType.User, name, description)

  def user(
      name: String,
      description: String
  ): ValueParam[UserId, InteractionGuildMember, Id] =
    userUnresolved(name, description).mapWithResolve { (userId, resolve) =>
      for {
        user <- resolve.users.get(userId)
        member <- resolve.members.get(userId)
      } yield InteractionGuildMember(
        user,
        member.nick,
        member.roles,
        member.joinedAt,
        member.premiumSince,
        member.pending
      )
    }

  def channelUnresolved(
      name: String,
      description: String
  ): ValueParam[TextGuildChannelId, TextGuildChannelId, Id] =
    ValueParam.default(ApplicationCommandOptionType.Channel, name, description)

  def channel(
      name: String,
      description: String
  ): ValueParam[TextGuildChannelId, InteractionChannel, Id] =
    channelUnresolved(name, description).mapWithResolve((channelId, resolve) =>
      resolve.channels.get(channelId)
    )

  def roleUnresolved(
      name: String,
      description: String
  ): ValueParam[RoleId, RoleId, Id] =
    ValueParam.default(ApplicationCommandOptionType.Role, name, description)

  def role(name: String, description: String): ValueParam[RoleId, Role, Id] =
    roleUnresolved(name, description).mapWithResolve((roleId, resolve) =>
      resolve.roles.get(roleId)
    )

  def mentionableUnresolved(
      name: String,
      description: String
  ): ValueParam[UserOrRoleId, UserOrRoleId, Id] =
    ValueParam.default(
      ApplicationCommandOptionType.Mentionable,
      name,
      description
    )

  def mentionable(
      name: String,
      description: String
  ): ValueParam[UserOrRoleId, Either[InteractionGuildMember, Role], Id] =
    mentionableUnresolved(name, description).mapWithResolve { (id, resolve) =>
      resolve.roles.get(RoleId(id)).map(Right(_)).orElse {
        for {
          user <- resolve.users.get(UserId(id))
          member <- resolve.members.get(UserId(id))
        } yield Left(
          InteractionGuildMember(
            user,
            member.nick,
            member.roles,
            member.joinedAt,
            member.premiumSince,
            member.pending
          )
        )
      }
    }
}
