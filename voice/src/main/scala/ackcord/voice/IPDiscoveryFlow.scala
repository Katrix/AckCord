package ackcord.voice

import java.nio.ByteOrder

import akka.stream.scaladsl.Flow
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{
  GraphStageLogic,
  GraphStageLogicWithLogging,
  GraphStageWithMaterializedValue,
  InHandler,
  OutHandler
}
import akka.util.ByteString

import scala.concurrent.{Future, Promise}

class IPDiscoveryFlow(openValve: () => Unit)
    extends GraphStageWithMaterializedValue[FlowShape[ByteString, ByteString], Future[VoiceUDPFlow.FoundIP]] {

  val in: Inlet[ByteString]   = Inlet("IPDiscoveryFlow.in")
  val out: Outlet[ByteString] = Outlet("IPDiscoveryFlow.out")

  override def shape: FlowShape[ByteString, ByteString] = FlowShape(in, out)

  override def createLogicAndMaterializedValue(
      inheritedAttributes: Attributes
  ): (GraphStageLogic, Future[VoiceUDPFlow.FoundIP]) = {
    val promise = Promise[VoiceUDPFlow.FoundIP]
    val logic = new GraphStageLogicWithLogging(shape) with InHandler with OutHandler {

      override def onPush(): Unit = {
        val data = grab(in)
        log.debug(s"Grabbing data for IP discovery $data")
        val (addressBytes, portBytes) = data.splitAt(68)

        val address = new String(addressBytes.drop(4).toArray).trim
        val port    = portBytes.asByteBuffer.order(ByteOrder.LITTLE_ENDIAN).getShort

        promise.success(VoiceUDPFlow.FoundIP(address, port))
        log.debug("Success doing IP discovery")

        setHandler(in, new InHandler {
          override def onPush(): Unit = push(out, grab(in))
        })

        openValve()
      }

      override def onPull(): Unit = pull(in)

      override def onUpstreamFailure(ex: Throwable): Unit = {
        promise.tryFailure(new Exception("Connection failed.", ex))
        super.onUpstreamFailure(ex)
      }

      setHandlers(in, out, this)
    }

    (logic, promise.future)
  }
}
object IPDiscoveryFlow {
  def flow(openValve: () => Unit): Flow[ByteString, ByteString, Future[VoiceUDPFlow.FoundIP]] =
    Flow.fromGraph(new IPDiscoveryFlow(openValve))
}
