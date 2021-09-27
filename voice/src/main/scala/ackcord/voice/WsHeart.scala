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

package ackcord.voice

import scala.concurrent.duration._

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}

object WsHeart {

  def apply(parent: ActorRef[VoiceWsHandler.Command]): Behavior[Command] =
    Behaviors.setup { ctx =>
      Behaviors.withTimers(timers =>
        runningHeart(ctx, timers, parent, None, receivedAck = true)
      )
    }

  def runningHeart(
      context: ActorContext[Command],
      timers: TimerScheduler[Command],
      parent: ActorRef[VoiceWsHandler.Command],
      previousNonce: Option[Int],
      receivedAck: Boolean
  ): Behavior[Command] = Behaviors.receiveMessage {
    case StartBeating(interval, nonce) =>
      context.log.debug(s"Starting to beat with initial nonce $nonce")
      timers.startTimerAtFixedRate("heartbeatTimerKey", Beat, interval.millis)
      runningHeart(context, timers, parent, Some(nonce), receivedAck = true)

    case StopBeating =>
      timers.cancel("heartbeatTimerKey")
      runningHeart(context, timers, parent, None, receivedAck = true)

    case BeatAck(nonce) =>
      val log = context.log
      log.debug(s"Received HeartbeatACK with nonce $nonce")
      if (previousNonce.contains(nonce))
        runningHeart(context, timers, parent, None, receivedAck = true)
      else {
        log.warn("Did not receive correct nonce in HeartbeatACK. Restarting.")
        parent ! VoiceWsHandler.Restart(fresh = false, 500.millis)
        Behaviors.same
      }
    case Beat =>
      val log = context.log
      if (receivedAck) {
        val nonce = System.currentTimeMillis().toInt

        parent ! VoiceWsHandler.SendHeartbeat(nonce)
        log.debug(s"Sent Heartbeat with nonce $nonce")

        runningHeart(
          context,
          timers,
          parent,
          previousNonce = Some(nonce),
          receivedAck = false
        )
      } else {
        log.warn("Did not receive HeartbeatACK between heartbeats. Restarting.")
        parent ! VoiceWsHandler.Restart(fresh = false, 0.millis)
        Behaviors.same
      }
  }

  sealed trait Command
  case class StartBeating(interval: Double, nonce: Int) extends Command
  case object StopBeating extends Command
  case class BeatAck(nonce: Int) extends Command
  case object Beat extends Command
}
