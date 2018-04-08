package net.katsstuff.ackcord

import scala.collection.immutable
import scala.concurrent.Future

import akka.NotUsed
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{KillSwitches, UniqueKillSwitch}
import net.katsstuff.ackcord.commands._
import net.katsstuff.ackcord.http.requests.RequestHelper
import net.katsstuff.ackcord.util.MessageParser

/**
  * An object which manages a [[Commands]] instance.
  */
trait CommandsHelper {

  /**
    * The commands object specific to this command helper.
    */
  def commands: Commands

  /**
    * The request helper to use when sending messages from this command helper.
    */
  def requests: RequestHelper

  private def runDSL[A](source: Source[RequestDSL[A], NotUsed]): (UniqueKillSwitch, Future[immutable.Seq[A]]) = {
    val helper = requests
    import helper.mat

    source
      .viaMat(KillSwitches.single)(Keep.right)
      .flatMapConcat(_.toSource(helper.flow))
      .toMat(Sink.seq)(Keep.both)
      .run()
  }

  /**
    * Run a [[RequestDSL]] with a [[CacheSnapshot]] when raw command arrives.
    *
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done and all the values it computed.
    */
  def onRawCommandDSLC[A](
      handler: CacheSnapshot => PartialFunction[RawCmd, RequestDSL[A]]
  ): (UniqueKillSwitch, Future[immutable.Seq[A]]) = {
    runDSL {
      commands.subscribe.collect {
        case cmd: RawCmd if handler(cmd.c).isDefinedAt(cmd) => handler(cmd.c)(cmd)
      }
    }
  }

  /**
    * Run a [[RequestDSL]] when raw command arrives.
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done and all the values it computed.
    */
  def onRawCommandDSL[A](
      handler: PartialFunction[RawCmd, RequestDSL[A]]
  ): (UniqueKillSwitch, Future[immutable.Seq[A]]) =
    onRawCommandDSLC { _ =>
      {
        case cmd if handler.isDefinedAt(cmd) => handler(cmd)
      }
    }

  /**
    * Run some code with a [[CacheSnapshot]] when raw command arrives.
    *
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done and all the values it computed.
    */
  def onRawCommandC[A](
      handler: CacheSnapshot => PartialFunction[RawCmd, A]
  ): (UniqueKillSwitch, Future[immutable.Seq[A]]) = {
    onRawCommandDSLC { c =>
      {
        case msg if handler(c).isDefinedAt(msg) => RequestDSL.pure(handler(c)(msg))
      }
    }
  }

  /**
    * Run some code when raw command arrives.
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done and all the values it computed.
    */
  def onRawCommand[A](handler: PartialFunction[RawCmd, A]): (UniqueKillSwitch, Future[immutable.Seq[A]]) =
    onRawCommandC { _ =>
      {
        case msg if handler.isDefinedAt(msg) => handler(msg)
      }
    }

  /**
    * Register a command which runs a [[RequestDSL]] with a [[CacheSnapshot]].
    *
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done and all the values it computed.
    */
  def registerCommandDSLC[A: MessageParser, B](
      category: CmdCategory,
      aliases: Seq[String],
      filters: Seq[CmdFilter] = Nil,
      description: Option[CmdDescription] = None
  )(handler: CacheSnapshot => ParsedCmd[A] => RequestDSL[B]): (UniqueKillSwitch, Future[immutable.Seq[B]]) = {
    val sink = (requests: RequestHelper) => {
      ParsedCmdFlow[A]
        .map(handler)
        .flatMapConcat(_.toSource(requests.flow))
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(Sink.seq)(Keep.both)
    }

    val factory = ParsedCmdFactory(category, aliases, sink, filters, description)

    commands.subscribe(factory)(Keep.right)
  }

  /**
    * Register a command which runs a [[RequestDSL]].
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done and all the values it computed.
    */
  def registerCommandDSL[A: MessageParser, B](
      category: CmdCategory,
      aliases: Seq[String],
      filters: Seq[CmdFilter] = Nil,
      description: Option[CmdDescription] = None
  )(handler: ParsedCmd[A] => RequestDSL[B]): (UniqueKillSwitch, Future[immutable.Seq[B]]) = {
    val sink = (requests: RequestHelper) => {
      ParsedCmdFlow[A]
        .map(_ => handler)
        .flatMapConcat(_.toSource(requests.flow))
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(Sink.seq)(Keep.both)
    }

    val factory = ParsedCmdFactory(category, aliases, sink, filters, description)

    commands.subscribe(factory)(Keep.right)
  }

  /**
    * Register a command which runs some code with a [[CacheSnapshot]].
    *
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done and all the values it computed.
    */
  def registerCommandC[A: MessageParser, B](
      category: CmdCategory,
      aliases: Seq[String],
      filters: Seq[CmdFilter] = Nil,
      description: Option[CmdDescription] = None
  )(handler: CacheSnapshot => ParsedCmd[A] => B): (UniqueKillSwitch, Future[immutable.Seq[B]]) = {
    val sink = (_: RequestHelper) => {
      ParsedCmdFlow[A]
        .map(handler)
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(Sink.seq)(Keep.both)
    }

    val factory = ParsedCmdFactory(category, aliases, sink, filters, description)

    commands.subscribe(factory)(Keep.right)
  }

  /**
    * Register a command which runs some code.
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done and all the values it computed.
    */
  def registerCommand[A: MessageParser](
      category: CmdCategory,
      aliases: Seq[String],
      filters: Seq[CmdFilter] = Nil,
      description: Option[CmdDescription] = None
  )(handler: ParsedCmd[A] => Unit): (UniqueKillSwitch, Future[immutable.Seq[Unit]]) = {
    val sink = (_: RequestHelper) => {
      ParsedCmdFlow[A]
        .map(_ => handler)
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(Sink.seq)(Keep.both)
    }

    val factory = ParsedCmdFactory(category, aliases, sink, filters, description)

    commands.subscribe(factory)(Keep.right)
  }

  /**
    * Registers an [[CommandHandler]] that will be called when that command is used.
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done and all the values it computed.
    */
  def registerHandler[A: MessageParser, B](
      handler: CommandHandler[A, B]
  ): (UniqueKillSwitch, Future[immutable.Seq[B]]) =
    registerCommandC[A, B](handler.category, handler.aliases, handler.filters, handler.description) {
      implicit c => parsed =>
        handler.handle(parsed.msg, parsed.args, parsed.remaining)
    }

  /**
    * Registers an [[CommandHandlerDSL]] that will be run when that command is used.
    * @return A kill switch to cancel this listener, and a future representing
    *         when it's done and all the values it computed.
    */
  def registerHandler[A: MessageParser, B](
      handler: CommandHandlerDSL[A, B]
  ): (UniqueKillSwitch, Future[immutable.Seq[B]]) =
    registerCommandDSLC[A, B](handler.category, handler.aliases, handler.filters, handler.description) {
      implicit c => parsed =>
        handler.handle(parsed.msg, parsed.args, parsed.remaining)
    }
}
case class SeperateCommandsHelper(commands: Commands, requests: RequestHelper) extends CommandsHelper
