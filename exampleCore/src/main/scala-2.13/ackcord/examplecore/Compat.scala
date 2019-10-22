package ackcord.examplecore

import scala.collection.mutable
import scala.jdk.CollectionConverters._

import com.sedmelluq.discord.lavaplayer.track.AudioTrack

private[examplecore] object Compat {
  def enqueueMany(queue: mutable.Queue[AudioTrack], tail: Seq[AudioTrack]): queue.type = {
    queue.enqueueAll(tail)
  }

  def convertJavaList[A](jList: java.util.List[A]): Seq[A] = jList.asScala.toSeq
}
