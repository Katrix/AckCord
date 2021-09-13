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
package ackcord.interactions

import ackcord.data._
import ackcord.data.raw.RawGuildMember
import ackcord.{CacheSnapshot, OptFuture}
import cats.arrow.FunctionK
import cats.~>

case class InteractionInvocationInfo(
    id: InteractionId,
    guildId: Option[GuildId],
    channelId: TextChannelId,
    user: User,
    member: Option[RawGuildMember],
    memberPermissions: Option[Permission],
    token: String,
    clientId: String
) {
  def webhookId: SnowflakeType[Webhook] = SnowflakeType(clientId)
}

case class CommandInvocationInfo[A](
    interactionInfo: InteractionInvocationInfo,
    commandId: CommandId,
    args: A
)

trait Interaction {
  def interactionInvocationInfo: InteractionInvocationInfo

  def id: InteractionId                        = interactionInvocationInfo.id
  def optGuildId: Option[GuildId]              = interactionInvocationInfo.guildId
  def channelId: TextChannelId                 = interactionInvocationInfo.channelId
  def user: User                               = interactionInvocationInfo.user
  def optMember: Option[GuildMember]           = interactionInvocationInfo.member.map(_.toGuildMember(optGuildId.get))
  def optMemberPermissions: Option[Permission] = interactionInvocationInfo.memberPermissions
  def token: String                            = interactionInvocationInfo.token
  def webhookId: SnowflakeType[Webhook]        = interactionInvocationInfo.webhookId

  def optCache: Option[CacheSnapshot]
}

trait CommandInteraction[A] extends Interaction {
  def commandInvocationInfo: CommandInvocationInfo[A]
  override def interactionInvocationInfo: InteractionInvocationInfo = commandInvocationInfo.interactionInfo

  def commandId: CommandId = commandInvocationInfo.commandId
  def args: A              = commandInvocationInfo.args
}

trait ComponentInteraction extends Interaction {
  def customId: String
  def message: Message
}

trait MenuInteraction extends ComponentInteraction {
  def values: Seq[String]
}

trait CacheInteraction extends Interaction {
  def cache: CacheSnapshot
  override def optCache: Option[CacheSnapshot] = Some(cache)
}
trait CacheComponentInteraction  extends CacheInteraction with ComponentInteraction
trait CacheMenuInteraction       extends CacheInteraction with MenuInteraction
trait CacheCommandInteraction[A] extends CacheInteraction with CommandInteraction[A]

case class BaseCacheComponentInteraction(
    interactionInvocationInfo: InteractionInvocationInfo,
    message: Message,
    customId: String,
    cache: CacheSnapshot
) extends CacheComponentInteraction
case class BaseCacheMenuInteraction(
    interactionInvocationInfo: InteractionInvocationInfo,
    message: Message,
    customId: String,
    values: Seq[String],
    cache: CacheSnapshot
) extends CacheMenuInteraction
case class BaseCacheCommandInteraction[A](commandInvocationInfo: CommandInvocationInfo[A], cache: CacheSnapshot)
    extends CacheCommandInteraction[A]

trait ResolvedInteraction extends CacheInteraction {
  def textChannel: TextChannel
  def optGuild: Option[GatewayGuild]
}
trait ResolvedComponentInteraction  extends CacheComponentInteraction with ResolvedInteraction
trait ResolvedMenuInteraction       extends CacheMenuInteraction with ResolvedInteraction
trait ResolvedCommandInteraction[A] extends CacheCommandInteraction[A] with ResolvedInteraction

case class BaseResolvedComponentInteraction(
    interactionInvocationInfo: InteractionInvocationInfo,
    message: Message,
    customId: String,
    textChannel: TextChannel,
    optGuild: Option[GatewayGuild],
    cache: CacheSnapshot
) extends ResolvedComponentInteraction
case class BaseResolvedMenuInteraction(
    interactionInvocationInfo: InteractionInvocationInfo,
    message: Message,
    customId: String,
    values: Seq[String],
    textChannel: TextChannel,
    optGuild: Option[GatewayGuild],
    cache: CacheSnapshot
) extends ResolvedMenuInteraction
case class BaseResolvedCommandInteraction[A](
    commandInvocationInfo: CommandInvocationInfo[A],
    textChannel: TextChannel,
    optGuild: Option[GatewayGuild],
    cache: CacheSnapshot
) extends ResolvedCommandInteraction[A]

trait GuildInteraction extends ResolvedInteraction {
  def guild: GatewayGuild
  def member: GuildMember
  def memberPermissions: Permission

  def textChannel: TextGuildChannel

  override def optGuild: Option[GatewayGuild] = Some(guild)
}
trait GuildComponentInteraction  extends ResolvedComponentInteraction with GuildInteraction
trait GuildMenuInteraction       extends ResolvedMenuInteraction with GuildInteraction
trait GuildCommandInteraction[A] extends ResolvedCommandInteraction[A] with GuildInteraction

case class BaseGuildComponentInteraction(
    interactionInvocationInfo: InteractionInvocationInfo,
    message: Message,
    customId: String,
    textChannel: TextGuildChannel,
    guild: GatewayGuild,
    member: GuildMember,
    memberPermissions: Permission,
    cache: CacheSnapshot
) extends GuildComponentInteraction
case class BaseGuildMenuInteraction(
    interactionInvocationInfo: InteractionInvocationInfo,
    message: Message,
    customId: String,
    values: Seq[String],
    textChannel: TextGuildChannel,
    guild: GatewayGuild,
    member: GuildMember,
    memberPermissions: Permission,
    cache: CacheSnapshot
) extends GuildMenuInteraction
case class BaseGuildCommandInteraction[A](
    commandInvocationInfo: CommandInvocationInfo[A],
    textChannel: TextGuildChannel,
    guild: GatewayGuild,
    member: GuildMember,
    memberPermissions: Permission,
    cache: CacheSnapshot
) extends GuildCommandInteraction[A]

trait VoiceChannelInteraction extends GuildInteraction {
  def voiceChannel: VoiceGuildChannel
}
trait VoiceChannelComponentInteraction  extends GuildComponentInteraction with VoiceChannelInteraction
trait VoiceChannelMenuInteraction       extends GuildMenuInteraction with VoiceChannelInteraction
trait VoiceChannelCommandInteraction[A] extends GuildCommandInteraction[A] with VoiceChannelInteraction

case class BaseVoiceChannelComponentInteraction(
    interactionInvocationInfo: InteractionInvocationInfo,
    message: Message,
    customId: String,
    textChannel: TextGuildChannel,
    guild: GatewayGuild,
    member: GuildMember,
    memberPermissions: Permission,
    voiceChannel: VoiceGuildChannel,
    cache: CacheSnapshot
) extends VoiceChannelComponentInteraction
case class BaseVoiceChannelMenuInteraction(
    interactionInvocationInfo: InteractionInvocationInfo,
    message: Message,
    customId: String,
    values: Seq[String],
    textChannel: TextGuildChannel,
    guild: GatewayGuild,
    member: GuildMember,
    memberPermissions: Permission,
    voiceChannel: VoiceGuildChannel,
    cache: CacheSnapshot
) extends VoiceChannelMenuInteraction
case class BaseVoiceChannelCommandInteraction[A](
    commandInvocationInfo: CommandInvocationInfo[A],
    textChannel: TextGuildChannel,
    guild: GatewayGuild,
    member: GuildMember,
    memberPermissions: Permission,
    voiceChannel: VoiceGuildChannel,
    cache: CacheSnapshot
) extends VoiceChannelCommandInteraction[A]

case class StatelessComponentInteraction(
    interactionInvocationInfo: InteractionInvocationInfo,
    message: Message,
    customId: String
) extends ComponentInteraction {
  override def optCache: Option[CacheSnapshot] = None
}

case class StatelessMenuInteraction(
    interactionInvocationInfo: InteractionInvocationInfo,
    message: Message,
    customId: String,
    values: Seq[String]
) extends MenuInteraction {
  override def optCache: Option[CacheSnapshot] = None
}

case class StatelessCommandInteraction[A](
    commandInvocationInfo: CommandInvocationInfo[A]
) extends CommandInteraction[A] {
  override def optCache: Option[CacheSnapshot] = None
}

trait DataInteractionTransformer[From[_], To[_]] { outer =>
  def filter[A](from: From[A]): Either[Option[String], To[A]]

  def andThen[To2[_]](transformer: DataInteractionTransformer[To, To2]): DataInteractionTransformer[From, To2] =
    new DataInteractionTransformer[From, To2] {
      override def filter[A](from: From[A]): Either[Option[String], To2[A]] =
        outer.filter(from).flatMap(transformer.filter)
    }

  @inline final def compose[From2[_]](
      transformer: DataInteractionTransformer[From2, From]
  ): DataInteractionTransformer[From2, To] =
    transformer.andThen(this)
}
object DataInteractionTransformer {

  def identity[F[_]]: DataInteractionTransformer[F, F] = new DataInteractionTransformer[F, F] {
    override def filter[A](from: F[A]): Either[Option[String], F[A]] = Right(from)
  }

  /** A command transformer which resolves most ids from the cache. */
  def resolved[I[A] <: CacheInteraction, O[_]](
      create: (TextChannel, Option[GatewayGuild]) => I ~> O
  ): DataInteractionTransformer[I, O] = new DataInteractionTransformer[I, O] {
    override def filter[A](from: I[A]): Either[Option[String], O[A]] = {
      implicit val c: CacheSnapshot = from.cache

      val res = from.optGuildId match {
        case Some(guildId) =>
          for {
            channel <- from.channelId.asChannelId[TextGuildChannel].resolve(guildId)
            guild   <- guildId.resolve
          } yield create(channel, Some(guild))(from)

        case None =>
          from.channelId.resolve.map(create(_, None)(from))
      }

      res.toRight(Some("Missing cached values while executing function"))
    }
  }

  /** A command function that only allows commands sent from a guild. */
  def onlyInGuild[I[A] <: ResolvedInteraction, O[_]](
      create: (GatewayGuild, GuildMember, Permission, TextGuildChannel) => I ~> O
  ): DataInteractionTransformer[I, O] =
    new DataInteractionTransformer[I, O] {

      override def filter[A](from: I[A]): Either[Option[String], O[A]] = {
        from.optGuild match {
          case Some(guild) =>
            val member            = from.optMember.get
            val memberPermissions = from.optMemberPermissions.get
            val textGuildChannel  = from.asInstanceOf[TextGuildChannel]

            Right(create(guild, member, memberPermissions, textGuildChannel)(from))
          case None =>
            Left(Some(s"This command can only be used in a guild"))
        }
      }
    }

  def inVoiceChannel[I[A] <: GuildInteraction, O[_]](
      create: VoiceGuildChannel => I ~> O
  ): DataInteractionTransformer[I, O] = new DataInteractionTransformer[I, O] {
    type Result[A] = Either[Option[String], O[A]]

    override def filter[A](from: I[A]): Either[Option[String], O[A]] = {
      implicit val c: CacheSnapshot = from.cache
      from.guild.voiceStates
        .get(from.user.id)
        .flatMap(_.channelId)
        .flatMap(_.resolve(from.guild.id))
        .toRight(Some(s"This command can only be used while in a voice channel"))
        .map(vCh => create(vCh)(from))
    }
  }

  /**
    * A command function that requires that those who use this command need some
    * set of permissions.
    */
  def needPermission[M[A] <: GuildInteraction](
      neededPermission: Permission
  ): DataInteractionTransformer[M, M] = new DataInteractionTransformer[M, M] {

    override def filter[A](from: M[A]): Either[Option[String], M[A]] =
      Either.cond(
        from.memberPermissions.hasPermissions(neededPermission),
        from,
        Some("You don't have permission to use this command")
      )
  }
}

class DataInteractionFunction[From[_], To[_]](f: FunctionK[From, To]) extends DataInteractionTransformer[From, To] {
  override def filter[A](from: From[A]): Either[Option[String], To[A]] = Right(f(from))
}

trait InteractionTransformer[From, To]
    extends DataInteractionTransformer[shapeless.Const[From]#λ, shapeless.Const[To]#λ] { outer =>
  override def filter[A](from: From): Either[Option[String], To] = filterSimple(from)

  def filterSimple(from: From): Either[Option[String], To]

  def andThen[To2](transformer: InteractionTransformer[To, To2]): InteractionTransformer[From, To2] =
    (from: From) => outer.filter(from).flatMap(transformer.filter)

  @inline final def compose[From2](
      transformer: InteractionTransformer[From2, From]
  ): InteractionTransformer[From2, To] =
    transformer.andThen(this)
}
object InteractionTransformer {

  def fromComplex[From, To](
      complex: DataInteractionTransformer[shapeless.Const[From]#λ, shapeless.Const[To]#λ]
  ): InteractionTransformer[From, To] =
    (from: From) => complex.filter(from)

  def fromComplexCreate1[From, To, Info1](
      create: Info1 => From => To,
      makeComplex: (
          Info1 => shapeless.Const[From]#λ ~> shapeless.Const[To]#λ
      ) => DataInteractionTransformer[shapeless.Const[From]#λ, shapeless.Const[To]#λ]
  ): InteractionTransformer[From, To] = {
    fromComplex(
      makeComplex(info1 =>
        new (shapeless.Const[From]#λ ~> shapeless.Const[To]#λ) {
          override def apply[A](fa: From): To = create(info1)(fa)
        }
      )
    )
  }

  def fromComplexCreate[From, To, Info1, Info2](
      create: (Info1, Info2) => From => To,
      makeComplex: (
          (Info1, Info2) => shapeless.Const[From]#λ ~> shapeless.Const[To]#λ
      ) => DataInteractionTransformer[shapeless.Const[From]#λ, shapeless.Const[To]#λ]
  ): InteractionTransformer[From, To] =
    fromComplexCreate1[From, To, (Info1, Info2)](
      create.tupled,
      f => makeComplex((i1, i2) => f((i1, i2)))
    )

  def fromComplexCreate[From, To, Info1, Info2, Info3, Info4](
      create: (Info1, Info2, Info3, Info4) => From => To,
      makeComplex: (
          (Info1, Info2, Info3, Info4) => shapeless.Const[From]#λ ~> shapeless.Const[To]#λ
      ) => DataInteractionTransformer[shapeless.Const[From]#λ, shapeless.Const[To]#λ]
  ): InteractionTransformer[From, To] =
    fromComplexCreate1[From, To, (Info1, Info2, Info3, Info4)](
      create.tupled,
      f => makeComplex((i1, i2, i3, i4) => f((i1, i2, i3, i4)))
    )

  def identity[A]: InteractionTransformer[A, A] = (from: A) => Right(from)

  /** An interaction transformer which gurantees that a cache is present. */
  def cache[I <: Interaction, O](create: CacheSnapshot => I => O): InteractionTransformer[I, O] = new InteractionTransformer[I, O] {
    override def filterSimple(from: I): Either[Option[String], O] = {
      from match {
        case cacheInteraction: CacheInteraction => Right(create(cacheInteraction.cache)(from))
        case _ => Left(Some("This action can only be used when the bot has access to a cache"))
      }
    }
  }

  /** A command transformer which resolves most ids from the cache. */
  def resolved[I <: CacheInteraction, O](
      create: (TextChannel, Option[GatewayGuild]) => I => O
  ): InteractionTransformer[I, O] =
    fromComplexCreate[I, O, TextChannel, Option[GatewayGuild]](
      create,
      DataInteractionTransformer.resolved[shapeless.Const[I]#λ, shapeless.Const[O]#λ]
    )

  /** A command function that only allows commands sent from a guild. */
  def onlyInGuild[I <: ResolvedInteraction, O](
      create: (GatewayGuild, GuildMember, Permission, TextGuildChannel) => I => O
  ): InteractionTransformer[I, O] =
    fromComplexCreate[I, O, GatewayGuild, GuildMember, Permission, TextGuildChannel](
      create,
      DataInteractionTransformer.onlyInGuild[shapeless.Const[I]#λ, shapeless.Const[O]#λ]
    )

  def inVoiceChannel[I <: GuildInteraction, O](
      create: VoiceGuildChannel => I => O
  ): InteractionTransformer[I, O] =
    fromComplexCreate1[I, O, VoiceGuildChannel](
      create,
      DataInteractionTransformer.inVoiceChannel[shapeless.Const[I]#λ, shapeless.Const[O]#λ]
    )

  /**
    * A command function that requires that those who use this command need some
    * set of permissions.
    */
  def needPermission[M <: GuildInteraction](
      neededPermission: Permission
  ): InteractionTransformer[M, M] = fromComplex(
    DataInteractionTransformer.needPermission[shapeless.Const[M]#λ](neededPermission)
  )
}

sealed trait InteractionResponse {
  def toRawInteractionResponse: RawInteractionResponse = this match {
    case InteractionResponse.Pong => RawInteractionResponse(InteractionResponseType.Pong, None)
    case InteractionResponse.Acknowledge(_) =>
      RawInteractionResponse(InteractionResponseType.DeferredChannelMessageWithSource, None)
    case InteractionResponse.UpdateMessageLater(_) =>
      RawInteractionResponse(InteractionResponseType.DeferredUpdateMessage, None)
    case InteractionResponse.UpdateMessage(data, _) =>
      RawInteractionResponse(InteractionResponseType.UpdateMessage, Some(data))
    case InteractionResponse.ChannelMessage(message, _) =>
      RawInteractionResponse(InteractionResponseType.ChannelMessageWithSource, Some(message))
  }
}
object InteractionResponse {
  sealed trait AsyncMessageable extends InteractionResponse {
    def doAsync(action: AsyncMessageToken => OptFuture[_])(implicit
        interaction: Interaction
    ): InteractionResponse
  }

  case object Pong                                             extends InteractionResponse
  case class Acknowledge(andThenDo: () => OptFuture[_])        extends InteractionResponse
  case class UpdateMessageLater(andThenDo: () => OptFuture[_]) extends InteractionResponse
  case class UpdateMessage(
      message: RawInteractionApplicationCommandCallbackData,
      andThenDo: () => OptFuture[_]
  ) extends InteractionResponse
      with AsyncMessageable {

    override def doAsync(action: AsyncMessageToken => OptFuture[_])(implicit
        interaction: Interaction
    ): InteractionResponse = copy(andThenDo = () => action(AsyncToken.fromInteractionWithMessage(interaction)))
  }
  case class ChannelMessage(
      message: RawInteractionApplicationCommandCallbackData,
      andThenDo: () => OptFuture[_]
  ) extends InteractionResponse
      with AsyncMessageable {

    def doAsync(action: AsyncMessageToken => OptFuture[_])(implicit
        interaction: Interaction
    ): ChannelMessage = copy(andThenDo = () => action(AsyncToken.fromInteractionWithMessage(interaction)))
  }
}
