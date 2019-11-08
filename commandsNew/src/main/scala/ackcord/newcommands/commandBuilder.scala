/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2019 Katrix
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package ackcord.newcommands

import ackcord.CacheSnapshot
import ackcord.data._
import ackcord.requests.{Request, RequestHelper}
import ackcord.util.Streamable
import akka.NotUsed
import akka.stream.FlowShape
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, Merge, Partition, Sink}
import cats.data.OptionT
import cats.~>

/**
  * A mapping over command builders.
  * @tparam I The input message type
  * @tparam O The output message type
  */
trait CommandFunction[-I[_], +O[_]] { self =>

  /**
    * A flow that represents this mapping.
    */
  def flow[A]: Flow[I[A], Either[Option[CommandError], O[A]], NotUsed]

  /**
    * Chains first this function, and then another one.
    */
  def andThen[O2[_]](that: CommandFunction[O, O2]): CommandFunction[I, O2] = new CommandFunction[I, O2] {
    override def flow[A]: Flow[I[A], Either[Option[CommandError], O2[A]], NotUsed] =
      CommandFunction.flowViaEither(self.flow[A], that.flow[A])(Keep.right)
  }
}
object CommandFunction {

  /**
    * Flow for short circuiting eithers.
    */
  def flowViaEither[I, M, O, E, Mat1, Mat2, Mat3](
      flow1: Flow[I, Either[E, M], Mat1],
      flow2: Flow[M, Either[E, O], Mat2]
  )(combine: (Mat1, Mat2) => Mat3): Flow[I, Either[E, O], Mat3] = {
    Flow.fromGraph(GraphDSL.create(flow1, flow2)(combine) { implicit b => (selfFlow, thatFlow) =>
      import GraphDSL.Implicits._

      val selfPartition =
        b.add(Partition[Either[E, M]](2, {
          case Left(_)  => 0
          case Right(_) => 1
        }))
      val selfErr = selfPartition.out(0).map(_.asInstanceOf[Either[E, O]])
      val selfOut = selfPartition.out(1).map(_.getOrElse(sys.error("impossible")))

      val thatPartition =
        b.add(Partition[Either[E, O]](2, {
          case Left(_)  => 0
          case Right(_) => 1
        }))
      val thatErr = thatPartition.out(0)
      val thatOut = thatPartition.out(1)

      val resMerge = b.add(Merge[Either[E, O]](3))

      // format: OFF
      selfFlow ~> selfPartition
                  selfOut       ~> thatFlow ~> thatPartition
                  selfErr                                    ~> resMerge
                                               thatOut       ~> resMerge
                                               thatErr       ~> resMerge
      // format: ON

      FlowShape(
        selfFlow.in,
        resMerge.out
      )
    })
  }
}

/**
  * A [[CommandFunction]] that can't fail, but might return a different
  * message type.
  * @tparam I The input message type
  * @tparam O The output message type
  */
trait CommandTransformer[-I[_], +O[_]] extends CommandFunction[I, O] { self =>

  /**
    * The flow representing this mapping without the eithers.
    */
  def flowMapper[A]: Flow[I[A], O[A], NotUsed]

  override def flow[A]: Flow[I[A], Either[Option[CommandError], O[A]], NotUsed] = flowMapper.map(Right.apply)

  override def andThen[O2[_]](that: CommandFunction[O, O2]): CommandFunction[I, O2] =
    new CommandFunction[I, O2] {
      override def flow[A]: Flow[I[A], Either[Option[CommandError], O2[A]], NotUsed] = flowMapper.via(that.flow)
    }

  /**
    * Chains first this transformer, and then another one. More efficient than
    * the base andThen function.
    */
  def andThen[O2[_]](that: CommandTransformer[O, O2]): CommandTransformer[I, O2] =
    new CommandTransformer[I, O2] {
      override def flowMapper[A]: Flow[I[A], O2[A], NotUsed] = self.flowMapper.via(that.flowMapper)
    }
}
object CommandTransformer {

  /**
    * Converts a [[cats.arrow.FunctionK]] to an [[CommandTransformer]].
    */
  def fromFuncK[I[_], O[_]](f: I ~> O): CommandTransformer[I, O] = new CommandTransformer[I, O] {
    override def flowMapper[A]: Flow[I[A], O[A], NotUsed] = Flow[I[A]].map(f(_))
  }
}

/**
  * A [[CommandFunction]] from a command message to an output. Used for
  * creating commands.
  * @tparam M The command message type used by the command.
  * @tparam A The argument type of this command builder.
  */
trait CommandBuilder[+M[_], A] extends CommandFunction[CommandMessage, M] { self =>

  /**
    * A request helper that belongs to this builder.
    */
  def requests: RequestHelper

  /**
    * The parser used for parsing the arguments this command takes.
    */
  def parser: MessageParser[A]

  /**
    * Converts this builder into a builder that will create [[NamedComplexCommand]].
    * These don't need to be provided a name when registering them.
    *
    * @param namedSymbol The symbol to use when invoking the command
    * @param namedAliases The valid aliases to use when invoking the command
    * @param mustMention If the command requires a mention
    * @param aliasesCaseSensitive If the command aliases should be matched with case sensitivity
    */
  def named(
      namedSymbol: String,
      namedAliases: Seq[String],
      mustMention: Boolean = true,
      aliasesCaseSensitive: Boolean = false
  ): NamedCommandBuilder[M, A] =
    new NamedCommandBuilder[M, A] {
      override def symbol: String = namedSymbol

      override def aliases: Seq[String] = namedAliases

      override def requiresMention: Boolean = mustMention

      override def caseSensitive: Boolean = aliasesCaseSensitive

      override def requests: RequestHelper = self.requests

      override def parser: MessageParser[A] = self.parser

      override def flow[B]: Flow[CommandMessage[B], Either[Option[CommandError], M[B]], NotUsed] = self.flow[B]
    }

  /**
    * Creates a new command builder parsing a specific type.
    * @tparam B The type to parse
    */
  def parsing[B](implicit newParser: MessageParser[B]): CommandBuilder[M, B] = new CommandBuilder[M, B] {
    override def requests: RequestHelper = self.requests

    override def parser: MessageParser[B] = newParser

    override def flow[C]: Flow[CommandMessage[C], Either[Option[CommandError], M[C]], NotUsed] = self.flow
  }

  /**
    * Creates a command from a sink.
    * @param sinkBlock The sink that will process this command.
    * @tparam Mat The materialized result of running this command.
    */
  def streamed[Mat](sinkBlock: Sink[M[A], Mat]): ComplexCommand[A, Mat] = new ComplexCommand[A, Mat] {
    override def parser: MessageParser[A] = self.parser

    override def flow: Flow[CommandMessage[A], CommandError, Mat] =
      CommandBuilder.streamedFlow(sinkBlock, self.flow[A])
  }

  /**
    * Creates a command that results in some streamable type G
    * @param block The execution of the command.
    * @tparam G The streamable result type.
    */
  def async[G[_]](block: M[A] => G[Unit])(implicit streamable: Streamable[G]): ComplexCommand[A, NotUsed] =
    streamed(Flow[M[A]].flatMapConcat(m => streamable.toSource(block(m))).to(Sink.ignore))

  /**
    * Creates a command that might do a single request, wrapped in an effect type G
    * @param block The execution of the command.
    * @tparam G The streamable result type.
    */
  def asyncOptRequest[G[_]](
      block: M[A] => OptionT[G, Request[Any]]
  )(implicit streamable: Streamable[G]): ComplexCommand[A, NotUsed] =
    streamed(Flow[M[A]].flatMapConcat(m => streamable.optionToSource(block(m))).to(requests.sinkIgnore))

  /**
    * Creates a command that will do a single request
    * @param block The execution of the command.
    */
  def withRequest(block: M[A] => Request[Any]): ComplexCommand[A, NotUsed] =
    streamed(Flow[M[A]].map(block).to(requests.sinkIgnore))

  /**
    * Creates a command that might do a single request
    * @param block The execution of the command.
    */
  def withRequestOpt(block: M[A] => Option[Request[Any]]): ComplexCommand[A, NotUsed] =
    streamed(Flow[M[A]].mapConcat(block(_).toList).to(requests.sinkIgnore))

  /**
    * Creates a command that might execute unknown side effects.
    * @param block The execution of the command.
    */
  def withSideEffects(block: M[A] => Unit): ComplexCommand[A, NotUsed] =
    streamed(Sink.foreach(block).mapMaterializedValue(_ => NotUsed))

  override def andThen[M2[_]](f: CommandFunction[M, M2]): CommandBuilder[M2, A] = new CommandBuilder[M2, A] {
    override def requests: RequestHelper = self.requests

    override def parser: MessageParser[A] = self.parser

    override def flow[C]: Flow[CommandMessage[C], Either[Option[CommandError], M2[C]], NotUsed] =
      CommandFunction.flowViaEither(self.flow[C], f.flow[C])(Keep.right)
  }
}
object CommandBuilder {

  /**
    * A command function that only allows commands sent from a guild, and that
    * lets you build the result command message.
    */
  def onlyInGuild[I[A] <: CommandMessage[A], O[_]](
      create: (TGuildChannel, Guild) => I ~> O
  ): CommandFunction[I, O] =
    new CommandFunction[I, O] {

      type Result[A] = Either[Option[CommandError], O[A]]

      override def flow[A]: Flow[I[A], Result[A], NotUsed] =
        Flow[I[A]].map { m =>
          implicit val c: CacheSnapshot = m.cache

          lazy val e: Result[A] = Left(Some(CommandError.mk(s"This command can only be used in a guild", m)))

          m.tChannel match {
            case chG: TGuildChannel =>
              chG.guild.fold[Result[A]](e) { g =>
                Right(create(chG, g)(m))
              }
            case _ => e
          }
        }
    }

  /**
    * A command function that lets you add the guild member to a command message.
    */
  def withGuildMember[I[A] <: GuildCommandMessage[A] with UserCommandMessage[A], O[
      _
  ]](create: GuildMember => I ~> O): CommandTransformer[I, O] = new CommandTransformer[I, O] {
    override def flowMapper[A]: Flow[I[A], O[A], NotUsed] =
      Flow[I[A]].mapConcat { m =>
        m.guild.members.get(m.user.id).map(member => create(member)(m)).toList
      }
  }

  def inVoiceChannel[I[A] <: GuildCommandMessage[A] with UserCommandMessage[A], O[_]](
      create: VGuildChannel => I ~> O
  ): CommandFunction[I, O] = new CommandFunction[I, O] {
    type Result[A] = Either[Option[CommandError], O[A]]

    override def flow[A]: Flow[I[A], Result[A], NotUsed] = Flow[I[A]].map { m =>
      implicit val c: CacheSnapshot = m.cache
      m.guild.voiceStates
        .get(m.user.id)
        .flatMap(_.channelId)
        .flatMap(_.vResolve(m.guild.id))
        .toRight(
          Some(CommandError.mk(s"This command can only be used while in a voice channel", m)): Option[CommandError]
        )
        .map(vCh => create(vCh)(m))
    }
  }

  /**
    * A command function that only allow commands sent from one specific guild.
    */
  def inOneGuild[M[A] <: GuildCommandMessage[A]](
      guildId: GuildId
  ): CommandFunction[M, M] =
    new CommandFunction[M, M] {
      override def flow[A]: Flow[M[A], Either[Option[CommandError], M[A]], NotUsed] =
        Flow[M[A]].map(m => Either.cond(m.guild.id == guildId, m, None))
    }

  /**
    * A command function that requires that those who use this command need
    * some set of permissions.
    */
  def needPermission[M[A] <: GuildCommandMessage[A]](
      neededPermission: Permission
  ): CommandFunction[M, M] = new CommandFunction[M, M] {
    override def flow[A]: Flow[M[A], Either[Option[CommandError], M[A]], NotUsed] =
      Flow[M[A]].map { m =>
        val guild = m.guild

        val allowed = guild.members
          .get(UserId(m.message.authorId))
          .exists(_.channelPermissionsId(guild, m.message.channelId).hasPermissions(neededPermission))

        if (allowed) Right(m)
        else Left(Some(CommandError("You don't have permission to use this command", m.tChannel, m.cache)))
      }
  }

  /**
    * A command function that disallows bots from using it.
    */
  def nonBot[I[A] <: CommandMessage[A], O[_]](
      create: User => I ~> O
  ): CommandFunction[I, O] =
    new CommandFunction[I, O] {
      override def flow[A]: Flow[I[A], Either[Option[CommandError], O[A]], NotUsed] =
        Flow[I[A]].map { m =>
          implicit val c: CacheSnapshot = m.cache
          m.message.authorUser
            .filter(!_.bot.getOrElse(false))
            .toRight(None)
            .map(u => create(u)(m))
        }
    }

  /**
    * Creates a raw command builder without any extra processing.
    */
  def rawBuilder(requestHelper: RequestHelper): CommandBuilder[CommandMessage, NotUsed] =
    new CommandBuilder[CommandMessage, NotUsed] {
      override def requests: RequestHelper = requestHelper

      override def parser: MessageParser[NotUsed] = MessageParser.notUsedParser

      override def flow[A]: Flow[CommandMessage[A], Either[Option[CommandError], CommandMessage[A]], NotUsed] =
        Flow[CommandMessage[A]].map(Right.apply)
    }

  private[ackcord] def streamedFlow[M[_], A, Mat](
      sinkBlock: Sink[M[A], Mat],
      selfFlow: Flow[CommandMessage[A], Either[Option[CommandError], M[A]], NotUsed]
  ): Flow[CommandMessage[A], CommandError, Mat] = {

    Flow.fromGraph(GraphDSL.create(sinkBlock) { implicit b => block =>
      import GraphDSL.Implicits._
      val selfFlowShape = b.add(selfFlow)

      val selfPartition = b.add(Partition[Either[Option[CommandError], M[A]]](2, {
        case Left(_)  => 0
        case Right(_) => 1
      }))
      val selfErr = selfPartition.out(0).map(_.swap.getOrElse(sys.error("impossible"))).mapConcat(_.toList)
      val selfOut = selfPartition.out(1).map(_.getOrElse(sys.error("impossible")))

      selfFlowShape ~> selfPartition
      selfOut ~> block

      FlowShape(
        selfFlowShape.in,
        selfErr.outlet
      )
    })
  }
}

/**
  * A [[CommandFunction]] from a command message to an output. Used for
  * creating commands.
  * @tparam M The command message type used by the command.
  * @tparam A The argument type of this command builder.
  */
trait NamedCommandBuilder[+M[_], A] extends CommandBuilder[M, A] { self =>

  /**
    * The prefix symbol to use for the command this builder will create.
    */
  def symbol: String

  /**
    * The valid aliases for the command this builder will create.
    */
  def aliases: Seq[String]

  /**
    * If the command this builder will create requires a mention when invoking it.
    */
  def requiresMention: Boolean

  /**
    * If the aliases of the command this builder will create should be case sensitive
    */
  def caseSensitive: Boolean

  /**
    * Creates a new command builder parsing a specific type.
    * @tparam B The type to parse
    */
  override def parsing[B](implicit newParser: MessageParser[B]): NamedCommandBuilder[M, B] =
    new NamedCommandBuilder[M, B] {

      override def symbol: String = self.symbol

      override def aliases: Seq[String] = self.aliases

      override def requiresMention: Boolean = self.requiresMention

      override def caseSensitive: Boolean = self.caseSensitive

      override def requests: RequestHelper = self.requests

      override def parser: MessageParser[B] = newParser

      override def flow[C]: Flow[CommandMessage[C], Either[Option[CommandError], M[C]], NotUsed] = self.flow
    }

  override def streamed[Mat](sinkBlock: Sink[M[A], Mat]): NamedComplexCommand[A, Mat] =
    new NamedComplexCommand[A, Mat] {
      override def symbol: String = self.symbol

      override def aliases: Seq[String] = self.aliases

      override def requiresMention: Boolean = self.requiresMention

      override def caseSensitive: Boolean = self.caseSensitive

      override def parser: MessageParser[A] = self.parser

      override def flow: Flow[CommandMessage[A], CommandError, Mat] =
        CommandBuilder.streamedFlow(sinkBlock, self.flow[A])
    }

  override def async[G[_]](
      block: M[A] => G[Unit]
  )(implicit streamable: Streamable[G]): NamedComplexCommand[A, NotUsed] =
    streamed(Flow[M[A]].flatMapConcat(m => streamable.toSource(block(m))).to(Sink.ignore))

  override def asyncOptRequest[G[_]](
      block: M[A] => OptionT[G, Request[Any]]
  )(implicit streamable: Streamable[G]): NamedComplexCommand[A, NotUsed] =
    streamed(Flow[M[A]].flatMapConcat(m => streamable.optionToSource(block(m))).to(requests.sinkIgnore))

  override def withRequest(block: M[A] => Request[Any]): NamedComplexCommand[A, NotUsed] =
    streamed(Flow[M[A]].map(block).to(requests.sinkIgnore))

  override def withRequestOpt(block: M[A] => Option[Request[Any]]): NamedComplexCommand[A, NotUsed] =
    streamed(Flow[M[A]].mapConcat(block(_).toList).to(requests.sinkIgnore))

  override def withSideEffects(block: M[A] => Unit): NamedComplexCommand[A, NotUsed] =
    streamed(Sink.foreach(block).mapMaterializedValue(_ => NotUsed))

  override def andThen[M2[_]](f: CommandFunction[M, M2]): NamedCommandBuilder[M2, A] =
    new NamedCommandBuilder[M2, A] {

      override def symbol: String = self.symbol

      override def aliases: Seq[String] = self.aliases

      override def requiresMention: Boolean = self.requiresMention

      override def caseSensitive: Boolean = self.caseSensitive

      override def requests: RequestHelper = self.requests

      override def parser: MessageParser[A] = self.parser

      override def flow[C]: Flow[CommandMessage[C], Either[Option[CommandError], M2[C]], NotUsed] =
        CommandFunction.flowViaEither(self.flow[C], f.flow[C])(Keep.right)
    }
}

/**
  * A message sent with an invocation of a command.
  * @tparam A The parsed argument type
  */
trait CommandMessage[+A] {

  /**
    * Easy access to a request helper.
    */
  def requests: RequestHelper

  /**
    * A cache snapshot taken when the command was used.
    */
  def cache: CacheSnapshot

  /**
    * The channel the command was used from.
    */
  def tChannel: TChannel

  /**
    * The message that invoked the command.
    */
  def message: Message

  /**
    * The parsed arguments of this command.
    */
  def parsed: A
}
object CommandMessage {

  implicit def findCache[A](implicit message: CommandMessage[A]): CacheSnapshot = message.cache

  case class Default[A](
      requests: RequestHelper,
      cache: CacheSnapshot,
      tChannel: TChannel,
      message: Message,
      parsed: A
  ) extends CommandMessage[A]
}

class WrappedCommandMessage[A](m: CommandMessage[A]) extends CommandMessage[A] {
  override def requests: RequestHelper = m.requests

  override def cache: CacheSnapshot = m.cache

  override def tChannel: TChannel = m.tChannel

  override def message: Message = m.message

  override def parsed: A = m.parsed
}

/**
  * A message sent with the invocation of a guild command
  * @tparam A The parsed argument type
  */
trait GuildCommandMessage[+A] extends CommandMessage[A] {
  override def tChannel: TGuildChannel

  /**
    * The guild this command was used in.
    */
  def guild: Guild
}
object GuildCommandMessage {

  case class Default[A](
      override val tChannel: TGuildChannel,
      guild: Guild,
      m: CommandMessage[A]
  ) extends WrappedCommandMessage(m)
      with GuildCommandMessage[A]

  case class WithUser[A](
      override val tChannel: TGuildChannel,
      guild: Guild,
      user: User,
      m: CommandMessage[A]
  ) extends WrappedCommandMessage(m)
      with GuildCommandMessage[A]
      with UserCommandMessage[A]
}

/**
  * A message sent with the invocation of command used by a user
  * @tparam A The parsed argument type
  */
trait UserCommandMessage[+A] extends CommandMessage[A] {

  /**
    * The user that used this command.
    */
  def user: User
}
object UserCommandMessage {

  case class Default[A](user: User, m: CommandMessage[A]) extends WrappedCommandMessage(m) with UserCommandMessage[A]
}

trait GuildMemberCommandMessage[+A] extends GuildCommandMessage[A] with UserCommandMessage[A] {

  /**
    * The guild member that used this command.
    */
  def guildMember: GuildMember
}
object GuildMemberCommandMessage {

  case class Default[A](
      override val tChannel: TGuildChannel,
      guild: Guild,
      user: User,
      guildMember: GuildMember,
      m: CommandMessage[A]
  ) extends WrappedCommandMessage(m)
      with GuildMemberCommandMessage[A]
}

trait VoiceGuildCommandMessage[+A] extends GuildCommandMessage[A] with UserCommandMessage[A] {

  /**
    * The voice channel the user that used this command is in.
    */
  def vChannel: VGuildChannel
}
object VoiceGuildCommandMessage {

  case class Default[A](
      override val tChannel: TGuildChannel,
      guild: Guild,
      user: User,
      vChannel: VGuildChannel,
      m: CommandMessage[A]
  ) extends WrappedCommandMessage(m)
      with VoiceGuildCommandMessage[A]

  case class WithGuildMember[A](
      override val tChannel: TGuildChannel,
      guild: Guild,
      user: User,
      guildMember: GuildMember,
      vChannel: VGuildChannel,
      m: CommandMessage[A]
  ) extends WrappedCommandMessage(m)
      with VoiceGuildCommandMessage[A]
      with GuildMemberCommandMessage[A]
}

/**
  * Represents an error encountered when executing an command.
  * @param error The errror message
  * @param channel The channel the error occoured in
  * @param cache A cache snapshot tied to the execution of the command
  */
case class CommandError(error: String, channel: TChannel, cache: CacheSnapshot)
object CommandError {
  def mk[A](error: String, message: CommandMessage[A]): CommandError =
    CommandError(error, message.tChannel, message.cache)
}
