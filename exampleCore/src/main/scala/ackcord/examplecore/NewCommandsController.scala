package ackcord.examplecore

import java.time.temporal.ChronoUnit

import scala.concurrent.Future

import ackcord._
import ackcord.syntax._
import ackcord.newcommands._
import ackcord.data.Permission
import ackcord.requests.CreateMessage
import akka.stream.scaladsl.Flow
import cats.syntax.all._

class NewCommandsController(requests: RequestHelper) extends CommandController(requests) {

  val hello: NamedCommand[List[String]] = Command
    .named("%", Seq("hello"), mustMention = true)
    .withRequest { implicit m =>
      m.tChannel.sendMessage(s"Hello ${m.user.username}")
    }

  val copy: NamedCommand[Int] =
    Command.named("%", Seq("copy"), mustMention = true).parsing[Int].withRequestOpt { implicit m =>
      m.message.tGuildChannel.map(_.sendMessage(s"You said ${m.parsed}"))
    }

  val guildInfo: NamedCommand[List[String]] =
    GuildCommand.named("%", Seq("guildInfo"), mustMention = true).withRequest { implicit m =>
      val guildName   = m.guild.name
      val channelName = m.tChannel.name
      val userNick    = m.guildMember.nick.getOrElse(m.user.username)

      m.tChannel.sendMessage(
        s"This guild is named $guildName, the channel is named $channelName and you are called $userNick"
      )
    }

  val parsingNumbers: NamedCommand[(Int, Int)] =
    Command
      .named("%", Seq("parseNum"), mustMention = true)
      .parsing((MessageParser[Int], MessageParser[Int]).tupled)
      .withRequestOpt { implicit m =>
        m.message.tGuildChannel.map(_.sendMessage(s"Arg 1: ${m.parsed._1}, Arg 2: ${m.parsed._2}"))
      }

  private val ElevatedCommand: CommandBuilder[GuildUserCommandMessage, List[String]] =
    GuildCommand.andThen(CommandBuilder.needPermission[GuildUserCommandMessage](Permission.Administrator))

  val adminsOnly: NamedCommand[List[String]] =
    ElevatedCommand.named("%", Seq("adminOnly"), mustMention = true).withSideEffects { _ =>
      println("Command executed by an admin")
    }

  val timeDiff: NamedCommand[List[String]] =
    Command.named("%", Seq("timeDiff"), mustMention = true).async[SourceRequest] { implicit m =>
      import requestRunner._
      for {
        channel <- optionPure(m.message.channelId.tResolve)
        sentMsg <- run(channel.sendMessage("Msg"))
        _ <- {
          val time = ChronoUnit.MILLIS.between(m.message.timestamp, sentMsg.timestamp)
          run(channel.sendMessage(s"$time ms between command and response"))
        }
      } yield ()
    }

  val ping: NamedCommand[List[String]] = Command.named("%", Seq("ping"), mustMention = true).streamed {
    Flow[CommandMessage[List[String]]]
      .map(m => CreateMessage.mkContent(m.message.channelId, "Pong"))
      .to(requests.sinkIgnore)
  }

  val timeDiff2: NamedCommand[List[String]] =
    Command.named("%", Seq("timeDiff2"), mustMention = true).async[Future] { implicit m =>
      //The ExecutionContext is provided by the controller
      for {
        answer  <- requests.singleFuture(m.tChannel.sendMessage("Msg"))
        sentMsg <- Future.fromTry(answer.eitherData.toTry)
        time = ChronoUnit.MILLIS.between(m.message.timestamp, sentMsg.timestamp)
        _ <- requests.singleFuture(m.tChannel.sendMessage(s"$time ms between command and response"))
      } yield ()
    }
}
