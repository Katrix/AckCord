package ackcord.voice

import scala.concurrent.duration._

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}

object WsHeart {

  def apply(parent: ActorRef[VoiceWsHandler.Command]): Behavior[Command] =
    Behaviors.setup { ctx =>
      Behaviors.withTimers { timers =>
        runningHeart(ctx, timers, parent, None, receivedAck = true)
      }
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
      timers.startTimerAtFixedRate("heartbeatTimerKey", Beat, (interval * 0.75).toInt.millis)
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

        runningHeart(context, timers, parent, previousNonce = Some(nonce), receivedAck = false)
      } else {
        log.warn("Did not receive HeartbeatACK between heartbeats. Restarting.")
        parent ! VoiceWsHandler.Restart(fresh = false, 0.millis)
        Behaviors.same
      }
  }

  sealed trait Command
  case class StartBeating(interval: Int, nonce: Int) extends Command
  case object StopBeating                            extends Command
  case class BeatAck(nonce: Int)                     extends Command
  case object Beat                                   extends Command
}
