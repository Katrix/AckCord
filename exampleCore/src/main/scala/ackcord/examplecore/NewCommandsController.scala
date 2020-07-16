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

package ackcord.examplecore

import java.nio.file.Paths
import java.time.temporal.ChronoUnit

import scala.concurrent.Future
import scala.util.Random

import ackcord._
import ackcord.data._
import ackcord.commands._
import ackcord.requests.{CreateMessage, Request}
import ackcord.syntax._
import akka.NotUsed
import akka.actor.CoordinatedShutdown
import akka.actor.typed.scaladsl.adapter._
import akka.stream.scaladsl.{Flow, Sink}
import cats.syntax.all._
import cats.instances.future._

//Lot's of different examples of how to use commands
class NewCommandsController(requests: Requests) extends CommandController(requests) {

  val general = Seq("!")

  val hello: NamedCommand[NotUsed] = Command
    .named(general, Seq("hello"), mustMention = true)
    .withRequest(m => m.textChannel.sendMessage(s"Hello ${m.user.username}"))

  val copy: NamedCommand[Int] =
    GuildCommand.named(general, Seq("copy"), mustMention = true).parsing[Int].withRequestOpt { implicit m =>
      m.message.channelId.resolve(m.guild.id).map(_.sendMessage(s"You said ${m.parsed}"))
    }

  val guildInfo: NamedCommand[NotUsed] =
    GuildCommand.named(general, Seq("guildInfo"), mustMention = true).withRequest { m =>
      val guildName   = m.guild.name
      val channelName = m.textChannel.name
      val userNick    = m.guildMember.nick.getOrElse(m.user.username)

      m.textChannel.sendMessage(
        s"This guild is named $guildName, the channel is named $channelName and you are called $userNick"
      )
    }

  val parsingNumbers: NamedCommand[(Int, Int)] =
    Command
      .named(general, Seq("parseNum"), mustMention = true)
      .parsing((MessageParser[Int], MessageParser[Int]).tupled)
      .withRequest(m => m.textChannel.sendMessage(s"Arg 1: ${m.parsed._1}, Arg 2: ${m.parsed._2}"))

  val sendFile: NamedCommand[NotUsed] = Command.named(general, Seq("sendFile"), mustMention = true).withRequest { m =>
    val embed = OutgoingEmbed(
      title = Some("This is an embed"),
      description = Some("This embed is sent together with a file"),
      fields = Seq(EmbedField("FileName", "theFile.txt"))
    )

    m.textChannel.sendMessage("Here is the file", files = Seq(Paths.get("theFile.txt")), embed = Some(embed))
  }

  private val ElevatedCommand: CommandBuilder[GuildUserCommandMessage, NotUsed] =
    GuildCommand.andThen(CommandBuilder.needPermission[GuildUserCommandMessage](Permission.Administrator))

  val adminsOnly: NamedCommand[NotUsed] =
    ElevatedCommand.named(general, Seq("adminOnly"), mustMention = true).withSideEffects { _ =>
      println("Command executed by an admin")
    }

  val timeDiff: NamedCommand[NotUsed] =
    Command.named(general, Seq("timeDiff"), mustMention = true).asyncOpt { implicit m =>
      import requestHelper._
      for {
        sentMsg <- run(m.textChannel.sendMessage("Msg"))
        time = ChronoUnit.MILLIS.between(m.message.timestamp, sentMsg.timestamp)
        _ <- run(m.textChannel.sendMessage(s"$time ms between command and response"))
      } yield ()
    }

  val ping: NamedCommand[NotUsed] = Command.named(general, Seq("ping"), mustMention = true).toSink {
    Flow[CommandMessage[NotUsed]]
      .map(m => CreateMessage.mkContent(m.message.channelId, "Pong"))
      .to(requests.sinkIgnore)
  }

  val timeDiff2: NamedCommand[NotUsed] =
    Command.named(general, Seq("timeDiff2"), mustMention = true).async { implicit m =>
      //The ExecutionContext is provided by the controller
      for {
        answer  <- requests.singleFuture(m.textChannel.sendMessage("Msg"))
        sentMsg <- Future.fromTry(answer.eitherData.toTry)
        time = ChronoUnit.MILLIS.between(m.message.timestamp, sentMsg.timestamp)
        _ <- requests.singleFuture(m.textChannel.sendMessage(s"$time ms between command and response"))
      } yield ()
    }

  def ratelimitTest(name: String, sink: Sink[Request[_], _]): NamedCommand[Int] =
    Command.named(general, Seq(name), mustMention = true).parsing[Int].toSink {
      Flow[CommandMessage[Int]]
        .mapConcat(implicit m => List.tabulate(m.parsed)(i => m.textChannel.sendMessage(s"Msg$i")))
        .to(sink)
    }

  val maybeFail: NamedCommand[NotUsed] = Command.named(general, Seq("maybeFail"), mustMention = true).withRequest { r =>
    if (Random.nextInt(100) < 25) {
      throw new Exception("Failed")
    }

    r.textChannel.sendMessage("Succeeded")
  }

  val kill: NamedCommand[NotUsed] =
    ElevatedCommand
      .named(general, Seq("kill", "die"), mustMention = true)
      .withSideEffects(_ => CoordinatedShutdown(requests.system.toClassic).run(CoordinatedShutdown.JvmExitReason))
}
