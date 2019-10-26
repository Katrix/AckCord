package ackcord.examplecore

import scala.collection.JavaConverters._
import scala.collection.immutable.Queue

import com.sedmelluq.discord.lavaplayer.track.AudioTrack

private[examplecore] object Compat {
  def enqueueMany(queue: Queue[AudioTrack], tail: Seq[AudioTrack]): Queue[AudioTrack] =
    queue.enqueue(tail: _*)

  def convertJavaList[A](jList: java.util.List[A]): Seq[A] = jList.asScala
}
