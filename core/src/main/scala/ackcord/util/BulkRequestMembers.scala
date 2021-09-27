package ackcord.util

import java.util.UUID

import scala.concurrent.Future
import scala.concurrent.duration._

import ackcord.Events
import ackcord.data.{GuildId, UserId}
import ackcord.gateway.GatewayEvent.{
  GuildMemberChunk,
  GuildMemberChunkData,
  RawGuildMemberWithGuild
}
import ackcord.gateway.{Dispatch, RequestGuildMembers, RequestGuildMembersData}
import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import cats.data.Ior

object BulkRequestMembers {

  /**
    * Request members for all the users passed in.
    * @param guild
    *   The guild to get the members for the users in.
    * @param userIds
    *   The users to get the members for.
    * @param events
    *   An events instance to send the requests with.
    * @return
    *   A source containing the members for the user ids passed in. The
    *   materialized value of the source is all the members not found. The
    *   returned members are represented with [[RawGuildMemberWithGuild]] which
    *   is like a [[ackcord.data.GuildMember]] together with a
    *   [[ackcord.data.User]].
    */
  def sourceUserIds(
      guild: GuildId,
      userIds: Seq[UserId],
      events: Events,
      timeout: FiniteDuration = 30.seconds
  )(implicit
      system: ActorSystem[Nothing]
  ): Source[RawGuildMemberWithGuild, Future[Seq[(GuildId, UserId)]]] = {
    require(userIds.nonEmpty, "Must request at least one member")
    val nonce = UUID.randomUUID().toString.replace("-", "")

    source(
      RequestGuildMembers(
        RequestGuildMembersData(
          guild,
          None,
          None,
          presences = false,
          Some(userIds),
          Some(nonce)
        )
      ),
      events,
      nonce,
      timeout
    )
  }

  /**
    * Request members for all the users passed in.
    *
    * Beware that this method uses more memory than consuming the elements as
    * they are gotten using [[sourceUserIds]].
    *
    * @param guild
    *   The guild to get the members for the users in.
    * @param userIds
    *   The users to get the members for.
    * @param events
    *   An events instance to send the requests with.
    * @return
    *   A future Ior containing on the left members not found, and on the right
    *   members found. The returned members are represented with
    *   [[RawGuildMemberWithGuild]] which is like a [[ackcord.data.GuildMember]]
    *   together with a [[ackcord.data.User]].
    */
  def seqUserIds(
      guild: GuildId,
      userIds: Seq[UserId],
      events: Events,
      timeout: FiniteDuration = 30.seconds
  )(implicit
      system: ActorSystem[Nothing]
  ): Future[Ior[Seq[(GuildId, UserId)], Seq[RawGuildMemberWithGuild]]] = {
    import system.executionContext
    val (futureNotFound, futureMembers) =
      sourceUserIds(guild, userIds, events, timeout)
        .toMat(Sink.seq)(Keep.both)
        .run()

    for {
      notFound <- futureNotFound
      members <- futureMembers
    } yield (notFound, members) match {
      case (Seq(), result)  => Ior.Right(result)
      case (errors, Seq())  => Ior.Left(errors)
      case (Seq(), Seq())   => Ior.Left(Seq()) //Should be unreachable
      case (errors, result) => Ior.Both(errors, result)
    }
  }

  /**
    * Request members for a given username query.
    * @param guild
    *   The guild to get the members in.
    * @param query
    *   The username query to search for. Must not be empty.
    * @param events
    *   An events instance to send the requests with.
    * @param limit
    *   Max amount of members to get.
    * @return
    *   A source containing members who's username starts with the query. The
    *   returned members are represented with [[RawGuildMemberWithGuild]] which
    *   is like a [[ackcord.data.GuildMember]] together with a
    *   [[ackcord.data.User]].
    */
  def sourceUsernameQuery(
      guild: GuildId,
      query: String,
      events: Events,
      limit: Int = 100,
      timeout: FiniteDuration = 30.seconds
  )(implicit
      system: ActorSystem[Nothing]
  ): Source[RawGuildMemberWithGuild, NotUsed] = {
    require(
      query.nonEmpty,
      """|Requesting all members of a guild might have adverse effects on other parts of AckCord. 
         |If you think this is something you need, and there is no other better way to achieve what you want,
         |send a raw gateway request instead""".stripMargin
    )
    val nonce = UUID.randomUUID().toString.replace("-", "")

    source(
      RequestGuildMembers(
        RequestGuildMembersData(
          guild,
          Some(query),
          Some(limit),
          presences = false,
          None,
          Some(nonce)
        )
      ),
      events,
      nonce,
      timeout
    ).mapMaterializedValue(_ => NotUsed)
  }

  /**
    * Request members for a given username query.
    * @param guild
    *   The guild to get the members in.
    * @param query
    *   The username query to search for. Must not be empty.
    * @param events
    *   An events instance to send the requests with.
    * @param limit
    *   Max amount of members to get.
    * @return
    *   A future seq containing members who's username starts with the query.
    *   The returned members are represented with [[RawGuildMemberWithGuild]]
    *   which is like a [[ackcord.data.GuildMember]] together with a
    *   [[ackcord.data.User]].
    */
  def seqUsernameQuery(
      guild: GuildId,
      query: String,
      events: Events,
      limit: Int = 100,
      timeout: FiniteDuration = 30.seconds
  )(implicit
      system: ActorSystem[Nothing]
  ): Future[Seq[RawGuildMemberWithGuild]] =
    sourceUsernameQuery(guild, query, events, limit, timeout).runWith(Sink.seq)

  private def source(
      request: RequestGuildMembers,
      events: Events,
      nonce: String,
      timeout: FiniteDuration
  )(implicit
      system: ActorSystem[Nothing]
  ): Source[RawGuildMemberWithGuild, Future[Seq[(GuildId, UserId)]]] = {
    val sendRequest = Source.single(request).to(events.toGatewayPublish)

    val getMembers: Source[
      (Seq[RawGuildMemberWithGuild], Seq[(GuildId, UserId)]),
      NotUsed
    ] =
      events.fromGatewaySubscribe
        .collect { case Dispatch(_, GuildMemberChunk(_, data), _) =>
          data.value match {
            case Left(e)          => Source.failed(e)
            case Right(chunkData) => Source.single(chunkData)
          }
        }
        .flatMapConcat(identity)
        .takeWhile(
          chunk => chunk.chunkIndex < (chunk.chunkCount - 1),
          inclusive = true
        )
        .collect {
          case GuildMemberChunkData(
                guildId,
                members,
                _,
                _,
                notFound,
                _,
                Some(`nonce`)
              ) =>
            (
              members.map(RawGuildMemberWithGuild(guildId, _)),
              notFound.toSeq.flatten.map(guildId -> _)
            )
        }

    //We do this to only send the request when there is demand for it
    val combined = Source.lazySingle { () =>
      sendRequest.run()
      Left(())
    } ++ getMembers.map(Right(_))

    combined
      .drop(1)
      .map {
        case Left(_)      => sys.error("impossible")
        case Right(value) => value
      }
      .alsoToMat(
        Flow[(Seq[RawGuildMemberWithGuild], Seq[(GuildId, UserId)])]
          .map(_._2)
          .mapConcat(_.toVector)
          .toMat(Sink.seq)(Keep.right)
      )(
        Keep.right
      )
      .map(_._1)
      .mapConcat(_.toVector)
      .idleTimeout(timeout)
  }
}
