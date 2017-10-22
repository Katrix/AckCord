/*
 * This file is part of AckCord, licensed under the MIT License (MIT).
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

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}

import com.sedmelluq.discord.lavaplayer.player._
import com.sedmelluq.discord.lavaplayer.player.event._
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.{AudioItem, AudioPlaylist, AudioTrack}

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Cancellable, Props}
import akka.pattern.pipe
import akka.stream.Materializer
import akka.util.ByteString
import net.katsstuff.ackcord.DiscordClient.ClientActor
import net.katsstuff.ackcord.commands.{
  CommandDescription,
  CommandDispatcher,
  CommandFilter,
  CommandMeta,
  ParsedCommandActor
}
import net.katsstuff.ackcord.data.{CacheSnapshot, ChannelId, GuildId, Message, Snowflake}
import net.katsstuff.ackcord.example.DataSender.{SendMusic, StartSendMusic, StopSendMusic}
import net.katsstuff.ackcord.example.MusicHandler.{QueueUrl, StopMusic}
import net.katsstuff.ackcord.http.websocket.AbstractWsHandler.{Login, Logout}
import net.katsstuff.ackcord.http.websocket.gateway.{VoiceStateUpdate, VoiceStateUpdateData}
import net.katsstuff.ackcord.http.websocket.voice.VoiceUDPHandler.{silence, SendData}
import net.katsstuff.ackcord.http.websocket.voice.VoiceWsHandler
import net.katsstuff.ackcord.http.websocket.voice.VoiceWsHandler.{SetSpeaking, VoiceReady}
import net.katsstuff.ackcord.{APIMessage, AudioAPIMessage}

class MusicHandler(client: ClientActor, guildId: GuildId)(implicit mat: Materializer) extends Actor with ActorLogging {
  implicit val impClient: ClientActor = client

  implicit val system: ActorSystem = context.system
  import system.dispatcher

  private val manager: AudioPlayerManager = {
    val man = new DefaultAudioPlayerManager
    AudioSourceManagers.registerRemoteSources(man)
    man.enableGcMonitoring()
    man.setPlayerCleanupThreshold(3000)
    man.getConfiguration.setResamplingQuality(AudioConfiguration.ResamplingQuality.MEDIUM)
    man
  }

  val commands =
    Seq(JoinCommand.cmdMeta(guildId), StopCommand.cmdMeta(self), QueueCommand.cmdMeta(self))

  val commandDispatcher: ActorRef = context.actorOf(
    CommandDispatcher
      .props(needMention = true, CommandMeta.dispatcherMap(commands), CommandErrorHandler.props),
    "MusicHandlerCommands"
  )

  var player:     AudioPlayer = _
  var sessionId:  String      = _
  var token:      String      = _
  var endPoint:   String      = _
  var voiceWs:    ActorRef    = _
  var dataSender: ActorRef    = _

  override def receive: Receive = {
    case msg: APIMessage.MessageCreate => commandDispatcher.forward(msg)
    case StopMusic =>
      player.stopTrack()
      voiceWs ! Logout
      context.stop(dataSender)

      client ! VoiceStateUpdate(VoiceStateUpdateData(guildId, None, selfMute = false, selfDeaf = false))
      player = null
      sessionId = null
      token = null
      endPoint = null
      voiceWs = null
      dataSender = null
      log.info("Left")
    case QueueUrl(url) =>
      log.info("Received queue item")
      loadItem(url).pipeTo(self)
    case e: FriendlyException =>
      e.printStackTrace() //TODO: Handle better
    case VoiceReady(udpHandler) =>
      log.info("Audio ready")
      if (player == null) {
        player = manager.createPlayer()
        player.addListener(new AudioEventSender(self))
      }
      dataSender = context.actorOf(DataSender.props(player, udpHandler, voiceWs), "DataSender")
    case e: AudioAPIMessage => log.info(e.toString)
    case APIMessage.VoiceStateUpdate(state, c, _) if state.userId == c.botUser.id =>
      sessionId = state.sessionId
      if (token != null && endPoint != null) {
        connect(c)
      }
      log.info("Received session id")
    case APIMessage.VoiceServerUpdate(receivedToken, guild, receivedEndpoint, c, _) if guild.id == guildId =>
      token = receivedToken
      endPoint = if (receivedEndpoint.endsWith(":80")) receivedEndpoint.dropRight(3) else receivedEndpoint
      if (sessionId != null) {
        connect(c)
      }
      log.info("Got token and endpoint")
    case APIMessage.VoiceStateUpdate(_, _, _) =>
    case track: AudioTrack =>
      player.startTrack(track, true)
      log.info("Received track")
    case playlist: AudioPlaylist =>
      log.info("Received playlist")
      Option(playlist.getSelectedTrack)
        .orElse(playlist.getTracks.asScala.headOption)
        .foreach(player.startTrack(_, true))
    case e: PlayerPauseEvent =>
      log.info(e.toString)
      dataSender ! StopSendMusic
    case e: PlayerResumeEvent =>
      log.info(e.toString)
      dataSender ! StartSendMusic
    case e: TrackStartEvent =>
      log.info(e.toString)
      dataSender ! StartSendMusic
    case e: TrackEndEvent =>
      log.info(e.toString)
      dataSender ! StopSendMusic
    case e: TrackExceptionEvent =>
      log.info(e.toString)
      dataSender ! StopSendMusic
    case e: TrackStuckEvent =>
      log.info(e.toString)
      dataSender ! StopSendMusic
  }

  def connect(c: CacheSnapshot): Unit = {
    voiceWs = context.actorOf(
      VoiceWsHandler.props(endPoint, guildId, c.botUser.id, sessionId, token, Some(self), None),
      "VoiceWS"
    )
    voiceWs ! Login
    log.info("Connected")
  }

  def loadItem(identifier: String): Future[AudioItem] = {
    val promise = Promise[AudioItem]

    manager.loadItem(identifier, new AudioLoadResultHandler {
      override def loadFailed(e: FriendlyException): Unit = promise.failure(e)

      override def playlistLoaded(playlist: AudioPlaylist): Unit = promise.success(playlist)

      override def noMatches(): Unit = promise.failure(new NoMatchException)

      override def trackLoaded(track: AudioTrack): Unit = promise.success(track)
    })

    promise.future
  }
}
object MusicHandler {
  def props(client: ClientActor)(guildId: GuildId)(implicit mat: Materializer): Props =
    Props(new MusicHandler(client, guildId))
  case object StopMusic
  case class QueueUrl(url: String)
}

class JoinCommand(guildId: GuildId)(implicit val client: ClientActor) extends ParsedCommandActor[String] with ActorLogging {
  override def handleCommand(msg: Message, rawChannel: String, remaining: List[String])(
      implicit c: CacheSnapshot
  ): Unit = {
    client ! VoiceStateUpdate(
      VoiceStateUpdateData(guildId, Some(ChannelId(Snowflake(rawChannel))), selfMute = false, selfDeaf = false)
    )
    log.info("Joined")
  }
}
object JoinCommand {
  def props(guildId: GuildId)(implicit client: ClientActor): Props =
    Props(new JoinCommand(guildId))
  def cmdMeta(guildId: GuildId)(implicit client: ClientActor): CommandMeta[String] =
    CommandMeta[String](
      category = ExampleCmdCategories.&,
      alias = Seq("j", "join"),
      handler = props(guildId),
      filters = Seq(CommandFilter.InGuild),
      description = Some(
        CommandDescription(name = "Join channel", description = "Makes the bot join a channel", usage = "<channelId>")
      ),
    )
}

class StopCommand(musicHandler: ActorRef)(implicit val client: ClientActor) extends ParsedCommandActor[NotUsed] {
  override def handleCommand(msg: Message, args: NotUsed, remaining: List[String])(implicit c: CacheSnapshot): Unit =
    musicHandler ! StopMusic
}
object StopCommand {
  def props(musicHandler: ActorRef)(implicit client: ClientActor): Props = Props(new StopCommand(musicHandler))
  def cmdMeta(musicHandler: ActorRef)(implicit client: ClientActor): CommandMeta[NotUsed] =
    CommandMeta[NotUsed](
      category = ExampleCmdCategories.&,
      alias = Seq("s", "stop"),
      handler = props(musicHandler),
      filters = Seq(CommandFilter.InGuild),
      description =
        Some(CommandDescription(name = "Stop music", description = "Stop music from playing, and leave the channel")),
    )
}

class QueueCommand(musicHandler: ActorRef)(implicit val client: ClientActor) extends ParsedCommandActor[String] {
  override def handleCommand(msg: Message, url: String, remaining: List[String])(implicit c: CacheSnapshot): Unit =
    musicHandler ! QueueUrl(url)
}
object QueueCommand {
  def props(musicHandler: ActorRef)(implicit client: ClientActor): Props = Props(new QueueCommand(musicHandler))
  def cmdMeta(musicHandler: ActorRef)(implicit client: ClientActor): CommandMeta[NotUsed] =
    CommandMeta[NotUsed](
      category = ExampleCmdCategories.&,
      alias = Seq("q", "queue"),
      handler = props(musicHandler),
      filters = Seq(CommandFilter.InGuild),
      description = Some(CommandDescription(name = "Queue music", description = "Set an url as the url to play")),
    )
}

class DataSender(player: AudioPlayer, udpHandler: ActorRef, wsHandler: ActorRef) extends Actor with ActorLogging {

  implicit val system: ActorSystem = context.system
  import system.dispatcher

  var cancelable: Cancellable = _

  override def postStop(): Unit =
    if (cancelable != null) {
      cancelable.cancel()
    }

  override def receive: Receive = {
    case SendMusic =>
      val frame = player.provide()
      if (frame != null) {
        udpHandler ! SendData(ByteString.fromArray(frame.data))
      }
    case StartSendMusic =>
      if (cancelable == null) {
        wsHandler ! SetSpeaking(true)
        log.info("Starting to send music")
        cancelable = system.scheduler.schedule(20.millis, 20.millis, self, SendMusic)
      }
    case StopSendMusic =>
      if (cancelable != null) {
        wsHandler ! SetSpeaking(false)
        log.info("Stopping to send music")
        for (i <- 1 to 5) {
          system.scheduler.scheduleOnce(i * 20.millis, udpHandler, SendData(silence))
        }

        cancelable.cancel()
        cancelable = null
      }
  }
}
object DataSender {
  def props(player: AudioPlayer, udpHandler: ActorRef, wsHandler: ActorRef): Props =
    Props(new DataSender(player, udpHandler, wsHandler))
  case object SendMusic
  case object StartSendMusic
  case object StopSendMusic
}

class AudioEventSender(sendTo: ActorRef) extends AudioEventListener {

  override def onEvent(event: AudioEvent): Unit =
    sendTo ! event
}

class NoMatchException extends Exception
