package ackcord.examplecore

import scala.collection.immutable.Queue
import scala.jdk.CollectionConverters._

import com.sedmelluq.discord.lavaplayer.track.AudioTrack

private[examplecore] object Compat {
  def enqueueMany(queue: Queue[AudioTrack], tail: Seq[AudioTrack]): Queue[AudioTrack] =
    queue.enqueueAll(tail)

  def convertJavaList[A](jList: java.util.List[A]): Seq[A] = jList.asScala.toSeq
}
