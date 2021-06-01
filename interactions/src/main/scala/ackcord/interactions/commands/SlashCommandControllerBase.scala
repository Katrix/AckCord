package ackcord.interactions.commands

import scala.util.matching.Regex

import ackcord.data._
import ackcord.interactions.{CommandInteraction, InteractionHandlerOps}
import akka.NotUsed
import cats.Id
import io.circe.DecodingFailure

trait SlashCommandControllerBase[BaseInteraction[A] <: CommandInteraction[A]] extends InteractionHandlerOps {

  def Command: CommandBuilder[BaseInteraction, NotUsed]

  private val userRegex: Regex    = """<@!?(\d+)>""".r
  private val channelRegex: Regex = """<#(\d+)>""".r
  private val roleRegex: Regex    = """<@&(\d+)>""".r

  private def parseMention[A](regex: Regex)(s: String): Either[DecodingFailure, SnowflakeType[A]] = s match {
    case regex(id) => Right(SnowflakeType(id))
    case _         => Left(DecodingFailure("Not a valid mention", Nil))
  }

  def string(name: String, description: String): ChoiceParam[String, String, Id] =
    ChoiceParam.default(
      ApplicationCommandOptionType.String,
      name,
      description,
      (name, str) => ApplicationCommandOptionChoice(name, Left(str)),
      _.hcursor.as[String]
    )

  def int(name: String, description: String): ChoiceParam[Int, Int, Id] =
    ChoiceParam.default(
      ApplicationCommandOptionType.Integer,
      name,
      description,
      (name, i) => ApplicationCommandOptionChoice(name, Right(i)),
      _.hcursor.as[Int]
    )

  def bool(name: String, description: String): ValueParam[Boolean, Boolean, Id] =
    ValueParam.default(ApplicationCommandOptionType.Boolean, name, description, _.hcursor.as[Boolean])

  def user(name: String, description: String): ValueParam[UserId, UserId, Id] =
    ValueParam.default(
      ApplicationCommandOptionType.User,
      name,
      description,
      _.hcursor.as[String].flatMap(parseMention(userRegex))
    )

  def channel(name: String, description: String): ValueParam[ChannelId, ChannelId, Id] =
    ValueParam.default(
      ApplicationCommandOptionType.Channel,
      name,
      description,
      _.hcursor.as[String].flatMap(parseMention(channelRegex))
    )

  def role(name: String, description: String): ValueParam[RoleId, RoleId, Id] =
    ValueParam.default(
      ApplicationCommandOptionType.Role,
      name,
      description,
      _.hcursor.as[String].flatMap(parseMention(roleRegex))
    )
}
