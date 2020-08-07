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
package ackcord.commands

import scala.concurrent.Future

import ackcord.CacheSnapshot
import ackcord.data._
import ackcord.requests.Requests
import akka.NotUsed
import akka.stream.FlowShape
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, Partition, Sink}
import cats.~>

/**
  * A [[CommandFunction]] from a command message to an output. Used for
  * creating commands.
  * @tparam M The command message type used by the command.
  * @tparam A The argument type of this command builder.
  */
trait CommandBuilder[+M[_], A] extends ActionBuilder[CommandMessage, M, CommandError, A] { self =>

  override type Action[B, Mat] = ComplexCommand[B, Mat]

  /**
    * Set the default value for must mention when creating a named command.
    */
  val defaultMustMention: Boolean

  /**
    * The parser used for parsing the arguments this command takes.
    */
  def parser: MessageParser[A]

  /**
    * Converts this builder into a builder that will create [[NamedComplexCommand]].
    * These don't need to be provided a name when registering them.
    *
    * @param structuredPrefixParser The structured prefix parser to use as a name for
    *                               commands created by this builder.
    */
  def namedParser(structuredPrefixParser: StructuredPrefixParser): NamedCommandBuilder[M, A] =
    new NamedCommandBuilder[M, A] {
      override val defaultMustMention: Boolean = self.defaultMustMention

      override def prefixParser: StructuredPrefixParser = structuredPrefixParser

      override def requests: Requests = self.requests

      override def parser: MessageParser[A] = self.parser

      override def flow[B]: Flow[CommandMessage[B], Either[Option[CommandError], M[B]], NotUsed] = self.flow[B]
    }

  /**
    * Converts this builder into a builder that will create [[NamedComplexCommand]].
    * These don't need to be provided a name when registering them.
    *
    * @param namedSymbols The symbols to use when invoking the command
    * @param namedAliases The valid aliases to use when invoking the command
    * @param mustMention If the command requires a mention
    * @param aliasesCaseSensitive If the command aliases should be matched with case sensitivity
    */
  def named(
      namedSymbols: Seq[String],
      namedAliases: Seq[String],
      mustMention: Boolean = defaultMustMention,
      aliasesCaseSensitive: Boolean = false
  ): NamedCommandBuilder[M, A] = namedParser(
    PrefixParser.structured(mustMention, namedSymbols, namedAliases, aliasesCaseSensitive)
  )

  /**
    * Converts this builder into a builder that will create [[NamedComplexCommand]].
    * These don't need to be provided a name when registering them.
    *
    * @param namedSymbols The symbols to use when invoking the command
    * @param namedAliases The valid aliases to use when invoking the command
    * @param mustMention If the command requires a mention
    * @param aliasesCaseSensitive If the command aliases should be matched with case sensitivity
    */
  def namedFunction(
      namedSymbols: (CacheSnapshot, Message) => Seq[String],
      namedAliases: (CacheSnapshot, Message) => Seq[String],
      mustMention: (CacheSnapshot, Message) => Boolean = (_, _) => defaultMustMention,
      aliasesCaseSensitive: (CacheSnapshot, Message) => Boolean = (_, _) => false
  ): NamedCommandBuilder[M, A] = namedParser(
    PrefixParser.structuredFunction(mustMention, namedSymbols, namedAliases, aliasesCaseSensitive)
  )

  /**
    * Converts this builder into a builder that will create [[NamedComplexCommand]].
    * These don't need to be provided a name when registering them.
    *
    * @param namedSymbols The symbols to use when invoking the command
    * @param namedAliases The valid aliases to use when invoking the command
    * @param mustMention If the command requires a mention
    * @param aliasesCaseSensitive If the command aliases should be matched with case sensitivity
    */
  def namedAsync(
      namedSymbols: (CacheSnapshot, Message) => Future[Seq[String]],
      namedAliases: (CacheSnapshot, Message) => Future[Seq[String]],
      mustMention: (CacheSnapshot, Message) => Future[Boolean] = (_, _) => Future.successful(defaultMustMention),
      aliasesCaseSensitive: (CacheSnapshot, Message) => Future[Boolean] = (_, _) => Future.successful(false)
  ): NamedCommandBuilder[M, A] = namedParser(
    PrefixParser.structuredAsync(mustMention, namedSymbols, namedAliases, aliasesCaseSensitive)
  )

  /**
    * Creates a new command builder parsing a specific type.
    * @tparam B The type to parse
    */
  def parsing[B](implicit newParser: MessageParser[B]): CommandBuilder[M, B] = new CommandBuilder[M, B] {
    override val defaultMustMention: Boolean = self.defaultMustMention

    override def requests: Requests = self.requests

    override def parser: MessageParser[B] = newParser

    override def flow[C]: Flow[CommandMessage[C], Either[Option[CommandError], M[C]], NotUsed] = self.flow
  }

  /**
    * Creates a command from a sink.
    * @param sinkBlock The sink that will process this command.
    * @tparam Mat The materialized result of running this command.
    */
  def toSink[Mat](sinkBlock: Sink[M[A], Mat]): ComplexCommand[A, Mat] =
    new ComplexCommand[A, Mat](self.parser, CommandBuilder.streamedFlow(sinkBlock, self.flow[A]))

  override def andThen[M2[_]](f: CommandFunction[M, M2]): CommandBuilder[M2, A] = new CommandBuilder[M2, A] {
    override val defaultMustMention: Boolean = self.defaultMustMention

    override def requests: Requests = self.requests

    override def parser: MessageParser[A] = self.parser

    override def flow[C]: Flow[CommandMessage[C], Either[Option[CommandError], M2[C]], NotUsed] =
      ActionFunction.flowViaEither(self.flow[C], f.flow[C])(Keep.right)
  }
}
object CommandBuilder {

  /**
    * A command function that only allows commands sent from a guild, and that
    * lets you build the result command message.
    */
  def onlyInGuild[I[A] <: CommandMessage[A], O[_]](
      create: (TextGuildChannel, GuildGatewayMessage, Guild) => I ~> O
  ): CommandFunction[I, O] =
    new CommandFunction[I, O] {

      type Result[A] = Either[Option[CommandError], O[A]]

      override def flow[A]: Flow[I[A], Result[A], NotUsed] =
        Flow[I[A]].map { m =>
          implicit val c: CacheSnapshot = m.cache

          lazy val e: Result[A] = Left(Some(CommandError.mk(s"This command can only be used in a guild", m)))

          m.textChannel match {
            case chG: TextGuildChannel =>
              chG.guild.fold[Result[A]](e) { guild =>
                m.message match {
                  case guildMessage: GuildGatewayMessage => Right(create(chG, guildMessage, guild)(m))
                  case _                                 => e
                }
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
      Flow[I[A]].mapConcat(m => m.guild.members.get(m.user.id).map(member => create(member)(m)).toList)
  }

  def inVoiceChannel[I[A] <: GuildCommandMessage[A] with UserCommandMessage[A], O[_]](
      create: VoiceGuildChannel => I ~> O
  ): CommandFunction[I, O] = new CommandFunction[I, O] {
    type Result[A] = Either[Option[CommandError], O[A]]

    override def flow[A]: Flow[I[A], Result[A], NotUsed] = Flow[I[A]].map { m =>
      implicit val c: CacheSnapshot = m.cache
      m.guild.voiceStates
        .get(m.user.id)
        .flatMap(_.channelId)
        .flatMap(_.resolve(m.guild.id))
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
        else Left(Some(CommandError("You don't have permission to use this command", m.textChannel, m.cache)))
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
  def rawBuilder(requestHelper: Requests, defaultMustMentionVal: Boolean): CommandBuilder[CommandMessage, NotUsed] =
    new CommandBuilder[CommandMessage, NotUsed] {
      override val defaultMustMention: Boolean = defaultMustMentionVal

      override def requests: Requests = requestHelper

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

      val selfPartition = b.add(
        Partition[Either[Option[CommandError], M[A]]](
          2,
          {
            case Left(_)  => 0
            case Right(_) => 1
          }
        )
      )
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
trait NamedCommandBuilder[+M[_], A] extends ActionBuilder[CommandMessage, M, CommandError, A] { self =>

  override type Action[B, Mat] = NamedComplexCommand[B, Mat]

  /**
    * The prefix parser to use for commands created from this builder
    */
  def prefixParser: StructuredPrefixParser

  /**
    * Set the default value for must mention when creating a named command.
    */
  val defaultMustMention: Boolean

  /**
    * The parser used for parsing the arguments this command takes.
    */
  def parser: MessageParser[A]

  /**
    * Adds a description to this builder
    */
  def described(descriptionObj: CommandDescription): NamedDescribedCommandBuilder[M, A] =
    new NamedDescribedCommandBuilder[M, A] {

      override def prefixParser: StructuredPrefixParser = self.prefixParser

      override def description: CommandDescription = descriptionObj

      override val defaultMustMention: Boolean = self.defaultMustMention

      override def parser: MessageParser[A] = self.parser

      override def requests: Requests = self.requests

      override def flow[C]: Flow[CommandMessage[C], Either[Option[CommandError], M[C]], NotUsed] = self.flow
    }

  /**
    * Adds a description to this builder
    */
  def described(
      name: String,
      description: String,
      usage: String = "",
      extra: Map[String, String] = Map.empty
  ): NamedDescribedCommandBuilder[M, A] =
    described(CommandDescription(name, description, usage, extra))

  /**
    * Creates a new command builder parsing a specific type.
    * @tparam B The type to parse
    */
  def parsing[B](implicit newParser: MessageParser[B]): NamedCommandBuilder[M, B] =
    new NamedCommandBuilder[M, B] {
      override val defaultMustMention: Boolean = self.defaultMustMention

      override def prefixParser: StructuredPrefixParser = self.prefixParser

      override def requests: Requests = self.requests

      override def parser: MessageParser[B] = newParser

      override def flow[C]: Flow[CommandMessage[C], Either[Option[CommandError], M[C]], NotUsed] = self.flow
    }

  def toSink[Mat](sinkBlock: Sink[M[A], Mat]): NamedComplexCommand[A, Mat] = NamedComplexCommand(
    ComplexCommand(self.parser, CommandBuilder.streamedFlow(sinkBlock, self.flow[A])),
    self.prefixParser
  )

  override def andThen[M2[_]](f: CommandFunction[M, M2]): NamedCommandBuilder[M2, A] =
    new NamedCommandBuilder[M2, A] {
      override val defaultMustMention: Boolean = self.defaultMustMention

      override def prefixParser: StructuredPrefixParser = self.prefixParser

      override def requests: Requests = self.requests

      override def parser: MessageParser[A] = self.parser

      override def flow[C]: Flow[CommandMessage[C], Either[Option[CommandError], M2[C]], NotUsed] =
        ActionFunction.flowViaEither(self.flow[C], f.flow[C])(Keep.right)
    }
}

/**
  * A [[CommandFunction]] from a command message to an output. Used for
  * creating commands.
  * @tparam M The command message type used by the command.
  * @tparam A The argument type of this command builder.
  */
trait NamedDescribedCommandBuilder[+M[_], A] extends ActionBuilder[CommandMessage, M, CommandError, A] { self =>

  override type Action[B, Mat] = NamedDescribedComplexCommand[B, Mat]

  /**
    * The prefix parser to use for commands created from this builder
    */
  def prefixParser: StructuredPrefixParser

  /**
    * The description to use for commands created from this builder
    */
  def description: CommandDescription

  /**
    * Set the default value for must mention when creating a named command.
    */
  val defaultMustMention: Boolean

  /**
    * The parser used for parsing the arguments this command takes.
    */
  def parser: MessageParser[A]

  /**
    * Creates a new command builder parsing a specific type.
    * @tparam B The type to parse
    */
  def parsing[B](implicit newParser: MessageParser[B]): NamedDescribedCommandBuilder[M, B] =
    new NamedDescribedCommandBuilder[M, B] {
      override val defaultMustMention: Boolean = self.defaultMustMention

      override def prefixParser: StructuredPrefixParser = self.prefixParser

      override def description: CommandDescription = self.description

      override def requests: Requests = self.requests

      override def parser: MessageParser[B] = newParser

      override def flow[C]: Flow[CommandMessage[C], Either[Option[CommandError], M[C]], NotUsed] = self.flow
    }

  def toSink[Mat](sinkBlock: Sink[M[A], Mat]): NamedDescribedComplexCommand[A, Mat] = NamedDescribedComplexCommand(
    ComplexCommand(self.parser, CommandBuilder.streamedFlow(sinkBlock, self.flow[A])),
    self.prefixParser,
    self.description
  )

  override def andThen[M2[_]](f: CommandFunction[M, M2]): NamedDescribedCommandBuilder[M2, A] =
    new NamedDescribedCommandBuilder[M2, A] {
      override val defaultMustMention: Boolean = self.defaultMustMention

      override def prefixParser: StructuredPrefixParser = self.prefixParser

      override def description: CommandDescription = self.description

      override def requests: Requests = self.requests

      override def parser: MessageParser[A] = self.parser

      override def flow[C]: Flow[CommandMessage[C], Either[Option[CommandError], M2[C]], NotUsed] =
        ActionFunction.flowViaEither(self.flow[C], f.flow[C])(Keep.right)
    }
}

/**
  * A message sent with an invocation of a command.
  * @tparam A The parsed argument type
  */
trait CommandMessage[+A] {

  /**
    * A cache snapshot taken when the command was used.
    */
  def cache: CacheSnapshot

  /**
    * The channel the command was used from.
    */
  def textChannel: TextChannel

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
      requests: Requests,
      cache: CacheSnapshot,
      textChannel: TextChannel,
      message: Message,
      parsed: A
  ) extends CommandMessage[A]
}

class WrappedCommandMessage[A](m: CommandMessage[A]) extends CommandMessage[A] {
  override def cache: CacheSnapshot = m.cache

  override def textChannel: TextChannel = m.textChannel

  override def message: Message = m.message

  override def parsed: A = m.parsed
}

/**
  * A message sent with the invocation of a guild command
  * @tparam A The parsed argument type
  */
trait GuildCommandMessage[+A] extends CommandMessage[A] {
  override def textChannel: TextGuildChannel

  /**
    * The guild this command was used in.
    */
  def guild: Guild

  override def message: GuildGatewayMessage
}
object GuildCommandMessage {

  case class Default[A](
      override val textChannel: TextGuildChannel,
      override val message: GuildGatewayMessage,
      guild: Guild,
      m: CommandMessage[A]
  ) extends WrappedCommandMessage(m)
      with GuildCommandMessage[A]

  case class WithUser[A](
      override val textChannel: TextGuildChannel,
      override val message: GuildGatewayMessage,
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
      override val textChannel: TextGuildChannel,
      override val message: GuildGatewayMessage,
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
  def voiceChannel: VoiceGuildChannel
}
object VoiceGuildCommandMessage {

  case class Default[A](
      override val textChannel: TextGuildChannel,
      override val message: GuildGatewayMessage,
      guild: Guild,
      user: User,
      voiceChannel: VoiceGuildChannel,
      m: CommandMessage[A]
  ) extends WrappedCommandMessage(m)
      with VoiceGuildCommandMessage[A]

  case class WithGuildMember[A](
      override val textChannel: TextGuildChannel,
      override val message: GuildGatewayMessage,
      guild: Guild,
      user: User,
      guildMember: GuildMember,
      voiceChannel: VoiceGuildChannel,
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
case class CommandError(error: String, channel: TextChannel, cache: CacheSnapshot)
object CommandError {
  def mk[A](error: String, message: CommandMessage[A]): CommandError =
    CommandError(error, message.textChannel, message.cache)
}
