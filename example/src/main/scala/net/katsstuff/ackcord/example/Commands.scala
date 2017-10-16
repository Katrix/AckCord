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
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import net.katsstuff.ackcord.DiscordClient.{ClientActor, ShutdownClient}
import net.katsstuff.ackcord.data._
import Commands.GetChannelInfo
import net.katsstuff.ackcord.http.rest.Requests
import net.katsstuff.ackcord.http.rest.Requests.CreateMessageData
import net.katsstuff.ackcord.syntax._
import net.katsstuff.ackcord.{APIMessage, Request, RequestResponse}

class Commands(client: ClientActor) extends Actor with ActorLogging {
  implicit val system: ActorSystem = context.system

  val infoResponseHandler: ActorRef = system.actorOf(InfoCommandHandler.props(client))

  override def receive: Receive = {
    case APIMessage.MessageCreate(message, c, _) =>
      implicit val cache: CacheSnapshot = c
      message.content match {
        case "!ping" =>
          //Construct request manually
          client ! Request(
            Requests.CreateMessage(message.channelId, CreateMessageData("Pong", None, tts = false, None, None)),
            NotUsed,
            None
          )
        case "!sendFile" =>
          val embed = OutgoingEmbed(
            title = Some("This is an embed"),
            description = Some("This embed is sent together with a file"),
            fields = Seq(EmbedField("FileName", "theFile.txt"))
          )

          message.tChannel.foreach { tChannel =>
            //Use channel to construct request
            client ! tChannel.sendMessage(
              "Here is the file",
              file = Some(Paths.get("theFile.txt")),
              embed = Some(embed)
            )
          }
        case s if s.startsWith("!infoChannel ") =>
          val withChannel = s.substring("!infoChannel ".length)
          val r           = """<#(\d+)>""".r

          val channel = r
            .findFirstMatchIn(withChannel)
            .map(_.group(1))
            .map((ChannelId.apply _).compose(Snowflake.apply))
            .flatMap(id => message.guild.flatMap(_.channelById(id)))

          channel match {
            case Some(gChannel) =>
              client ! client.fetchChannel(
                gChannel.id,
                GetChannelInfo(gChannel.guildId, gChannel.id, message.channelId, c),
                Some(infoResponseHandler)
              )
            case None => message.tChannel.foreach(client ! _.sendMessage("Channel not found"))
          }
        case "!kill" =>
          log.info("Received shutdown command")
          client ! ShutdownClient
        case _ =>
      }
  }
}
object Commands {
  def props(client: ClientActor): Props = Props(new Commands(client))
  case class GetChannelInfo(
      guildId: GuildId,
      requestedChannelId: ChannelId,
      senderChannelId: ChannelId,
      c: CacheSnapshot
  )
}

class InfoCommandHandler(client: ClientActor) extends Actor with ActorLogging {
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
  }
}
object InfoCommandHandler {
  def props(client: ClientActor): Props = Props(new InfoCommandHandler(client))
}
