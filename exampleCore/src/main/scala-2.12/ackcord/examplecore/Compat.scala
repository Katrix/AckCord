package ackcord.examplecore

import scala.collection.mutable
import scala.collection.JavaConverters._

import com.sedmelluq.discord.lavaplayer.track.AudioTrack

private[examplecore] object Compat {
  def enqueueMany(queue: mutable.Queue[AudioTrack], tail: Seq[AudioTrack]): queue.type = {
    queue.enqueue(tail: _*)
    queue
  }

  def convertJavaList[A](jList: java.util.List[A]): Seq[A] = jList.asScala
}
