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
import akka.stream.scaladsl.{Flow, GraphDSL, Partition, Sink}
import cats.~>

/**
  * A [[CommandFunction]] from a command message to an output. Used for
  * creating commands.
  * @param defaultMustMention Set the default value for must mention when
  *                           creating a named command.
  * @param defaultMentionOrPrefix Set the default value for mention or prefix
  *                               when creating a named command.
  * @param parser The parser used for parsing the arguments this command takes.
  * @tparam M The command message type used by the command.
  * @tparam A The argument type of this command builder.
  */
case class CommandBuilder[+M[_], A](
    requests: Requests,
    defaultMustMention: Boolean,
    defaultMentionOrPrefix: Boolean,
    parser: MessageParser[A],
    actionFunction: ActionFunction[CommandMessage, M, CommandError]
) extends ActionBuilder[CommandMessage, M, CommandError, A] { self =>

  override type Action[B, Mat] = ComplexCommand[B, Mat]

  /** A flow that represents this mapping. */
  override def flow[C]: Flow[CommandMessage[C], Either[Option[CommandError], M[C]], NotUsed] = actionFunction.flow[C]

  /**
    * Converts this builder into a builder that will create [[NamedComplexCommand]].
    * These don't need to be provided a name when registering them.
    *
    * @param structuredPrefixParser The structured prefix parser to use as a name for
    *                               commands created by this builder.
    */
  def namedParser(structuredPrefixParser: StructuredPrefixParser): NamedCommandBuilder[M, A] =
    new NamedCommandBuilder(this, structuredPrefixParser)

  /**
    * Converts this builder into a builder that will create [[NamedComplexCommand]].
    * These don't need to be provided a name when registering them.
    *
    * @param namedSymbols The symbols to use when invoking the command
    * @param namedAliases The valid aliases to use when invoking the command
    * @param mustMention If the command requires a mention
    * @param aliasesCaseSensitive If the command aliases should be matched with case sensitivity
    * @param mentionOrPrefix If true allows one to use a mention in place of a prefix.
    *                        If needsMention is also true, skips the symbol check.
    */
  def named(
      namedSymbols: Seq[String],
      namedAliases: Seq[String],
      mustMention: Boolean = defaultMustMention,
      aliasesCaseSensitive: Boolean = false,
      mentionOrPrefix: Boolean = defaultMentionOrPrefix
  ): NamedCommandBuilder[M, A] =
    namedParser(
      PrefixParser.structured(
        mustMention,
        namedSymbols,
        namedAliases,
        aliasesCaseSensitive,
        mentionOrPrefix
      )
    )

  /**
    * Converts this builder into a builder that will create [[NamedComplexCommand]].
    * These don't need to be provided a name when registering them.
    *
    * @param namedSymbols The symbols to use when invoking the command
    * @param namedAliases The valid aliases to use when invoking the command
    * @param mustMention If the command requires a mention
    * @param aliasesCaseSensitive If the command aliases should be matched with case sensitivity
    * @param canExecute A early precheck if the command can execute at all
    * @param mentionOrPrefix If true allows one to use a mention in place of a prefix.
    *                        If needsMention is also true, skips the symbol check.
    */
  def namedFunction(
      namedSymbols: (CacheSnapshot, Message) => Seq[String],
      namedAliases: (CacheSnapshot, Message) => Seq[String],
      mustMention: (CacheSnapshot, Message) => Boolean = (_, _) => defaultMustMention,
      aliasesCaseSensitive: (CacheSnapshot, Message) => Boolean = (_, _) => false,
      canExecute: (CacheSnapshot, Message) => Boolean = (_, _) => true,
      mentionOrPrefix: (CacheSnapshot, Message) => Boolean = (_, _) => defaultMentionOrPrefix
  ): NamedCommandBuilder[M, A] = namedParser(
    PrefixParser.structuredFunction(
      mustMention,
      namedSymbols,
      namedAliases,
      aliasesCaseSensitive,
      canExecute,
      mentionOrPrefix
    )
  )

  /**
    * Converts this builder into a builder that will create [[NamedComplexCommand]].
    * These don't need to be provided a name when registering them.
    *
    * @param namedSymbols The symbols to use when invoking the command
    * @param namedAliases The valid aliases to use when invoking the command
    * @param mustMention If the command requires a mention
    * @param aliasesCaseSensitive If the command aliases should be matched with case sensitivity
    * @param canExecute A early precheck if the command can execute at all
    * @param mentionOrPrefix If true allows one to use a mention in place of a prefix.
    *                        If needsMention is also true, skips the symbol check.
    */
  def namedAsync(
      namedSymbols: (CacheSnapshot, Message) => Future[Seq[String]],
      namedAliases: (CacheSnapshot, Message) => Future[Seq[String]],
      mustMention: (CacheSnapshot, Message) => Future[Boolean] = (_, _) => Future.successful(defaultMustMention),
      aliasesCaseSensitive: (CacheSnapshot, Message) => Future[Boolean] = (_, _) => Future.successful(false),
      canExecute: (CacheSnapshot, Message) => Future[Boolean] = (_, _) => Future.successful(true),
      mentionOrPrefix: (CacheSnapshot, Message) => Future[Boolean] = (_, _) => Future.successful(defaultMentionOrPrefix)
  ): NamedCommandBuilder[M, A] = namedParser(
    PrefixParser.structuredAsync(
      mustMention,
      namedSymbols,
      namedAliases,
      aliasesCaseSensitive,
      canExecute,
      mentionOrPrefix
    )
  )

  /**
    * Creates a new command builder parsing a specific type.
    * @tparam B The type to parse
    */
  def parsing[B](implicit newParser: MessageParser[B]): CommandBuilder[M, B] =
    copy(parser = newParser)

  /**
    * Creates a command from a sink.
    * @param sinkBlock The sink that will process this command.
    * @tparam Mat The materialized result of running this command.
    */
  def toSink[Mat](sinkBlock: Sink[M[A], Mat]): ComplexCommand[A, Mat] =
    new ComplexCommand[A, Mat](self.parser, CommandBuilder.streamedFlow(sinkBlock, self.flow[A]))

  override def andThen[M2[_]](f: CommandFunction[M, M2]): CommandBuilder[M2, A] =
    copy(actionFunction = actionFunction.andThen(f))
}
object CommandBuilder {

  /**
    * A command function that only allows commands sent from a guild, and that
    * lets you build the result command message.
    */
  def onlyInGuild[I[A] <: CommandMessage[A], O[_]](
      create: (TextGuildChannel, GuildGatewayMessage, GatewayGuild) => I ~> O
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

  /** A command function that lets you add the guild member to a command message. */
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

  /** A command function that only allow commands sent from one specific guild. */
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

  /** A command function that disallows bots from using it. */
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

  /** Creates a raw command builder without any extra processing. */
  def rawBuilder(
      requests: Requests,
      defaultMustMention: Boolean,
      defaultMentionOrPrefix: Boolean
  ): CommandBuilder[CommandMessage, NotUsed] =
    new CommandBuilder[CommandMessage, NotUsed](
      requests,
      defaultMustMention,
      defaultMentionOrPrefix,
      MessageParser.notUsedParser,
      ActionFunction.identity
    )

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
  * @param defaultMustMention Set the default value for must mention when
  *                           creating a named command.
  * @param defaultMentionOrPrefix Set the default value for mention or prefix
  *                               when creating a named command.
  * @param prefixParser The prefix parser to use for commands created from this builder.
  * @param parser The parser used for parsing the arguments this command takes.
  * @tparam M The command message type used by the command.
  * @tparam A The argument type of this command builder.
  */
case class NamedCommandBuilder[+M[_], A](
    requests: Requests,
    defaultMustMention: Boolean,
    defaultMentionOrPrefix: Boolean,
    prefixParser: StructuredPrefixParser,
    parser: MessageParser[A],
    actionFunction: ActionFunction[CommandMessage, M, CommandError]
) extends ActionBuilder[CommandMessage, M, CommandError, A] {

  def this(builder: CommandBuilder[M, A], prefixParser: StructuredPrefixParser) =
    this(
      builder.requests,
      builder.defaultMustMention,
      builder.defaultMentionOrPrefix,
      prefixParser,
      builder.parser,
      builder.actionFunction
    )

  override type Action[B, Mat] = NamedComplexCommand[B, Mat]

  /** A flow that represents this mapping. */
  override def flow[C]: Flow[CommandMessage[C], Either[Option[CommandError], M[C]], NotUsed] = actionFunction.flow[C]

  /** Adds a description to this builder */
  def described(description: CommandDescription): NamedDescribedCommandBuilder[M, A] =
    new NamedDescribedCommandBuilder[M, A](this, description)

  /** Adds a description to this builder */
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
    copy(parser = newParser)

  def toSink[Mat](sinkBlock: Sink[M[A], Mat]): NamedComplexCommand[A, Mat] = NamedComplexCommand(
    ComplexCommand(parser, CommandBuilder.streamedFlow(sinkBlock, flow[A])),
    prefixParser
  )

  override def andThen[M2[_]](f: CommandFunction[M, M2]): NamedCommandBuilder[M2, A] =
    copy(actionFunction = actionFunction.andThen(f))
}

/**
  * A [[CommandFunction]] from a command message to an output. Used for
  * creating commands.
  * @param defaultMustMention Set the default value for must mention when
  *                           creating a named command.
  * @param defaultMentionOrPrefix Set the default value for mention or prefix
  *                               when creating a named command.
  * @param prefixParser The prefix parser to use for commands created from this builder.
  * @param description The description to use for commands created from this builder.
  * @param parser The parser used for parsing the arguments this command takes.
  * @tparam M The command message type used by the command.
  * @tparam A The argument type of this command builder.
  */
case class NamedDescribedCommandBuilder[+M[_], A](
    requests: Requests,
    defaultMustMention: Boolean,
    defaultMentionOrPrefix: Boolean,
    prefixParser: StructuredPrefixParser,
    description: CommandDescription,
    parser: MessageParser[A],
    actionFunction: ActionFunction[CommandMessage, M, CommandError]
) extends ActionBuilder[CommandMessage, M, CommandError, A] {

  def this(builder: NamedCommandBuilder[M, A], description: CommandDescription) =
    this(
      builder.requests,
      builder.defaultMustMention,
      builder.defaultMentionOrPrefix,
      builder.prefixParser,
      description,
      builder.parser,
      builder.actionFunction
    )

  override type Action[B, Mat] = NamedDescribedComplexCommand[B, Mat]

  /** A flow that represents this mapping. */
  override def flow[C]: Flow[CommandMessage[C], Either[Option[CommandError], M[C]], NotUsed] = actionFunction.flow[C]

  /**
    * Creates a new command builder parsing a specific type.
    * @tparam B The type to parse
    */
  def parsing[B](implicit newParser: MessageParser[B]): NamedDescribedCommandBuilder[M, B] =
    copy(parser = newParser)

  def toSink[Mat](sinkBlock: Sink[M[A], Mat]): NamedDescribedComplexCommand[A, Mat] = NamedDescribedComplexCommand(
    ComplexCommand(parser, CommandBuilder.streamedFlow(sinkBlock, flow[A])),
    prefixParser,
    description
  )

  override def andThen[M2[_]](f: CommandFunction[M, M2]): NamedDescribedCommandBuilder[M2, A] =
    copy(actionFunction = actionFunction.andThen(f))
}

/**
  * A message sent with an invocation of a command.
  * @tparam A The parsed argument type
  */
trait CommandMessage[+A] {

  /** A cache snapshot taken when the command was used. */
  def cache: CacheSnapshot

  /** The channel the command was used from. */
  def textChannel: TextChannel

  /** The message that invoked the command. */
  def message: Message

  /** The parsed arguments of this command. */
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

  /** The guild this command was used in. */
  def guild: GatewayGuild

  override def message: GuildGatewayMessage
}
object GuildCommandMessage {

  case class Default[A](
      override val textChannel: TextGuildChannel,
      override val message: GuildGatewayMessage,
      guild: GatewayGuild,
      m: CommandMessage[A]
  ) extends WrappedCommandMessage(m)
      with GuildCommandMessage[A]

  case class WithUser[A](
      override val textChannel: TextGuildChannel,
      override val message: GuildGatewayMessage,
      guild: GatewayGuild,
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

  /** The user that used this command. */
  def user: User
}
object UserCommandMessage {

  case class Default[A](user: User, m: CommandMessage[A]) extends WrappedCommandMessage(m) with UserCommandMessage[A]
}

trait GuildMemberCommandMessage[+A] extends GuildCommandMessage[A] with UserCommandMessage[A] {

  /** The guild member that used this command. */
  def guildMember: GuildMember
}
object GuildMemberCommandMessage {

  case class Default[A](
      override val textChannel: TextGuildChannel,
      override val message: GuildGatewayMessage,
      guild: GatewayGuild,
      user: User,
      guildMember: GuildMember,
      m: CommandMessage[A]
  ) extends WrappedCommandMessage(m)
      with GuildMemberCommandMessage[A]
}

trait VoiceGuildCommandMessage[+A] extends GuildCommandMessage[A] with UserCommandMessage[A] {

  /** The voice channel the user that used this command is in. */
  def voiceChannel: VoiceGuildChannel
}
object VoiceGuildCommandMessage {

  case class Default[A](
      override val textChannel: TextGuildChannel,
      override val message: GuildGatewayMessage,
      guild: GatewayGuild,
      user: User,
      voiceChannel: VoiceGuildChannel,
      m: CommandMessage[A]
  ) extends WrappedCommandMessage(m)
      with VoiceGuildCommandMessage[A]

  case class WithGuildMember[A](
      override val textChannel: TextGuildChannel,
      override val message: GuildGatewayMessage,
      guild: GatewayGuild,
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
