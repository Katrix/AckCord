/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2019 Katrix
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
package ackcord

import scala.reflect.ClassTag

import ackcord.commands.{ActionBuilder, ActionFunction, ActionTransformer}
import ackcord.data.{Channel, Guild, GuildChannel, GuildMember, TChannel, TGuildChannel, User, VGuildChannel}
import ackcord.syntax._
import akka.NotUsed
import akka.stream.scaladsl.{Flow, Keep, Sink}
import cats.~>

trait EventHandlerBuilder[+M[_], A <: APIMessage] extends ActionBuilder[EventHandlerMessage, M, Nothing, A] { self =>
  override type Action[B, Mat] = EventHandler2[B, Mat]

  def refineEvent(msg: APIMessage): Option[A]

  def on[B >: A <: APIMessage](implicit tag: ClassTag[B]): EventHandlerBuilder[M, B] = new EventHandlerBuilder[M, B] {
    override def requests: Requests = self.requests

    override def flow[C]: Flow[EventHandlerMessage[C], Either[Option[Nothing], M[C]], NotUsed] = self.flow

    override def refineEvent(msg: APIMessage): Option[B] =
      tag.unapply(msg)
  }

  override def streamed[Mat](sinkBlock: Sink[M[A], Mat]): EventHandler2[A, Mat] = new EventHandler2[A, Mat] {
    override def flow: Sink[EventHandlerMessage[A], Mat] =
      self.flow[A].collect { case Right(ma) => ma }.toMat(sinkBlock)(Keep.right)

    override def refineEvent(msg: APIMessage): Option[A] = self.refineEvent(msg)
  }

  override def andThen[O2[_]](that: ActionFunction[M, O2, Nothing]): EventHandlerBuilder[O2, A] =
    new EventHandlerBuilder[O2, A] {
      override def refineEvent(msg: APIMessage): Option[A] = self.refineEvent(msg)

      override def requests: Requests = self.requests

      override def flow[B]: Flow[EventHandlerMessage[B], Either[Option[Nothing], O2[B]], NotUsed] =
        ActionFunction.flowViaEither(self.flow[B], that.flow[B])(Keep.right)
    }
}
object EventHandlerBuilder {
  type EventFunction[-I[_], +O[_]]    = ActionFunction[I, O, Nothing]
  type EventTransformer[-I[_], +O[_]] = ActionTransformer[I, O, Nothing]

  def rawBuilder(requestsObj: Requests): EventHandlerBuilder[EventHandlerMessage, APIMessage] =
    new EventHandlerBuilder[EventHandlerMessage, APIMessage] {
      override def refineEvent(msg: APIMessage): Option[APIMessage] = Some(msg)

      override def requests: Requests = requestsObj

      override def flow[A]: Flow[EventHandlerMessage[A], Either[Option[Nothing], EventHandlerMessage[A]], NotUsed] =
        Flow[EventHandlerMessage[A]].map(Right.apply)
    }

  def guildEvent[I[A] <: EventHandlerMessage[A], O[_]](
      create: Guild => I ~> O
  ): EventTransformer[I, O] = new EventTransformer[I, O] {

    override def flowMapper[A]: Flow[I[A], O[A], NotUsed] =
      Flow[I[A]]
        .map { i =>
          implicit val c: CacheSnapshot = i.cacheSnapshot
          (i.event: APIMessage) match {
            case e: APIMessage.GuildMessage =>
              Some(create(e.guild)(i))
            case e: APIMessage.ChannelMessage if e.channel.isInstanceOf[GuildChannel] =>
              e.channel
                .asInstanceOf[GuildChannel]
                .guild
                .map(create(_)(i))
            case e: APIMessage.MessageMessage if e.message.tGuildChannel.nonEmpty =>
              e.message.tGuildChannel
                .flatMap(_.guild)
                .map(create(_)(i))
            case e: APIMessage.VoiceStateUpdate if e.voiceState.guildId.isDefined =>
              e.voiceState.guild.map(create(_)(i))

            case _ => None
          }
        }
        .mapConcat(_.toList)
  }

  def channelEvent[I[A] <: EventHandlerMessage[A], O[_]](
      create: Channel => I ~> O
  ): EventTransformer[I, O] = new EventTransformer[I, O] {

    override def flowMapper[A]: Flow[I[A], O[A], NotUsed] =
      Flow[I[A]]
        .map { i =>
          implicit val c: CacheSnapshot = i.cacheSnapshot
          (i.event: APIMessage) match {
            case e: APIMessage.ChannelMessage => Some(create(e.channel)(i))
            case e: APIMessage.MessageMessage => c.getChannel(e.message.channelId).map(create(_)(i))
            case APIMessage.VoiceStateUpdate(voiceState, _) if voiceState.channelId.isDefined =>
              voiceState.vChannel.map(create(_)(i))
            case _ => None
          }
        }
        .mapConcat(_.toList)
  }

  def tChannelEvent[I[A] <: ChannelEventHandlerMessage[A], O[_]](
      create: TChannel => I ~> O
  ): EventTransformer[I, O] = new EventTransformer[I, O] {
    override def flowMapper[A]: Flow[I[A], O[A], NotUsed] =
      Flow[I[A]]
        .map { i =>
          i.channel.asTChannel.map(create(_)(i))
        }
        .mapConcat(_.toList)
  }

  def tGuildChannelEvent[I[A] <: ChannelEventHandlerMessage[A], O[_]](
      create: (TGuildChannel, Guild) => I ~> O
  ): EventTransformer[I, O] = new EventTransformer[I, O] {
    override def flowMapper[A]: Flow[I[A], O[A], NotUsed] =
      Flow[I[A]]
        .map { i =>
          implicit val c: CacheSnapshot = i.cacheSnapshot
          for {
            tgChannel <- i.channel.asTGuildChannel
            guild     <- tgChannel.guild
          } yield create(tgChannel, guild)(i)
        }
        .mapConcat(_.toList)
  }

  def vGuildChannelEvent[I[A] <: ChannelEventHandlerMessage[A], O[_]](
      create: (VGuildChannel, Guild) => I ~> O
  ): EventTransformer[I, O] = new EventTransformer[I, O] {
    override def flowMapper[A]: Flow[I[A], O[A], NotUsed] =
      Flow[I[A]]
        .map { i =>
          implicit val c: CacheSnapshot = i.cacheSnapshot
          for {
            tgChannel <- i.channel.asVGuildChannel
            guild     <- tgChannel.guild
          } yield create(tgChannel, guild)(i)
        }
        .mapConcat(_.toList)
  }

  def guildUserEvent[I[A] <: GuildEventHandlerMessage[A], O[_]](
      create: (Guild, User, GuildMember) => I ~> O
  ): EventTransformer[I, O] = new EventTransformer[I, O] {
    override def flowMapper[A]: Flow[I[A], O[A], NotUsed] =
      Flow[I[A]]
        .map { i =>
          implicit val c: CacheSnapshot = i.cacheSnapshot
          val guild                     = i.guild

          def doCreate(user: User) = guild.memberById(user.id).map(member => create(guild, user, member)(i))

          (i.event: APIMessage) match {
            case APIMessage.GuildBanAdd(_, user, _)                 => doCreate(user)
            case APIMessage.GuildBanRemove(_, user, _)              => doCreate(user)
            case APIMessage.GuildMemberAdd(member, _, _)            => member.user.map(user => create(guild, user, member)(i))
            case APIMessage.GuildMemberRemove(user, _, _)           => doCreate(user)
            case APIMessage.GuildMemberUpdate(_, _, user, _, _)     => doCreate(user)
            case APIMessage.MessageCreate(message, _)               => message.authorUser.flatMap(doCreate)
            case APIMessage.MessageUpdate(message, _)               => message.authorUser.flatMap(doCreate)
            case APIMessage.MessageReactionAdd(user, _, _, _, _)    => doCreate(user)
            case APIMessage.MessageReactionRemove(user, _, _, _, _) => doCreate(user)
            case APIMessage.PresenceUpdate(_, user, _, _, _)        => doCreate(user)
            case APIMessage.TypingStart(_, user, _, _)              => doCreate(user)
            case APIMessage.VoiceStateUpdate(voiceState, _)         => voiceState.user.flatMap(doCreate)
            case APIMessage.VoiceStateUpdate(voiceState, cache)     => voiceState.user.flatMap(doCreate)
            case _                                                  => None
          }
        }
        .mapConcat(_.toList)
  }
}

trait EventHandler2[A, Mat] {

  def refineEvent(msg: APIMessage): Option[A]

  def flow: Sink[EventHandlerMessage[A], Mat]
}

trait EventHandlerMessage[A] {
  def event: A with APIMessage

  def cacheSnapshot: CacheSnapshot = event.cache.current
}
object EventHandlerMessage {
  implicit def findCache[A](implicit message: EventHandlerMessage[A]): CacheSnapshot = message.cacheSnapshot

  case class Default[A <: APIMessage](event: A with APIMessage) extends EventHandlerMessage[A]
}

class WrappedEventHandlerMessage[A](m: EventHandlerMessage[A]) extends EventHandlerMessage[A] {
  override def event: A with APIMessage = m.event
}

trait GuildEventHandlerMessage[A] extends EventHandlerMessage[A] {
  def guild: Guild
}
object GuildEventHandlerMessage {

  case class Default[A <: APIMessage](guild: Guild, m: EventHandlerMessage[A])
      extends WrappedEventHandlerMessage(m)
      with GuildEventHandlerMessage[A]
}

trait ChannelEventHandlerMessage[A] extends EventHandlerMessage[A] {
  def channel: Channel
}
object ChannelEventHandlerMessage {

  case class Default[A <: APIMessage](channel: Channel, m: EventHandlerMessage[A])
      extends WrappedEventHandlerMessage(m)
      with ChannelEventHandlerMessage[A]
}

trait TChannelEventHandlerMessage[A] extends ChannelEventHandlerMessage[A] {
  def channel: TChannel
}
object TChannelEventHandlerMessage {
  case class Default[A <: APIMessage](channel: TChannel, m: EventHandlerMessage[A])
      extends WrappedEventHandlerMessage(m)
      with TChannelEventHandlerMessage[A]
}

trait TGuildChannelEventHandlerMessage[A] extends TChannelEventHandlerMessage[A] with GuildEventHandlerMessage[A] {
  def channel: TGuildChannel
}
object TGuildChannelEventHandlerMessage {
  case class Default[A <: APIMessage](channel: TGuildChannel, guild: Guild, m: EventHandlerMessage[A])
      extends WrappedEventHandlerMessage(m)
      with TGuildChannelEventHandlerMessage[A]
}

trait VGuildChannelEventHandlerMessage[A] extends ChannelEventHandlerMessage[A] with GuildEventHandlerMessage[A] {
  def channel: VGuildChannel
}
object VGuildChannelEventHandlerMessage {
  case class Default[A <: APIMessage](channel: VGuildChannel, guild: Guild, m: EventHandlerMessage[A])
      extends WrappedEventHandlerMessage(m)
      with VGuildChannelEventHandlerMessage[A]
}

trait UserEventHandlerMessage[A] extends EventHandlerMessage[A] {
  def user: User
}

trait GuildUserEventHandlerMessage[A] extends GuildEventHandlerMessage[A] with UserEventHandlerMessage[A] {
  def guildMember: GuildMember
}
object GuildUserEventHandlerMessage {
  case class Default[A <: APIMessage](guild: Guild, user: User, guildMember: GuildMember, m: EventHandlerMessage[A])
      extends WrappedEventHandlerMessage(m)
      with GuildUserEventHandlerMessage[A]
}
