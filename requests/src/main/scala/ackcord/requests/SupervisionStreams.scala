package ackcord.requests

import akka.actor.typed.ActorSystem
import akka.stream.javadsl.RunnableGraph
import akka.stream.{ActorAttributes, Attributes, Supervision}
import akka.stream.scaladsl.{Flow, Sink, Source}

object SupervisionStreams {

  def addLogAndContinueFunction[G](addAtributes: Attributes => G)(implicit system: ActorSystem[Nothing]): G =
    addAtributes(ActorAttributes.supervisionStrategy { e =>
      system.log.error("Unhandled exception in stream", e)
      Supervision.Resume
    })

  def logAndContinue[M](graph: RunnableGraph[M])(implicit system: ActorSystem[Nothing]): RunnableGraph[M] =
    addLogAndContinueFunction(graph.addAttributes)

  def logAndContinue[Out, Mat](source: Source[Out, Mat])(implicit system: ActorSystem[Nothing]): Source[Out, Mat] =
    addLogAndContinueFunction(source.addAttributes)

  def logAndContinue[In, Out, Mat](flow: Flow[In, Out, Mat])(implicit system: ActorSystem[Nothing]): Flow[In, Out, Mat] =
    addLogAndContinueFunction(flow.addAttributes)

  def logAndContinue[In, Mat](sink: Sink[In, Mat])(implicit system: ActorSystem[Nothing]): Sink[In, Mat] =
    addLogAndContinueFunction(sink.addAttributes)
}
