package net.katsstuff.ackcord.commands

import scala.concurrent.Future
import scala.language.higherKinds

import akka.NotUsed
import akka.stream.{Graph, SourceShape}
import akka.stream.scaladsl.Flow
import net.katsstuff.ackcord.CacheSnapshotLike

trait CmdFlowBase[A, Snapshot <: CacheSnapshotLike[F], F[_]] {

  def getCache(a: A): Snapshot

  def map[B](f: Snapshot => A => B): Flow[A, B, NotUsed] =
    Flow[A].map(a => f(getCache(a))(a))

  def mapConcat[B](f: Snapshot => A => List[B]): Flow[A, B, NotUsed] =
    Flow[A].mapConcat(a => f(getCache(a))(a))

  def mapAsync[B](parallelism: Int)(f: Snapshot => A => Future[B]): Flow[A, B, NotUsed] =
    Flow[A].mapAsync(parallelism)(a => f(getCache(a))(a))

  def mapAsyncUnordered[B](parallelism: Int)(f: Snapshot => A => Future[B]): Flow[A, B, NotUsed] =
    Flow[A].mapAsyncUnordered(parallelism)(a => f(getCache(a))(a))

  def flatMapConcat[B](f: Snapshot => A => Graph[SourceShape[B], NotUsed]): Flow[A, B, NotUsed] =
    Flow[A].flatMapConcat(a => f(getCache(a))(a))

  def flatMapMerge[B](breadth: Int)(f: Snapshot => A => Graph[SourceShape[B], NotUsed]): Flow[A, B, NotUsed] =
    Flow[A].flatMapMerge(breadth, a => f(getCache(a))(a))
}
