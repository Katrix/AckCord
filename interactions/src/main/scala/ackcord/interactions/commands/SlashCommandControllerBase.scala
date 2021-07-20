package ackcord.interactions.commands

import ackcord.data._
import ackcord.interactions.{CommandInteraction, InteractionHandlerOps}
import akka.NotUsed
import cats.Id

trait SlashCommandControllerBase[BaseInteraction[A] <: CommandInteraction[A]] extends InteractionHandlerOps {

  def Command: CommandBuilder[BaseInteraction, NotUsed]

  def string(name: String, description: String): ChoiceParam[String, String, Id] =
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

  def bool(name: String, description: String): ValueParam[Boolean, Boolean, Id] =
    ValueParam.default(ApplicationCommandOptionType.Boolean, name, description)

  def user(name: String, description: String): ValueParam[UserId, UserId, Id] =
    ValueParam.default(ApplicationCommandOptionType.User, name, description)

  def channel(name: String, description: String): ValueParam[ChannelId, ChannelId, Id] =
    ValueParam.default(ApplicationCommandOptionType.Channel, name, description)

  def role(name: String, description: String): ValueParam[RoleId, RoleId, Id] =
    ValueParam.default(ApplicationCommandOptionType.Role, name, description)

  def mentionable(name: String, description: String): ValueParam[UserOrRoleId, UserOrRoleId, Id] =
    ValueParam.default(ApplicationCommandOptionType.Mentionable, name, description)
}
