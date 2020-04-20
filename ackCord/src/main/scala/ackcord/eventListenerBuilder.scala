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
import ackcord.data.{Channel, Guild, GuildChannel, GuildMember, TextChannel, TextGuildChannel, User, VoiceGuildChannel}
import ackcord.syntax._
import akka.NotUsed
import akka.stream.scaladsl.{Flow, Keep, Sink}
import cats.~>

trait EventListenerBuilder[+M[_], A <: APIMessage] extends ActionBuilder[EventListenerMessage, M, Nothing, A] { self =>
  override type Action[B, Mat] = EventListener[B, Mat]

  def refineEvent(msg: APIMessage): Option[A]

  def on[B <: A](implicit tag: ClassTag[B]): EventListenerBuilder[M, B] = new EventListenerBuilder[M, B] {
    override def requests: Requests = self.requests

    override def flow[C]: Flow[EventListenerMessage[C], Either[Option[Nothing], M[C]], NotUsed] = self.flow

    override def refineEvent(msg: APIMessage): Option[B] =
      tag.unapply(msg)
  }

  override def toSink[Mat](sinkBlock: Sink[M[A], Mat]): EventListener[A, Mat] = new EventListener[A, Mat] {
    override def sink: Sink[EventListenerMessage[A], Mat] =
      self.flow[A].collect { case Right(ma) => ma }.toMat(sinkBlock)(Keep.right)

    override def refineEvent(msg: APIMessage): Option[A] = self.refineEvent(msg)
  }

  override def andThen[O2[_]](that: ActionFunction[M, O2, Nothing]): EventListenerBuilder[O2, A] =
    new EventListenerBuilder[O2, A] {
      override def refineEvent(msg: APIMessage): Option[A] = self.refineEvent(msg)

      override def requests: Requests = self.requests

      override def flow[B]: Flow[EventListenerMessage[B], Either[Option[Nothing], O2[B]], NotUsed] =
        ActionFunction.flowViaEither(self.flow[B], that.flow[B])(Keep.right)
    }
}
object EventListenerBuilder {
  type EventFunction[-I[_], +O[_]]    = ActionFunction[I, O, Nothing]
  type EventTransformer[-I[_], +O[_]] = ActionTransformer[I, O, Nothing]

  def rawBuilder(requestsObj: Requests): EventListenerBuilder[EventListenerMessage, APIMessage] =
    new EventListenerBuilder[EventListenerMessage, APIMessage] {
      override def refineEvent(msg: APIMessage): Option[APIMessage] = Some(msg)

      override def requests: Requests = requestsObj

      override def flow[A]: Flow[EventListenerMessage[A], Either[Option[Nothing], EventListenerMessage[A]], NotUsed] =
        Flow[EventListenerMessage[A]].map(Right.apply)
    }

  def guildEvent[I[A] <: EventListenerMessage[A], O[_]](
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
            case e: APIMessage.MessageMessage if e.message.textGuildChannel.nonEmpty =>
              e.message.textGuildChannel
                .flatMap(_.guild)
                .map(create(_)(i))
            case e: APIMessage.VoiceStateUpdate if e.voiceState.guildId.isDefined =>
              e.voiceState.guild.map(create(_)(i))

            case _ => None
          }
        }
        .mapConcat(_.toList)
  }

  def channelEvent[I[A] <: EventListenerMessage[A], O[_]](
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
              voiceState.voiceChannel.map(create(_)(i))
            case _ => None
          }
        }
        .mapConcat(_.toList)
  }

  def textChannelEvent[I[A] <: ChannelEventListenerMessage[A], O[_]](
      create: TextChannel => I ~> O
  ): EventTransformer[I, O] = new EventTransformer[I, O] {
    override def flowMapper[A]: Flow[I[A], O[A], NotUsed] =
      Flow[I[A]]
        .map(i => i.channel.asTextChannel.map(create(_)(i)))
        .mapConcat(_.toList)
  }

  def textGuildChannelEvent[I[A] <: ChannelEventListenerMessage[A], O[_]](
      create: (TextGuildChannel, Guild) => I ~> O
  ): EventTransformer[I, O] = new EventTransformer[I, O] {
    override def flowMapper[A]: Flow[I[A], O[A], NotUsed] =
      Flow[I[A]]
        .map { i =>
          implicit val c: CacheSnapshot = i.cacheSnapshot
          for {
            tgChannel <- i.channel.asTextGuildChannel
            guild     <- tgChannel.guild
          } yield create(tgChannel, guild)(i)
        }
        .mapConcat(_.toList)
  }

  def voiceGuildChannelEvent[I[A] <: ChannelEventListenerMessage[A], O[_]](
      create: (VoiceGuildChannel, Guild) => I ~> O
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

  def guildUserEvent[I[A] <: GuildEventListenerMessage[A], O[_]](
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
            case APIMessage.GuildMemberUpdate(_, _, user, _, _, _)  => doCreate(user)
            case APIMessage.MessageCreate(message, _)               => message.authorUser.flatMap(doCreate)
            case APIMessage.MessageUpdate(message, _)               => message.authorUser.flatMap(doCreate)
            case APIMessage.MessageReactionAdd(user, _, _, _, _)    => doCreate(user)
            case APIMessage.MessageReactionRemove(user, _, _, _, _) => doCreate(user)
            case APIMessage.PresenceUpdate(_, user, _, _, _, _, _)  => doCreate(user)
            case APIMessage.TypingStart(_, user, _, _)              => doCreate(user)
            case APIMessage.VoiceStateUpdate(voiceState, _)         => voiceState.user.flatMap(doCreate)
            case _                                                  => None
          }
        }
        .mapConcat(_.toList)
  }
}

trait EventListener[A, Mat] {

  def refineEvent(msg: APIMessage): Option[A]

  def sink: Sink[EventListenerMessage[A], Mat]
}

trait EventListenerMessage[A] {
  def event: A with APIMessage

  def cacheSnapshot: CacheSnapshot = event.cache.current
}
object EventListenerMessage {
  implicit def findCache[A](implicit message: EventListenerMessage[A]): CacheSnapshot = message.cacheSnapshot

  case class Default[A](event: A with APIMessage) extends EventListenerMessage[A]
}

class WrappedEventListenerMessage[A](m: EventListenerMessage[A]) extends EventListenerMessage[A] {
  override def event: A with APIMessage = m.event
}

trait GuildEventListenerMessage[A] extends EventListenerMessage[A] {
  def guild: Guild
}
object GuildEventListenerMessage {

  case class Default[A](guild: Guild, m: EventListenerMessage[A])
      extends WrappedEventListenerMessage(m)
      with GuildEventListenerMessage[A]
}

trait ChannelEventListenerMessage[A] extends EventListenerMessage[A] {
  def channel: Channel
}
object ChannelEventListenerMessage {

  case class Default[A](channel: Channel, m: EventListenerMessage[A])
      extends WrappedEventListenerMessage(m)
      with ChannelEventListenerMessage[A]
}

trait TextChannelEventListenerMessage[A] extends ChannelEventListenerMessage[A] {
  def channel: TextChannel
}
object TextChannelEventListenerMessage {
  case class Default[A](channel: TextChannel, m: EventListenerMessage[A])
      extends WrappedEventListenerMessage(m)
      with TextChannelEventListenerMessage[A]
}

trait TextGuildChannelEventListenerMessage[A]
    extends TextChannelEventListenerMessage[A]
    with GuildEventListenerMessage[A] {
  def channel: TextGuildChannel
}
object TextGuildChannelEventListenerMessage {
  case class Default[A](channel: TextGuildChannel, guild: Guild, m: EventListenerMessage[A])
      extends WrappedEventListenerMessage(m)
      with TextGuildChannelEventListenerMessage[A]
}

trait VGuildChannelEventListenerMessage[A] extends ChannelEventListenerMessage[A] with GuildEventListenerMessage[A] {
  def channel: VoiceGuildChannel
}
object VGuildChannelEventListenerMessage {
  case class Default[A](channel: VoiceGuildChannel, guild: Guild, m: EventListenerMessage[A])
      extends WrappedEventListenerMessage(m)
      with VGuildChannelEventListenerMessage[A]
}

trait UserEventListenerMessage[A] extends EventListenerMessage[A] {
  def user: User
}

trait GuildUserEventListenerMessage[A] extends GuildEventListenerMessage[A] with UserEventListenerMessage[A] {
  def guildMember: GuildMember
}
object GuildUserEventListenerMessage {
  case class Default[A](guild: Guild, user: User, guildMember: GuildMember, m: EventListenerMessage[A])
      extends WrappedEventListenerMessage(m)
      with GuildUserEventListenerMessage[A]
}
