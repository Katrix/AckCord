package ackcord.voice

import java.net.InetSocketAddress

import scala.concurrent.duration._
import scala.util.{Failure, Success}

import ackcord.data.{RawSnowflake, UserId}
import akka.NotUsed
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.stream.scaladsl.{Keep, Sink, Source, SourceQueueWithComplete}
import akka.stream.OverflowStrategy
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
