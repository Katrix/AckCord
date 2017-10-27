/*
 * This file is part of AkkaCord, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2017 Katrix
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
package net.katsstuff.ackcord.example

import java.nio.file.Paths

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import net.katsstuff.ackcord.DiscordClient.{ClientActor, ShutdownClient}
import net.katsstuff.ackcord.commands.{CommandDescription, CommandErrorHandler, CommandMeta, ParsedCommandActor}
import net.katsstuff.ackcord.data._
import net.katsstuff.ackcord.example.InfoChannelCommand.GetChannelInfo
import net.katsstuff.ackcord.http.rest.Requests
import net.katsstuff.ackcord.http.rest.Requests.CreateMessageData
import net.katsstuff.ackcord.syntax._
import net.katsstuff.ackcord.{DiscordClient, Request, RequestFailed, RequestResponse}

class PingCommand(implicit val client: ClientActor) extends ParsedCommandActor[NotUsed] {
  override def handleCommand(msg: Message, args: NotUsed, remaining: List[String])(implicit c: CacheSnapshot): Unit = {
    client ! Request(
      Requests.CreateMessage(msg.channelId, CreateMessageData("Pong", None, tts = false, None, None)),
      NotUsed,
      Some(errorResponder)
    )
  }
}
object PingCommand {
  def props(implicit client: ClientActor): Props = Props(new PingCommand)
  def cmdMeta(implicit client: ClientActor): CommandMeta[NotUsed] =
    CommandMeta[NotUsed](
      category = ExampleCmdCategories.!,
      alias = Seq("ping"),
      handler = props,
      description =
        Some(CommandDescription(name = "Ping", description = "Ping this bot and get a response. Used for testing")),
    )
}

class SendFileCommand(implicit val client: ClientActor) extends ParsedCommandActor[NotUsed] {
  override def handleCommand(msg: Message, args: NotUsed, remaining: List[String])(implicit c: CacheSnapshot): Unit = {
    val embed = OutgoingEmbed(
      title = Some("This is an embed"),
      description = Some("This embed is sent together with a file"),
      fields = Seq(EmbedField("FileName", "theFile.txt"))
    )

    msg.tChannel.foreach { tChannel =>
      //Use channel to construct request
      client ! tChannel.sendMessage(
        "Here is the file",
        file = Some(Paths.get("theFile.txt")),
        embed = Some(embed),
        sendResponseTo = Some(errorResponder)
      )
    }
  }
}
object SendFileCommand {
  def props(implicit client: ClientActor): Props = Props(new SendFileCommand)
  def cmdMeta(implicit client: ClientActor): CommandMeta[NotUsed] =
    CommandMeta[NotUsed](
      category = ExampleCmdCategories.!,
      alias = Seq("sendFile"),
      handler = props,
      description = Some(
        CommandDescription(name = "Send file", description = "Make the bot send an embed with a file. Used for testing")
      ),
    )
}

class InfoChannelCommand(implicit val client: ClientActor) extends ParsedCommandActor[GuildChannel] {

  val infoResponseHandler: ActorRef = context.actorOf(InfoCommandHandler.props(client))

  override def handleCommand(msg: Message, channel: GuildChannel, remaining: List[String])(
      implicit c: CacheSnapshot
  ): Unit = {
    client ! client.fetchChannel(
      channel.id,
      GetChannelInfo(channel.guildId, channel.id, msg.channelId, c),
      Some(infoResponseHandler)
    )
  }
}
object InfoChannelCommand {
  def props(implicit client: ClientActor): Props = Props(new InfoChannelCommand)
  def cmdMeta(implicit client: ClientActor): CommandMeta[GuildChannel] =
    CommandMeta[GuildChannel](
      category = ExampleCmdCategories.!,
      alias = Seq("infoChannel"),
      handler = props,
      description = Some(
        CommandDescription(
          name = "Channel info",
          description = "Make the bot fetch information about a text channel from Discord. Used for testing"
        )
      ),
    )

  case class GetChannelInfo(
      guildId: GuildId,
      requestedChannelId: ChannelId,
      senderChannelId: ChannelId,
      c: CacheSnapshot
  )
}

class InfoCommandHandler(implicit client: ClientActor) extends Actor with ActorLogging {
  override def receive: Receive = {
    case RequestResponse(res, GetChannelInfo(guildId, requestedChannelId, senderChannelId, c)) =>
      implicit val cache: CacheSnapshot = c
      val optName = cache.getGuildChannel(guildId, requestedChannelId).map(_.name)
      optName match {
        case Some(name) =>
          cache.getGuildChannel(guildId, senderChannelId) match {
            case Some(channel: TGuildChannel) => client ! channel.sendMessage(s"Info for $name:\n$res")
            case Some(channel: GuildChannel)  => log.warning("{} is not a valid text channel", channel.name)
            case None                         => log.warning("No channel found for {}", requestedChannelId)
          }
        case None => log.warning("No channel found for {}", requestedChannelId)
      }

      log.info(res.toString)
    case RequestFailed(e, _) => throw e
  }
}
object InfoCommandHandler {
  def props(implicit client: ClientActor): Props = Props(new InfoCommandHandler)
}

class KillCommand(main: ActorRef)(implicit val client: ClientActor)
    extends ParsedCommandActor[NotUsed]
    with ActorLogging {
  context.watch(main)

  override def receive: Receive = {
    case DiscordClient.ShutdownClient => //We make sure to ignore this to be able to run code after the shutdown is complete
    case Terminated(`main`) =>
      log.info("Everything shut down")
      context.system.terminate()
    case x if super.receive.isDefinedAt(x) => super.receive(x)
  }

  override def handleCommand(msg: Message, args: NotUsed, remaining: List[String])(implicit c: CacheSnapshot): Unit = {
    log.info("Received shutdown command")
    main ! ShutdownClient
  }
}
object KillCommand {
  def props(mainActor: ActorRef)(implicit client: ClientActor): Props = Props(new KillCommand(mainActor))
  def cmdMeta(mainActor: ActorRef)(implicit client: ClientActor): CommandMeta[NotUsed] =
    CommandMeta[NotUsed](
      category = ExampleCmdCategories.!,
      alias = Seq("kill", "die"),
      handler = props(mainActor),
      description = Some(CommandDescription(name = "Kill bot", description = "Shut down this bot")),
    )
}

class ExampleErrorHandler(implicit val client: ClientActor) extends CommandErrorHandler {
  override def noCommandReply(msg: Message)(implicit c: CacheSnapshot): Option[CreateMessageData] =
    Some(CreateMessageData("No command specified"))
  override def unknownCommandReply(msg: Message, args: List[String])(
      implicit c: CacheSnapshot
  ): Option[CreateMessageData] = {
    println("Sending no command" + self)
    Some(CreateMessageData(s"No command named ${args.head} known"))
  }
}
object ExampleErrorHandler {
  def props(implicit client: ClientActor): Props = Props(new ExampleErrorHandler)
}

class IgnoreUnknownErrorHandler(implicit val client: ClientActor) extends CommandErrorHandler {
  override def noCommandReply(msg: Message)(implicit c: CacheSnapshot): Option[CreateMessageData] = None
  override def unknownCommandReply(msg: Message, args: List[String])(
      implicit c: CacheSnapshot
  ): Option[CreateMessageData] = None
}
object IgnoreUnknownErrorHandler {
  def props(implicit client: ClientActor): Props = Props(new IgnoreUnknownErrorHandler)
}
