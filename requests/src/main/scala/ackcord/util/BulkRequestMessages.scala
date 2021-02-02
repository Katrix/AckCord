package ackcord.util

import ackcord.data.raw.RawMessage
import ackcord.data.{MessageId, TextChannelId}
import ackcord.requests.{GetChannelMessages, GetChannelMessagesData, Requests}
import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, Sink, Source}
import akka.stream.{KillSwitches, SourceShape}

import scala.concurrent.Future

object BulkRequestMessages {

  /**
    * Requests messages from Discord within the given message id range.
    * @param channel The channel to get the messages from.
    * @param from The exclusive start point getting the messages from.
    *             Note, if this is larger than `to`, then the messages are
    *             fetched in reverse.
    * @param to The inclusive end point to get messages to.
    *           Note, if this is smaller than `from`, then the messages are
    *           fetched in reverse.
    * @param requests The requests instance to use.
    * @param batchSize The amount of messages to request at once.
    * @return A source to consume the messages as they are fetched.
    */
  def source(
      channel: TextChannelId,
      from: MessageId,
      to: MessageId,
      requests: Requests,
      batchSize: Int = 100
  ): Source[RawMessage, NotUsed] = {
    require(batchSize > 0, "Batch size must be positive")
    require(batchSize <= 100, "Batch size must be less than or equal to 100")

    val reverse                                        = from > to
    def makeAfter(id: MessageId): Option[MessageId]    = if (reverse) None else Some(id)
    def makeBefore(id: MessageId): Option[MessageId]   = if (reverse) Some(id) else None
    def isAtEnd(id: MessageId): Boolean                = if (reverse) id <= to else id >= to
    def isOutsideRequestedArea(id: MessageId): Boolean = if (reverse) id < to else id > to

    val graph = GraphDSL.create() { implicit dsl =>
      import GraphDSL.Implicits._

      val seed     = dsl.add(Source.single(from))
      val afterIds = dsl.add(Merge[MessageId](2))
      val createRequests = dsl.add(
        Flow[MessageId].map { id =>
          GetChannelMessages(
            channel,
            GetChannelMessagesData(before = makeBefore(id), after = makeAfter(id), limit = Some(batchSize))
          )
        }
      )
      val runRequests = dsl
        .add(
          Flow[GetChannelMessages]
            .map(_ -> NotUsed)
            .via(
              requests.flowSuccess[Seq[RawMessage], NotUsed](ignoreFailures = false)(
                Requests.RequestProperties(retry = true)
              )
            )
            .map(_._1)
        )
      val reverseRequests   = dsl.add(if (reverse) Flow[Seq[RawMessage]] else Flow[Seq[RawMessage]].map(_.reverse))
      val broadcastReceived = dsl.add(Broadcast[Seq[RawMessage]](2))

      val completeSwitch = KillSwitches.shared("MessageRequester")
      val doneSwitch     = dsl.add(completeSwitch.flow[MessageId])

      val nextAfterId = dsl.add(
        Flow[Seq[RawMessage]]
          .mapConcat { seq =>
            if (seq.iterator.map(_.id).exists(isAtEnd) || seq.isEmpty) {
              completeSwitch.shutdown()
              Nil
            } else {
              List(seq.last.id)
            }
          }
      )

      val messagesFlatten = dsl.add(Flow[Seq[RawMessage]].mapConcat(_.toVector))

      // FORMAT: OFF

      seed ~> afterIds ~> createRequests ~> runRequests ~> reverseRequests ~> broadcastReceived ~> messagesFlatten
              afterIds <~ doneSwitch     <~ nextAfterId                    <~ broadcastReceived

      // FORMAT: ON

      SourceShape(messagesFlatten.out)
    }

    Source.fromGraph(graph).filter(m => !isOutsideRequestedArea(m.id))
  }

  /**
    * Requests messages from Discord within the given message id range.
    *
    * Beware that this method uses more memory than consuming the elements as
    * they are gotten using [[source]].
    *
    * @param channel The channel to get the messages from.
    * @param from The exclusive start point getting the messages from.
    *             Note, if this is larger than `to`, then the messages are
    *             fetched in reverse.
    * @param to The inclusive end point to get messages to.
    *           Note, if this is smaller than `from`, then the messages are
    *           fetched in reverse.
    * @param requests The requests instance to use.
    * @param batchSize The amount of messages to request at once.
    * @return A future seq containing all the messages in the range.
    */
  def futureSeq(
      channel: TextChannelId,
      from: MessageId,
      to: MessageId,
      requests: Requests,
      batchSize: Int = 100
  )(implicit system: ActorSystem[Nothing]): Future[Seq[RawMessage]] =
    source(channel, from, to, requests, batchSize).runWith(Sink.seq)
}
