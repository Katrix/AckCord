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
package ackcord.example

import java.nio.file.Paths
import java.time.temporal.ChronoUnit

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.util.Random

import ackcord._
import ackcord.commands._
import ackcord.data.{EmbedField, GuildId, OutgoingEmbed, Permission}
import ackcord.requests.{CreateMessage, Request}
import ackcord.syntax._
import akka.NotUsed
import akka.stream.scaladsl.{Flow, Sink}
import cats.syntax.all._
import com.sedmelluq.discord.lavaplayer.player.{AudioPlayerManager, DefaultAudioPlayerManager}
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.track.{AudioPlaylist, AudioTrack}

class MyCommands(client: DiscordClient, requests: Requests) extends CommandController(requests) {

  val hello: NamedDescribedCommand[NotUsed] =
    Command
      .named(Seq("!"), Seq("hello"), mustMention = true) //Simplest way to name a command
      .described("Hello", "Say hello")
      .withRequest(m => m.textChannel.sendMessage(s"Hello ${m.user.username}"))

  val mentionGuilds = Seq(GuildId("269988507378909186"))

  val copy: NamedDescribedCommand[Int] =
    GuildCommand
      //You can use functions to give different names depending on the context the command is executed in
      .namedFunction(
        (c, m) => if (m.guild(c).map(_.id).exists(mentionGuilds.contains)) Seq("!") else Seq("m!"),
        (_, _) => Seq("copy"),
        mustMention = (c, m) => m.guild(c).map(_.id).exists(mentionGuilds.contains)
      )
      .described("Copy", "Make the bot say what you said")
      .parsing[Int]
      .withRequestOpt { implicit m =>
        m.message.channelId.resolve(m.guild.id).map(_.sendMessage(s"You said ${m.parsed}"))
      }

  //Here we just store this in-memory, but in a real application you'd
  //probably store it in a database instead
  val shouldMentionMap = new TrieMap[GuildId, Boolean]
  val prefixSymbolsMap = new TrieMap[GuildId, Seq[String]]

  def needMentionInGuild(guildId: GuildId): Future[Boolean] =
    Future.successful(shouldMentionMap.getOrElseUpdate(guildId, false))

  def prefixSymbolsInGuild(guildId: GuildId): Future[Seq[String]] =
    Future.successful(prefixSymbolsMap.getOrElseUpdate(guildId, Seq("m!")))

  //Name info is stored in an object called StructuredPrefixParser
  //You can construct this like we showed above, or you can also use a future returning function
  //The future returning function is also available on the builder itself
  def dynamicPrefix(aliases: String*): StructuredPrefixParser =
    PrefixParser.structuredAsync(
      (c, m) => m.guild(c).fold(Future.successful(false))(g => needMentionInGuild(g.id)),
      (c, m) => m.guild(c).fold(Future.successful(Seq("m!")))(g => prefixSymbolsInGuild(g.id)),
      (_, _) => Future.successful(aliases)
    )

  val setShouldMention: NamedDescribedCommand[Boolean] =
    GuildCommand
      .namedParser(dynamicPrefix("setShouldMention"))
      .described("Set should mention", "Set if commands need a mention of the bot before the prefix")
      .parsing[Boolean]
      .withRequest { m =>
        shouldMentionMap.put(m.guild.id, m.parsed)
        m.textChannel.sendMessage(s"Set should mention to ${m.parsed}")
      }

  val modifyPrefixSymbols: NamedDescribedCommand[(String, String)] = {
    import MessageParser.Auto._ //Import auto so that we have an instance for (String, String) in scope
    GuildCommand
      .namedParser(dynamicPrefix("modifyPrefixSymbols"))
      .described("Modify prefix symbols", "Add, set or remove prefix symbols")
      .parsing[(String, String)]
      .withRequest { m =>
        m.parsed._1 match {
          case "add" | "+" =>
            Compat.updateWith(prefixSymbolsMap, m.guild.id) {
              case Some(existing) => Some((existing :+ m.parsed._2).distinct)
              case None           => Some(Seq(m.parsed._2))
            }
            m.textChannel.sendMessage(s"Added ${m.parsed._2} as a prefix symbol")

          case "set" | "=" =>
            Compat.updateWith(prefixSymbolsMap, m.guild.id) {
              case Some(_) => Some(Seq(m.parsed._2))
              case None    => Some(Seq(m.parsed._2))
            }
            m.textChannel.sendMessage(s"Set ${m.parsed._2} as the only prefix symbol")
          case "remove" | "-" =>
            //Race condition here, but I don't care
            if (prefixSymbolsMap.get(m.guild.id).exists(_.length > 1)) {
              Compat.updateWith(prefixSymbolsMap, m.guild.id) {
                case Some(Seq(_))   => throw new Exception("Race condition")
                case None           => throw new Exception("Race condition")
                case Some(existing) => Some(existing.filter(_ != m.parsed._2))
              }
              m.textChannel.sendMessage(s"Removed ${m.parsed._2} as a prefix symbol")
            } else {
              m.textChannel
                .sendMessage(s"Couldn't remove ${m.parsed._2} as a prefix symbol. Not enough existing prefixes")
            }
          case _ => m.textChannel.sendMessage(s"${m.parsed._1} is not a valid operation")
        }
      }
  }

  val guildInfo: NamedDescribedCommand[NotUsed] =
    GuildCommand
      .namedParser(dynamicPrefix("guildInfo"))
      .described("Guild info", "Prints info about the current guild")
      .withRequest { m =>
        val guildName   = m.guild.name
        val channelName = m.textChannel.name
        val userNick    = m.guildMember.nick.getOrElse(m.user.username)

        m.textChannel.sendMessage(
          s"This guild is named $guildName, the channel is named $channelName and you are called $userNick"
        )
      }

  val parsingNumbers: NamedDescribedCommand[(Int, Int)] =
    Command
      .namedParser(dynamicPrefix("parseNum"))
      .described("Parse numbers", "Have the bot parse two numbers")
      .parsing((MessageParser[Int], MessageParser[Int]).tupled)
      .withRequest(m => m.textChannel.sendMessage(s"Arg 1: ${m.parsed._1}, Arg 2: ${m.parsed._2}"))

  val sendFile: NamedDescribedCommand[NotUsed] =
    Command.namedParser(dynamicPrefix("sendFile")).described("Send file", "Send a file in an embed").withRequest { m =>
      val embed = OutgoingEmbed(
        title = Some("This is an embed"),
        description = Some("This embed is sent together with a file"),
        fields = Seq(EmbedField("FileName", "theFile.txt"))
      )

      m.textChannel.sendMessage("Here is the file", files = Seq(Paths.get("theFile.txt")), embeds = Seq(embed))
    }

  private val ElevatedCommand: CommandBuilder[GuildUserCommandMessage, NotUsed] =
    GuildCommand.andThen(CommandBuilder.needPermission[GuildUserCommandMessage](Permission.Administrator))

  val adminsOnly: NamedDescribedCommand[NotUsed] =
    ElevatedCommand
      .namedParser(dynamicPrefix("adminOnly"))
      .described("Elevanted command", "Command only admins can use")
      .withSideEffects(_ => println("Command executed by an admin"))

  val timeDiff: NamedDescribedCommand[NotUsed] =
    Command
      .namedParser(dynamicPrefix("timeDiff"))
      .described("Time diff", "Checks the time between sending and seeing a message")
      .asyncOpt { implicit m =>
        import requestHelper._
        for {
          sentMsg <- run(m.textChannel.sendMessage("Msg"))
          time = ChronoUnit.MILLIS.between(m.message.timestamp, sentMsg.timestamp)
          _ <- run(m.textChannel.sendMessage(s"$time ms between command and response"))
        } yield ()
      }

  val ping: NamedDescribedCommand[NotUsed] =
    Command.namedParser(dynamicPrefix("ping")).described("Ping", "Checks if the bot is alive").toSink {
      Flow[CommandMessage[NotUsed]]
        .map(m => CreateMessage.mkContent(m.message.channelId, "Pong"))
        .to(requests.sinkIgnore)
    }

  def ratelimitTest(name: String, sink: Sink[Request[_], _]): NamedDescribedCommand[Int] =
    Command
      .namedParser(dynamicPrefix(name))
      .described("Ratelimit test", "Checks that ratelimiting is working as intended")
      .parsing[Int]
      .toSink {
        Flow[CommandMessage[Int]]
          .mapConcat(implicit m => List.tabulate(m.parsed)(i => m.textChannel.sendMessage(s"Msg$i")))
          .to(sink)
      }

  val maybeFail: NamedDescribedCommand[NotUsed] = Command
    .namedParser(dynamicPrefix("maybeFail"))
    .described("MaybeFail", "A command that sometimes fails and throws an exception")
    .withRequest { r =>
      if (Random.nextInt(100) < 25) {
        throw new Exception("Failed")
      }

      r.textChannel.sendMessage("Succeeded")
    }

  val kill: NamedDescribedCommand[NotUsed] =
    ElevatedCommand
      .namedParser(dynamicPrefix("kill", "die"))
      .described("Kill", "Kills the bot")
      .withSideEffects(_ => client.shutdownJVM())

  val playerManager: AudioPlayerManager = new DefaultAudioPlayerManager
  AudioSourceManagers.registerRemoteSources(playerManager)

  val queue: NamedCommand[String] =
    GuildVoiceCommand.namedParser(dynamicPrefix("queue", "q")).parsing[String].streamed { r =>
      val guildId     = r.guild.id
      val url         = r.parsed
      val loadItem    = client.loadTrack(playerManager, url)
      val joinChannel = client.joinChannel(guildId, r.voiceChannel.id, playerManager.createPlayer())

      loadItem.zip(joinChannel).map {
        case (track: AudioTrack, player) =>
          player.startTrack(track, true)
          client.setPlaying(guildId, playing = true)
        case (playlist: AudioPlaylist, player) =>
          if (playlist.getSelectedTrack != null) {
            player.startTrack(playlist.getSelectedTrack, false)
          } else {
            player.startTrack(playlist.getTracks.get(0), false)
          }
          client.setPlaying(guildId, playing = true)
        case _ => sys.error("Unknown audio item")
      }
    }

}
