package ackcord.commands

import java.util.Locale

import scala.collection.mutable
import scala.concurrent.Future

import ackcord.CacheSnapshot
import ackcord.data.Message
import ackcord.data.raw.RawMessage
import ackcord.syntax._
import ackcord.requests.{CreateMessage, CreateMessageData, Request}
import akka.{Done, NotUsed}
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import cats.syntax.all._

abstract class HelpCmd[Command](ctx: ActorContext[Command])
    extends AbstractBehavior[Command](ctx) {
  import HelpCmd._
  import Args._

  val commands = mutable.HashSet.empty[CommandRegistration]

  protected def handler: Option[ActorRef[HandlerReply]] = None

  /**
    * If this help command should send an event to the handler when a command
    * is stopped.
    */
  protected def sendEndedEvent: Boolean = false

  /**
    * If this help command should send an event to the handler when all it's
    * commands have ended.
    */
  protected def sendEmptyEvent: Boolean = false

  def terminateCommand(registration: CommandRegistration): Command

  def onBaseMessage(msg: BaseCommand): Behavior[Command] = {
    msg match {
      case CmdMessage(replyTo, ParsedCmd(msg, Some(CommandArgs(cmd)), _, c)) =>
        implicit val cache: CacheSnapshot = c
        val lowercaseCommand              = cmd.toLowerCase(Locale.ROOT)

        val matches = for {
          reg <- commands
          prefix = reg.info.prefix(msg)
          if lowercaseCommand.startsWith(prefix)
          command = lowercaseCommand.substring(prefix.length)
          aliases = reg.info.aliases(msg): Seq[String]
          if aliases.contains(command)
        } yield reg

        val response = if (matches.nonEmpty) Some(createSearchReply(msg, cmd, matches.toSeq)) else unknownCmd(cmd)

        response.map(CreateMessage(msg.channelId, _)) match {
          case Some(req) => sendMessageAndAck(replyTo, req)
          case None      => sendAck(replyTo)
        }

      case CmdMessage(replyTo, ParsedCmd(msg, Some(PageArgs(page)), _, c)) =>
        implicit val cache: CacheSnapshot = c

        if (page > 0) {
          sendMessageAndAck(replyTo, CreateMessage(msg.channelId, createReplyAll(msg, page - 1)))
        } else {
          msg.channelId.tResolve match {
            case Some(channel) => sendMessageAndAck(replyTo, channel.sendMessage(s"Invalid page $page"))
            case None          => sendAck(replyTo)
          }
        }

      case CmdMessage(replyTo, ParsedCmd(msg, None, _, c)) =>
        implicit val cache: CacheSnapshot = c
        sendMessageAndAck(replyTo, CreateMessage(msg.channelId, createReplyAll(msg, 0)))

      case AddCmd(info, description, commandEnd) =>
        val registration = CommandRegistration(info, description)
        commands += registration

        context.pipeToSelf(commandEnd)(_ => terminateCommand(registration))

      case TerminateCommand(registration) =>
        commands -= registration

        if (sendEndedEvent) {
          handler.foreach(_ ! HelpCmd.CommandTerminated(registration))
        }

        if (commands.forall(_.description.extra.contains("ignore-help-last")) && sendEmptyEvent) {
          handler.foreach(_ ! HelpCmd.NoCommandsRemaining)
        }
    }

    Behaviors.same
  }

  /**
    * Sends an ack once the processing of a command is done.
    * @param sender The actor to send the ack to.
    */
  def sendAck(sender: ActorRef[Ack.type]): Unit = sender ! HelpCmd.Ack

  /**
    * Send a request, and acks the sender.
    */
  def sendMessageAndAck(sender: ActorRef[Ack.type], request: Request[RawMessage, NotUsed]): Unit

  /**
    * Create a reply for a search result
    * @param matches All the commands that matched the arguments
    * @return Data to create a message describing the search
    */
  def createSearchReply(message: Message, query: String, matches: Seq[CommandRegistration])(
      implicit c: CacheSnapshot
  ): CreateMessageData

  /**
    * Create a reply for all the commands tracked by this help command.
    * @param page The page to use. Starts at 0.
    * @return Data to create a message describing the commands tracked
    *         by this help command.
    */
  def createReplyAll(message: Message, page: Int)(implicit c: CacheSnapshot): CreateMessageData

  def unknownCmd(command: String): Option[CreateMessageData] =
    Some(CreateMessageData(s"Unknown command $command"))
}
object HelpCmd {
  case class CommandRegistration(info: AbstractCmdInfo, description: CmdDescription)

  trait BaseCommand

  case object Ack extends BaseCommand
  sealed trait HandlerReply

  sealed trait Args extends BaseCommand
  object Args {
    case class CommandArgs(command: String) extends Args
    case class PageArgs(page: Int)          extends Args

    //We write out the parser ourself as string parses any string
    implicit val parser: MessageParser[Args] =
      MessageParser.intParser.map[Args](PageArgs).orElse(MessageParser.stringParser.map(CommandArgs))
  }

  case class TerminateCommand(registration: CommandRegistration)           extends BaseCommand
  case class CmdMessage(replyTo: ActorRef[Ack.type], cmd: ParsedCmd[Option[Args]]) extends BaseCommand

  /**
    * Register a new help entry for a command.
    * @param info The command info for the command.
    * @param description The command description for the command
    * @param commandEnd A future that is completed when the command is removed.
    */
  case class AddCmd(info: AbstractCmdInfo, description: CmdDescription, commandEnd: Future[Done]) extends BaseCommand

  /**
    * Sent to a handler from the help command when a command is unregistered.
    * @param registration The registration info for the command
    */
  case class CommandTerminated(registration: CommandRegistration) extends HandlerReply

  /**
    * Sent from the help command when all the commands it's been managing have
    * been unregistered. Commands that have the extra property named
    * `ignore-help-last` will be ignored from the consideration of all commands.
    */
  case object NoCommandsRemaining extends HandlerReply
}
