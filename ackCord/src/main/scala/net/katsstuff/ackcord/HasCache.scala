package net.katsstuff.ackcord

import scala.language.higherKinds

import cats.Id
import net.katsstuff.ackcord.commands.{Cmd, ParsedCmd, RawCmd}

trait HasCache[F[_], A] {
  def cache(a: A): CacheSnapshot[F]
}
object HasCache {
  implicit val apiMessageHasCache: HasCache[Id, APIMessage]             = (a: APIMessage) => a.cache.current
  implicit def cmdHasCache[F[_]]: HasCache[F, Cmd[F]]                   = (a: Cmd[F]) => a.cache
  implicit def parsedCmdHasCache[F[_], A]: HasCache[F, ParsedCmd[F, A]] = (a: ParsedCmd[F, A]) => a.cache
  implicit def rawCmdHasCache[F[_]]: HasCache[F, RawCmd[F]]             = (a: RawCmd[F]) => a.c
}
