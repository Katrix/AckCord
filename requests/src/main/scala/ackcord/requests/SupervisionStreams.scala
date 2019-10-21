package ackcord.requests

import akka.actor.ActorSystem
import akka.stream.javadsl.RunnableGraph
import akka.stream.{ActorAttributes, Attributes, Supervision}
import akka.stream.scaladsl.{Flow, Sink, Source}

object SupervisionStreams {

  def addLogAndContinueFunction[G](addAtributes: Attributes => G)(implicit system: ActorSystem): G =
    addAtributes(ActorAttributes.supervisionStrategy { e =>
      system.log.error(e, "Unhandled exception in stream")
      Supervision.Resume
    })

  def logAndContinue[M](graph: RunnableGraph[M])(implicit system: ActorSystem): RunnableGraph[M] =
    addLogAndContinueFunction(graph.addAttributes)

  def logAndContinue[Out, Mat](source: Source[Out, Mat])(implicit system: ActorSystem): Source[Out, Mat] =
    addLogAndContinueFunction(source.addAttributes)

  def logAndContinue[In, Out, Mat](flow: Flow[In, Out, Mat])(implicit system: ActorSystem): Flow[In, Out, Mat] =
    addLogAndContinueFunction(flow.addAttributes)

  def logAndContinue[In, Mat](sink: Sink[In, Mat])(implicit system: ActorSystem): Sink[In, Mat] =
    addLogAndContinueFunction(sink.addAttributes)
}
