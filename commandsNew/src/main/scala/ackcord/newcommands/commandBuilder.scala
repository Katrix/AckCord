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
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, Merge, Partition, Sink, Source}
import akka.stream.{FlowShape, SourceShape}
import cats.data.OptionT
import cats.{Applicative, Monad, ~>}
import scala.language.higherKinds

/**
  * A mapping over command builders.
  * @tparam F The cache effect type
  * @tparam I The input message type
  * @tparam O The output message type
  */
trait CommandFunction[F[_], -I[_], +O[_]] { self =>

  /**
    * A flow that represents this mapping.
    */
  def flow[A]: Flow[I[A], Either[Option[CommandError[F]], O[A]], NotUsed]

  /**
    * Chains first this function, and then another one.
    */
  def andThen[O2[_]](that: CommandFunction[F, O, O2]): CommandFunction[F, I, O2] = new CommandFunction[F, I, O2] {
    override def flow[A]: Flow[I[A], Either[Option[CommandError[F]], O2[A]], NotUsed] =
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
      val selfOut = selfPartition.out(1).map(_.right.get)

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

  /**
    * Source for short circuiting eithers.
    */
  def sourceViaEither[I, O, E, Mat1, Mat2, Mat3](
      source: Source[Either[E, I], Mat1],
      flow: Flow[I, Either[E, O], Mat2]
  )(combine: (Mat1, Mat2) => Mat3): Source[Either[E, O], Mat3] = {
    Source.fromGraph(GraphDSL.create(source, flow)(combine) { implicit b => (selfSource, thatFlow) =>
      import GraphDSL.Implicits._

      val selfPartition =
        b.add(Partition[Either[E, I]](2, {
          case Left(_)  => 0
          case Right(_) => 1
        }))
      val selfErr = selfPartition.out(0).map(_.asInstanceOf[Either[E, O]])
      val selfOut = selfPartition.out(1).map(_.right.get)

      val thatPartition =
        b.add(Partition[Either[E, O]](2, {
          case Left(_)  => 0
          case Right(_) => 1
        }))
      val thatErr = thatPartition.out(0)
      val thatOut = thatPartition.out(1)

      val resMerge = b.add(Merge[Either[E, O]](3))

      // format: OFF
      selfSource ~> selfPartition
      selfOut       ~> thatFlow ~> thatPartition
      selfErr       ~>                              resMerge
      thatOut       ~> resMerge
      thatErr       ~> resMerge
      // format: ON

      SourceShape(resMerge.out)
    })
  }
}

/**
  * A [[CommandFunction]] that can't fail, but might return a different
  * message type.
  * @tparam F The cache effect type
  * @tparam I The input message type
  * @tparam O The output message type
  */
trait CommandTransformer[F[_], -I[_], +O[_]] extends CommandFunction[F, I, O] { self =>

  /**
    * The flow representing this mapping without the eithers.
    */
  def flowMapper[A]: Flow[I[A], O[A], NotUsed]

  override def flow[A]: Flow[I[A], Either[Option[CommandError[F]], O[A]], NotUsed] = flowMapper.map(Right.apply)

  override def andThen[O2[_]](that: CommandFunction[F, O, O2]): CommandFunction[F, I, O2] =
    new CommandFunction[F, I, O2] {
      override def flow[A]: Flow[I[A], Either[Option[CommandError[F]], O2[A]], NotUsed] = flowMapper.via(that.flow)
    }

  /**
    * Chains first this transformer, and then another one. More efficient than
    * the base andThen function.
    */
  def andThen[O2[_]](that: CommandTransformer[F, O, O2]): CommandTransformer[F, I, O2] =
    new CommandTransformer[F, I, O2] {
      override def flowMapper[A]: Flow[I[A], O2[A], NotUsed] = self.flowMapper.via(that.flowMapper)
    }
}
object CommandTransformer {

  /**
    * Converts a [[cats.arrow.FunctionK]] to an [[CommandTransformer]].
    */
  def fromFuncK[F[_], I[_], O[_]](f: I ~> O): CommandTransformer[F, I, O] = new CommandTransformer[F, I, O] {
    override def flowMapper[A]: Flow[I[A], O[A], NotUsed] = Flow[I[A]].map(f(_))
  }
}

/**
  * A [[CommandFunction]] from a command message to an output. Used for
  * creating commands.
  * @tparam F The cache effect type
  * @tparam M The command message type used by the command.
  * @tparam A The argument type of this command builder.
  */
trait CommandBuilder[F[_], +M[_], A] extends CommandFunction[F, CommandMessage[F, ?], M] { self =>

  /**
    * A request helper that belongs to this builder.
    */
  def requests: RequestHelper

  /**
    * The parser used for parsing the arguments this command takes.
    */
  def parser: MessageParser[A]

  /**
    * Converts this builder into a builder that will create [[NamedCommand]].
    * These don't need to be provided a name when registering them.
    * @param namedSymbol The symbol to use when invoking the command
    * @param namedAliases The valid aliases to use when invoking the command
    * @param mustMention If the command requires a mention
    */
  def named(
      namedSymbol: String,
      namedAliases: Seq[String],
      mustMention: Boolean
  ): NamedCommandBuilder[F, M, A] =
    new NamedCommandBuilder[F, M, A] {
      override def symbol: String = namedSymbol

      override def aliases: Seq[String] = namedAliases

      override def requiresMention: Boolean = mustMention

      override def requests: RequestHelper = self.requests

      override def parser: MessageParser[A] = self.parser

      override def flow[B]: Flow[CommandMessage[F, B], Either[Option[CommandError[F]], M[B]], NotUsed] = self.flow[B]
    }

  /**
    * Creates a new command builder parsing a specific type.
    * @tparam B The type to parse
    */
  def parsing[B](implicit newParser: MessageParser[B]): CommandBuilder[F, M, B] = new CommandBuilder[F, M, B] {
    override def requests: RequestHelper = self.requests

    override def parser: MessageParser[B] = newParser

    override def flow[C]: Flow[CommandMessage[F, C], Either[Option[CommandError[F]], M[C]], NotUsed] = self.flow
  }

  /**
    * Creates a command from a sink.
    * @param sinkBlock The sink that will process this command.
    * @tparam Mat The materialized result of running this command.
    */
  def streamed[Mat](sinkBlock: Sink[M[A], Mat]): Command[F, A, Mat] = new Command[F, A, Mat] {
    override def parser: MessageParser[A] = self.parser

    override def flow: Flow[CommandMessage[F, A], CommandError[F], Mat] = {
      Flow.fromGraph(GraphDSL.create(sinkBlock) { implicit b => block =>
        import GraphDSL.Implicits._
        val selfFlow = b.add(self.flow[A])

        val selfPartition = b.add(Partition[Either[Option[CommandError[F]], M[A]]](2, {
          case Left(_)  => 0
          case Right(_) => 1
        }))
        val selfErr = selfPartition.out(0).map(_.left.get).mapConcat(_.toList)
        val selfOut = selfPartition.out(1).map(_.right.get)

        selfFlow ~> selfPartition
        selfOut ~> block

        FlowShape(
          selfFlow.in,
          selfErr.outlet
        )
      })
    }
  }

  /**
    * Creates a command that results in some streamable type G
    * @param block The execution of the command.
    * @tparam G The streamable result type.
    */
  def async[G[_]](block: M[A] => G[Unit])(implicit streamable: Streamable[G]): Command[F, A, NotUsed] =
    streamed(Flow[M[A]].flatMapConcat(m => streamable.toSource(block(m))).to(Sink.ignore))

  /**
    * Creates a command that might do a single request, wrapped in an effect type G
    * @param block The execution of the command.
    * @tparam G The streamable result type.
    */
  def asyncOptRequest[G[_]](
      block: M[A] => OptionT[G, Request[Any, Any]]
  )(implicit streamable: Streamable[G]): Command[F, A, NotUsed] =
    streamed(Flow[M[A]].flatMapConcat(m => streamable.optionToSource(block(m))).to(requests.sinkIgnore))

  /**
    * Creates a command that will do a single request
    * @param block The execution of the command.
    */
  def withRequest(block: M[A] => Request[Any, Any]): Command[F, A, NotUsed] =
    streamed(Flow[M[A]].map(block).to(requests.sinkIgnore))

  /**
    * Creates a command that might do a single request
    * @param block The execution of the command.
    */
  def withRequestOpt(block: M[A] => Option[Request[Any, Any]]): Command[F, A, NotUsed] =
    streamed(Flow[M[A]].mapConcat(block(_).toList).to(requests.sinkIgnore))

  /**
    * Creates a command that might execute unknown side effects.
    * @param block The execution of the command.
    */
  def withSideEffects(block: M[A] => Unit): Command[F, A, NotUsed] =
    streamed(Sink.foreach(block).mapMaterializedValue(_ => NotUsed))

  override def andThen[M2[_]](f: CommandFunction[F, M, M2]): CommandBuilder[F, M2, A] = new CommandBuilder[F, M2, A] {
    override def requests: RequestHelper = self.requests

    override def parser: MessageParser[A] = self.parser

    override def flow[C]: Flow[CommandMessage[F, C], Either[Option[CommandError[F]], M2[C]], NotUsed] =
      CommandFunction.flowViaEither(self.flow[C], f.flow[C])(Keep.right)
  }
}
object CommandBuilder {

  /**
    * A command function that only allows commands sent from a guild, and that
    * lets you build the result command message.
    */
  def onlyInGuild[F[_]: Streamable: Applicative, I[A] <: CommandMessage[F, A], O[_]](
      create: (TGuildChannel, Guild) => I ~> O
  ): CommandFunction[F, I, O] =
    new CommandFunction[F, I, O] {

      type Result[A] = Either[Option[CommandError[F]], O[A]]

      override def flow[A]: Flow[I[A], Result[A], NotUsed] =
        Flow[I[A]].flatMapConcat { m =>
          implicit val c: CacheSnapshot[F] = m.cache

          lazy val e: Result[A] = Left(Some(CommandError.mk(s"This command can only be used in a guild", m)))

          val res = m.tChannel match {
            case chG: TGuildChannel =>
              chG.guild.fold[Result[A]](e) { g =>
                Right(create(chG, g)(m))
              }
            case _ => Applicative[F].pure(e)
          }

          Streamable[F].toSource(res)
        }
    }

  /**
    * A command function that lets you add the guild member to a command message.
    */
  def withGuildMember[F[_]: Streamable: Applicative, I[A] <: GuildCommandMessage[F, A] with UserCommandMessage[F, A], O[
      _
  ]](create: GuildMember => I ~> O): CommandTransformer[F, I, O] = new CommandTransformer[F, I, O] {
    override def flowMapper[A]: Flow[I[A], O[A], NotUsed] =
      Flow[I[A]].mapConcat { m =>
        m.guild.members.get(m.user.id).map(member => create(member)(m)).toList
      }
  }

  def inVoiceChannel[F[_]: Streamable: Monad, I[A] <: GuildCommandMessage[F, A] with UserCommandMessage[F, A], O[_]](
      create: VGuildChannel => I ~> O
  ): CommandFunction[F, I, O] = new CommandFunction[F, I, O] {
    type Result[A] = Either[Option[CommandError[F]], O[A]]

    override def flow[A]: Flow[I[A], Result[A], NotUsed] = Flow[I[A]].flatMapConcat { m =>
      implicit val c: CacheSnapshot[F] = m.cache
      val res: F[Result[A]] = OptionT
        .fromOption[F](m.guild.voiceStates.get(m.user.id).flatMap(_.channelId))
        .flatMap(_.vResolve(m.guild.id))
        .toRight(Some(CommandError.mk(s"This command can only be used in a guild", m)): Option[CommandError[F]])
        .map(vCh => create(vCh)(m))
        .value

      Streamable[F].toSource(res)
    }
  }

  /**
    * A command function that only allow commands sent from one specific guild.
    */
  def inOneGuild[F[_], M[A] <: GuildCommandMessage[F, A]](
      guildId: GuildId
  ): CommandFunction[F, M, M] =
    new CommandFunction[F, M, M] {
      override def flow[A]: Flow[M[A], Either[Option[CommandError[F]], M[A]], NotUsed] =
        Flow[M[A]].map(m => Either.cond(m.guild.id == guildId, m, None))
    }

  /**
    * A command function that requires that those who use this command need
    * some set of permissions.
    */
  def needPermission[F[_], M[A] <: GuildCommandMessage[F, A]](
      neededPermission: Permission
  ): CommandFunction[F, M, M] = new CommandFunction[F, M, M] {
    override def flow[A]: Flow[M[A], Either[Option[CommandError[F]], M[A]], NotUsed] =
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
  def nonBot[F[_]: Streamable: Monad, I[A] <: CommandMessage[F, A], O[_]](
      create: User => I ~> O
  ): CommandFunction[F, I, O] =
    new CommandFunction[F, I, O] {
      override def flow[A]: Flow[I[A], Either[Option[CommandError[F]], O[A]], NotUsed] =
        Flow[I[A]].flatMapConcat { m =>
          implicit val c: CacheSnapshot[F] = m.cache
          val res = m.message.authorUser
            .filter(!_.bot.getOrElse(false))
            .toRight(None)
            .map(u => create(u)(m))
            .value
          Streamable[F].toSource(res)
        }
    }

  /**
    * Creates a raw command builder without any extra processing.
    */
  def rawBuilder[F[_]](requestHelper: RequestHelper): CommandBuilder[F, CommandMessage[F, ?], List[String]] =
    new CommandBuilder[F, CommandMessage[F, ?], List[String]] {
      override def requests: RequestHelper = requestHelper

      override def parser: MessageParser[List[String]] = MessageParser.allStringsParser

      override def flow[A]: Flow[CommandMessage[F, A], Either[Option[CommandError[F]], CommandMessage[F, A]], NotUsed] =
        Flow[CommandMessage[F, A]].map(Right.apply)
    }
}

/**
  * A [[CommandFunction]] from a command message to an output. Used for
  * creating commands.
  * @tparam F The cache effect type
  * @tparam M The command message type used by the command.
  * @tparam A The argument type of this command builder.
  */
trait NamedCommandBuilder[F[_], +M[_], A] extends CommandBuilder[F, M, A] { self =>

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
    * Creates a new command builder parsing a specific type.
    * @tparam B The type to parse
    */
  override def parsing[B](implicit newParser: MessageParser[B]): NamedCommandBuilder[F, M, B] =
    new NamedCommandBuilder[F, M, B] {

      override def symbol: String = self.symbol

      override def aliases: Seq[String] = self.aliases

      override def requiresMention: Boolean = self.requiresMention

      override def requests: RequestHelper = self.requests

      override def parser: MessageParser[B] = newParser

      override def flow[C]: Flow[CommandMessage[F, C], Either[Option[CommandError[F]], M[C]], NotUsed] = self.flow
    }

  override def streamed[Mat](sinkBlock: Sink[M[A], Mat]): NamedCommand[F, A, Mat] = new NamedCommand[F, A, Mat] {
    override def symbol: String = self.symbol

    override def aliases: Seq[String] = self.aliases

    override def requiresMention: Boolean = self.requiresMention

    override def parser: MessageParser[A] = self.parser

    override def flow: Flow[CommandMessage[F, A], CommandError[F], Mat] = {
      Flow.fromGraph(GraphDSL.create(sinkBlock) { implicit b => block =>
        import GraphDSL.Implicits._
        val selfFlow = b.add(self.flow[A])

        val selfPartition = b.add(Partition[Either[Option[CommandError[F]], M[A]]](2, {
          case Left(_)  => 0
          case Right(_) => 1
        }))
        val selfErr = selfPartition.out(0).map(_.left.get).mapConcat(_.toList)
        val selfOut = selfPartition.out(1).map(_.right.get)

        selfFlow ~> selfPartition
        selfOut ~> block

        FlowShape(
          selfFlow.in,
          selfErr.outlet
        )
      })
    }
  }

  override def async[G[_]](block: M[A] => G[Unit])(implicit streamable: Streamable[G]): NamedCommand[F, A, NotUsed] =
    streamed(Flow[M[A]].flatMapConcat(m => streamable.toSource(block(m))).to(Sink.ignore))

  override def asyncOptRequest[G[_]](
      block: M[A] => OptionT[G, Request[Any, Any]]
  )(implicit streamable: Streamable[G]): NamedCommand[F, A, NotUsed] =
    streamed(Flow[M[A]].flatMapConcat(m => streamable.optionToSource(block(m))).to(requests.sinkIgnore))

  override def withRequest(block: M[A] => Request[Any, Any]): NamedCommand[F, A, NotUsed] =
    streamed(Flow[M[A]].map(block).to(requests.sinkIgnore))

  override def withRequestOpt(block: M[A] => Option[Request[Any, Any]]): NamedCommand[F, A, NotUsed] =
    streamed(Flow[M[A]].mapConcat(block(_).toList).to(requests.sinkIgnore))

  override def withSideEffects(block: M[A] => Unit): NamedCommand[F, A, NotUsed] =
    streamed(Sink.foreach(block).mapMaterializedValue(_ => NotUsed))

  override def andThen[M2[_]](f: CommandFunction[F, M, M2]): NamedCommandBuilder[F, M2, A] =
    new NamedCommandBuilder[F, M2, A] {

      override def symbol: String = self.symbol

      override def aliases: Seq[String] = self.aliases

      override def requiresMention: Boolean = self.requiresMention

      override def requests: RequestHelper = self.requests

      override def parser: MessageParser[A] = self.parser

      override def flow[C]: Flow[CommandMessage[F, C], Either[Option[CommandError[F]], M2[C]], NotUsed] =
        CommandFunction.flowViaEither(self.flow[C], f.flow[C])(Keep.right)
    }
}

/**
  * A message sent with an invocation of a command.
  * @tparam F The cache effect type
  * @tparam A The parsed argument type
  */
trait CommandMessage[F[_], +A] {

  /**
    * Easy access to a request helper.
    */
  def requests: RequestHelper

  /**
    * A cache snapshot taken when the command was used.
    */
  def cache: CacheSnapshot[F]

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

  implicit def findCache[F[_], A](implicit message: CommandMessage[F, A]): CacheSnapshot[F] = message.cache

  case class Default[F[_], A](
      requests: RequestHelper,
      cache: CacheSnapshot[F],
      tChannel: TChannel,
      message: Message,
      parsed: A
  ) extends CommandMessage[F, A]
}

class WrappedCommandMessage[F[_], A](m: CommandMessage[F, A]) extends CommandMessage[F, A] {
  override def requests: RequestHelper = m.requests

  override def cache: CacheSnapshot[F] = m.cache

  override def tChannel: TChannel = m.tChannel

  override def message: Message = m.message

  override def parsed: A = m.parsed
}

/**
  * A message sent with the invocation of a guild command
  * @tparam F The cache effect type
  * @tparam A The parsed argument type
  */
trait GuildCommandMessage[F[_], +A] extends CommandMessage[F, A] {
  override def tChannel: TGuildChannel

  /**
    * The guild this command was used in.
    */
  def guild: Guild
}
object GuildCommandMessage {

  case class Default[F[_], A](
      override val tChannel: TGuildChannel,
      guild: Guild,
      m: CommandMessage[F, A]
  ) extends WrappedCommandMessage(m)
      with GuildCommandMessage[F, A]

  case class WithUser[F[_], A](
      override val tChannel: TGuildChannel,
      guild: Guild,
      user: User,
      m: CommandMessage[F, A]
  ) extends WrappedCommandMessage(m)
      with GuildCommandMessage[F, A]
      with UserCommandMessage[F, A]
}

/**
  * A message sent with the invocation of command used by a user
  * @tparam F The cache effect type
  * @tparam A The parsed argument type
  */
trait UserCommandMessage[F[_], +A] extends CommandMessage[F, A] {

  /**
    * The user that used this command.
    */
  def user: User
}
object UserCommandMessage {

  case class Default[F[_], A](user: User, m: CommandMessage[F, A])
      extends WrappedCommandMessage(m)
      with UserCommandMessage[F, A]
}

trait GuildMemberCommandMessage[F[_], +A] extends GuildCommandMessage[F, A] with UserCommandMessage[F, A] {

  /**
    * The guild member that used this command.
    */
  def guildMember: GuildMember
}
object GuildMemberCommandMessage {

  case class Default[F[_], A](
      override val tChannel: TGuildChannel,
      guild: Guild,
      user: User,
      guildMember: GuildMember,
      m: CommandMessage[F, A]
  ) extends WrappedCommandMessage(m)
      with GuildMemberCommandMessage[F, A]
}

trait VoiceGuildCommandMessage[F[_], +A] extends GuildCommandMessage[F, A] with UserCommandMessage[F, A] {

  /**
    * The voice channel the user that used this command is in.
    */
  def voiceChannel: VGuildChannel
}
object VoiceGuildCommandMessage {

  case class Default[F[_], A](
      override val tChannel: TGuildChannel,
      guild: Guild,
      user: User,
      voiceChannel: VGuildChannel,
      m: CommandMessage[F, A]
  ) extends WrappedCommandMessage(m)
      with VoiceGuildCommandMessage[F, A]

  case class WithGuildMember[F[_], A](
      override val tChannel: TGuildChannel,
      guild: Guild,
      user: User,
      guildMember: GuildMember,
      voiceChannel: VGuildChannel,
      m: CommandMessage[F, A]
  ) extends WrappedCommandMessage(m)
      with VoiceGuildCommandMessage[F, A]
      with GuildMemberCommandMessage[F, A]
}

/**
  * Represents an error encountered when executing an command.
  * @param error The errror message
  * @param channel The channel the error occoured in
  * @param cache A cache snapshot tied to the execution of the command
  * @tparam F The cache's effect type
  */
case class CommandError[F[_]](error: String, channel: TChannel, cache: CacheSnapshot[F])
object CommandError {
  def mk[F[_], A](error: String, message: CommandMessage[F, A]): CommandError[F] =
    CommandError(error, message.tChannel, message.cache)
}

/**
  * A constructed command execution.
  * @tparam F The effect type of the cache
  * @tparam A The argument type of the command
  * @tparam Mat The materialized result of creating this command
  */
trait Command[F[_], A, Mat] {

  def parser: MessageParser[A]

  def flow: Flow[CommandMessage[F, A], CommandError[F], Mat]
}
