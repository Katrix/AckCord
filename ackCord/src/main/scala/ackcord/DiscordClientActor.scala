package ackcord

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise}

import ackcord.requests.Ratelimiter
import akka.Done
import akka.actor.CoordinatedShutdown
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.actor.typed.scaladsl.adapter._
import akka.pattern.gracefulStop

class DiscordClientActor(
    ctx: ActorContext[DiscordClientActor.Command],
    shardBehaviors: Seq[Behavior[DiscordShard.Command]],
    cache: Cache
) extends AbstractBehavior[DiscordClientActor.Command](ctx) {
  import DiscordClientActor._
  implicit val system: ActorSystem[Nothing] = context.system
  import system.executionContext

  val shards: Seq[ActorRef[DiscordShard.Command]] =
    shardBehaviors.zipWithIndex.map(t => context.spawn(t._1, s"Shard${t._2}"))

  var shardShutdownManager: ActorRef[DiscordShard.StopShard.type] = _

  val musicManager: ActorRef[MusicManager.Command] = context.spawn(MusicManager(cache), "MusicManager")

  val rateLimiter: ActorRef[Ratelimiter.Command] = context.spawn(Ratelimiter(), "Ratelimiter")

  private val shutdown = CoordinatedShutdown(system.toClassic)

  shutdown.addTask("service-stop", "stop-discord") { () =>
    gracefulStop(shardShutdownManager.toClassic, shutdown.timeout("service-stop"), DiscordShard.StopShard)
      .map(_ => Done)
  }

  def login(): Unit = {
    require(shardShutdownManager == null, "Already logged in")
    shardShutdownManager = context.spawn(ShardShutdownManager(shards), "ShardShutdownManager")

    DiscordShard.startShards(shards)
  }

  def logout(timeout: FiniteDuration): Future[Boolean] = {
    import akka.actor.typed.scaladsl.adapter._

    val promise = Promise[Boolean]

    require(shardShutdownManager != null, "Not logged in")
    promise.completeWith(gracefulStop(shardShutdownManager.toClassic, timeout, DiscordShard.StopShard))

    promise.future
  }

  override def onMessage(msg: Command): Behavior[Command] = {
    msg match {
      case DiscordClientActor.Login => login()
      case Logout(timeout, replyTo) => replyTo ! LogoutReply(logout(timeout))
      case GetShards(replyTo)       => replyTo ! GetShardsReply(shards)
      case GetMusicManager(replyTo) => replyTo ! GetMusicManagerReply(musicManager)
      case GetRatelimiter(replyTo)  => replyTo ! GetRatelimiterReply(rateLimiter)
    }

    Behaviors.same
  }
}
object DiscordClientActor {
  def apply(
      shardBehaviors: Seq[Behavior[DiscordShard.Command]],
      cache: Cache
  ): Behavior[Command] = Behaviors.setup { ctx =>
    new DiscordClientActor(ctx, shardBehaviors, cache)
  }

  sealed trait Command

  case object Login                                                          extends Command
  case class Logout(timeout: FiniteDuration, replyTo: ActorRef[LogoutReply]) extends Command
  case class GetShards(replyTo: ActorRef[GetShardsReply])                    extends Command
  case class GetMusicManager(replyTo: ActorRef[GetMusicManagerReply])        extends Command
  case class GetRatelimiter(replyTo: ActorRef[GetRatelimiterReply])          extends Command

  case class LogoutReply(done: Future[Boolean])
  case class GetShardsReply(shards: Seq[ActorRef[DiscordShard.Command]])
  case class GetMusicManagerReply(musicManager: ActorRef[MusicManager.Command])
  case class GetRatelimiterReply(ratelimiter: ActorRef[Ratelimiter.Command])
}
