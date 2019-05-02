package ackcord.lavaplayer

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler, TimerGraphStageLogicWithLogging}
import akka.stream.{Attributes, Outlet, SourceShape}
import akka.util.ByteString
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer

import scala.concurrent.duration._

class LavaplayerSource(player: AudioPlayer) extends GraphStage[SourceShape[ByteString]] {
  val out: Outlet[ByteString] = Outlet("LavaplayerSource.out")

  override def shape: SourceShape[ByteString] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new TimerGraphStageLogicWithLogging(shape) with OutHandler {
      override def onPull(): Unit =
        tryPushFrame()

      override protected def onTimer(timerKey: Any): Unit = timerKey match {
        case "RetryProvide" => tryPushFrame()
      }

      def tryPushFrame(): Unit = {
        val frame = player.provide()
        if (frame != null) {
          push(out, ByteString.fromArray(frame.getData))
        } else {
          //log.info("Scheduling attempt to provide frame")
          scheduleOnce("RetryProvide", 20.millis)
        }
      }

      setHandler(out, this)
    }
}
object LavaplayerSource {

  def source(player: AudioPlayer): Source[ByteString, NotUsed] =
    Source.fromGraph(new LavaplayerSource(player))
}
