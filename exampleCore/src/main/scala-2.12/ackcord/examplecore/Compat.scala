package ackcord.examplecore

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.immutable.Queue

import com.sedmelluq.discord.lavaplayer.track.AudioTrack

private[examplecore] object Compat {
  def enqueueMany(queue: Queue[AudioTrack], tail: Seq[AudioTrack]): Queue[AudioTrack] =
    queue.enqueue(tail.toIndexedSeq)

  def convertJavaList[A](jList: java.util.List[A]): Seq[A] = jList.asScala

  @tailrec
  def updateWith[K, V](map: collection.concurrent.Map[K, V], key: K)(f: Option[V] => Option[V]): Option[V] = {
    val previousValue = map.get(key)
    val nextValue     = f(previousValue)
    (previousValue, nextValue) match {
      case (None, None)                                             => None
      case (None, Some(next)) if map.putIfAbsent(key, next).isEmpty => nextValue
      case (Some(prev), None) if map.remove(key, prev)              => None
      case (Some(prev), Some(next)) if map.replace(key, prev, next) => nextValue
      case _                                                        => updateWith(map, key)(f)
    }
  }
}
