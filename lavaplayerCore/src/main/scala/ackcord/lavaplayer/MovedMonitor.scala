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
package ackcord.lavaplayer

import ackcord.{APIMessage, Events}
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.stream.scaladsl.Keep
import akka.stream.typed.scaladsl.ActorSink
import akka.stream.{KillSwitches, UniqueKillSwitch}

private[lavaplayer] object MovedMonitor {

  private[lavaplayer] def apply(
      events: Events,
      handler: ActorRef[LavaplayerHandler.Command]
  ): Behavior[Command] =
    Behaviors.setup { ctx =>
      implicit val system: ActorSystem[Nothing] = ctx.system

      val killSwitch = events.subscribeAPI
        .collectType[APIMessage.VoiceStateUpdate]
        .viaMat(KillSwitches.single)(Keep.right)
        .to(
          ActorSink
            .actorRefWithBackpressure(ctx.self, ReceivedEvent, InitSink, AckSink, CompletedSink, _ => CompletedSink)
        )
        .run()

      running(ctx, killSwitch, handler)
    }

  private def running(
      ctx: ActorContext[Command],
      killSwitch: UniqueKillSwitch,
      handler: ActorRef[LavaplayerHandler.Command]
  ): Behavior[Command] =
    Behaviors.receiveMessage {
      case Stop =>
        killSwitch.shutdown()
        Behaviors.same

      case InitSink(ackTo) =>
        ackTo ! AckSink
        Behaviors.same

      case ReceivedEvent(ackTo, APIMessage.VoiceStateUpdate(state, c, _)) if state.userId == c.current.botUser.id =>
        handler ! LavaplayerHandler.VoiceChannelMoved(state.channelId)
        ackTo ! AckSink
        Behaviors.same

      case ReceivedEvent(ackTo, _) =>
        ackTo ! AckSink
        Behaviors.same

      case CompletedSink =>
        Behaviors.stopped
    }

  private case object AckSink

  sealed trait Command
  case object Stop extends Command

  private case class InitSink(ackTo: ActorRef[AckSink.type])                                            extends Command
  private case class ReceivedEvent(ackTo: ActorRef[AckSink.type], message: APIMessage.VoiceStateUpdate) extends Command
  private case object CompletedSink                                                                     extends Command
}
