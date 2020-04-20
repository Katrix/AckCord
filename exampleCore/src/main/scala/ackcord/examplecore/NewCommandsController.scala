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

import java.time.temporal.ChronoUnit

import scala.concurrent.Future
import scala.util.Random

import ackcord._
import ackcord.data._
import ackcord.commands._
import ackcord.requests.CreateMessage
import ackcord.syntax._
import akka.NotUsed
import akka.stream.scaladsl.Flow
import cats.syntax.all._
import cats.instances.future._

class NewCommandsController(requests: Requests) extends CommandController(requests) {

  val hello: NamedCommand[NotUsed] = Command
    .named("%", Seq("hello"), mustMention = true)
    .withRequest(implicit m => m.textChannel.sendMessage(s"Hello ${m.user.username}"))

  val copy: NamedCommand[Int] =
    Command.named("%", Seq("copy"), mustMention = true).parsing[Int].withRequestOpt { implicit m =>
      m.message.textGuildChannel.map(_.sendMessage(s"You said ${m.parsed}"))
    }

  val guildInfo: NamedCommand[NotUsed] =
    GuildCommand.named("%", Seq("guildInfo"), mustMention = true).withRequest { implicit m =>
      val guildName   = m.guild.name
      val channelName = m.textChannel.name
      val userNick    = m.guildMember.nick.getOrElse(m.user.username)

      m.textChannel.sendMessage(
        s"This guild is named $guildName, the channel is named $channelName and you are called $userNick"
      )
    }

  val parsingNumbers: NamedCommand[(Int, Int)] =
    Command
      .named("%", Seq("parseNum"), mustMention = true)
      .parsing((MessageParser[Int], MessageParser[Int]).tupled)
      .withRequestOpt { implicit m =>
        m.message.textGuildChannel.map(_.sendMessage(s"Arg 1: ${m.parsed._1}, Arg 2: ${m.parsed._2}"))
      }

  private val ElevatedCommand: CommandBuilder[GuildUserCommandMessage, NotUsed] =
    GuildCommand.andThen(CommandBuilder.needPermission[GuildUserCommandMessage](Permission.Administrator))

  val adminsOnly: NamedCommand[NotUsed] =
    ElevatedCommand.named("%", Seq("adminOnly"), mustMention = true).withSideEffects { _ =>
      println("Command executed by an admin")
    }

  val timeDiff: NamedCommand[NotUsed] =
    Command.named("%", Seq("timeDiff"), mustMention = true).streamed[OptionTRequest] { implicit m =>
      import requestHelper._
      for {
        channel <- optionPure(m.message.channelId.tResolve)
        sentMsg <- run(channel.sendMessage("Msg"))
        _ <- {
          val time = ChronoUnit.MILLIS.between(m.message.timestamp, sentMsg.timestamp)
          run(channel.sendMessage(s"$time ms between command and response"))
        }
      } yield ()
    }

  val ping: NamedCommand[NotUsed] = Command.named("%", Seq("ping"), mustMention = true).toSink {
    Flow[CommandMessage[NotUsed]]
      .map(m => CreateMessage.mkContent(m.message.channelId, "Pong"))
      .to(requests.sinkIgnore)
  }

  val timeDiff2: NamedCommand[NotUsed] =
    Command.named("%", Seq("timeDiff2"), mustMention = true).streamed[Future] { implicit m =>
      //The ExecutionContext is provided by the controller
      for {
        answer  <- requests.singleFuture(m.textChannel.sendMessage("Msg"))
        sentMsg <- Future.fromTry(answer.eitherData.toTry)
        time = ChronoUnit.MILLIS.between(m.message.timestamp, sentMsg.timestamp)
        _ <- requests.singleFuture(m.textChannel.sendMessage(s"$time ms between command and response"))
      } yield ()
    }

  val maybeFail: NamedCommand[NotUsed] = Command.named("%", Seq("maybeFail"), mustMention = true).withRequest { r =>
    if (Random.nextInt(100) < 25) {
      throw new Exception("Failed")
    }

    r.textChannel.sendMessage("Succeeded")
  }
}
