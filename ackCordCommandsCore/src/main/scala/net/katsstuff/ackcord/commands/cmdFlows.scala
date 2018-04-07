package net.katsstuff.ackcord.commands

import cats.Id
import net.katsstuff.ackcord.CacheSnapshot

/**
  * A class to extract the cache from a parsed cmd object.
  */
class ParsedCmdFlow[A] extends CmdFlowBase[ParsedCmd[A], CacheSnapshot, Id] {
  override def getCache(a: ParsedCmd[A]): CacheSnapshot = a.cache
}
object ParsedCmdFlow {
  def apply[A] = new ParsedCmdFlow[A]
}

/**
  * An object to extract the cache from a unparsed cmd object.
  */
object CmdFlow extends CmdFlowBase[Cmd, CacheSnapshot, Id] {
  override def getCache(a: Cmd): CacheSnapshot = a.cache
}
