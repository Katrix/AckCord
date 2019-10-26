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

import java.net.InetSocketAddress

import scala.concurrent.duration._
import scala.util.{Failure, Success}

import ackcord.data.{RawSnowflake, UserId}
import akka.NotUsed
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Keep, Sink, Source, SourceQueueWithComplete}
import akka.util.ByteString
import org.slf4j.Logger

object VoiceUDPHandler {

  def apply(
      address: String,
      port: Int,
      ssrc: Int,
      serverId: RawSnowflake,
      userId: UserId,
      soundProducer: Source[ByteString, NotUsed],
      soundConsumer: Sink[AudioAPIMessage, NotUsed],
      parent: ActorRef[VoiceHandler.Command]
  ): Behavior[Command] =
    Behaviors
      .supervise(
        Behaviors.setup[Command] { ctx =>
          implicit val system: ActorSystem[Nothing] = ctx.system

          val ((queue, futIp), watchDone) = soundProducer
            .viaMat(
              VoiceUDPFlow
                .flow(
                  new InetSocketAddress(address, port),
                  ssrc,
                  serverId,
                  userId,
                  Source.queue[Option[ByteString]](0, OverflowStrategy.dropBuffer)
                )
                .watchTermination()(Keep.both)
            )(Keep.right)
            .to(soundConsumer)
            .run()

          ctx.pipeToSelf(futIp) {
            case Success(value) => IPDiscoveryResult(value)
            case Failure(e)     => SendExeption(e)
          }
          ctx.pipeToSelf(watchDone)(_ => ConnectionDied)

          handle(ctx, ctx.log, ssrc, queue, parent)
        }
      )
      .onFailure(
        SupervisorStrategy
          .restartWithBackoff(100.millis, 5.seconds, 1d)
          .withResetBackoffAfter(10.seconds)
          .withMaxRestarts(5)
      )

  def handle(
      ctx: ActorContext[Command],
      log: Logger,
      ssrc: Int,
      queue: SourceQueueWithComplete[Option[ByteString]],
      parent: ActorRef[VoiceHandler.Command]
  ): Behavior[Command] = Behaviors.receiveMessage {
    case SendExeption(e) => throw e
    case ConnectionDied  => Behaviors.stopped
    case Shutdown =>
      queue.complete()
      Behaviors.same
    case IPDiscoveryResult(VoiceUDPFlow.FoundIP(localAddress, localPort)) =>
      parent ! VoiceHandler.GotLocalIP(localAddress, localPort)
      Behaviors.same
    case SetSecretKey(key) =>
      queue.offer(key)
      Behaviors.same
  }

  sealed trait Command

  case object Shutdown extends Command

  private case class SendExeption(e: Throwable)                       extends Command
  private case object ConnectionDied                                  extends Command
  private case class IPDiscoveryResult(foundIP: VoiceUDPFlow.FoundIP) extends Command
  private[voice] case class SetSecretKey(key: Option[ByteString])     extends Command
}
