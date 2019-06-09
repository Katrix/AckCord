package ackcord.examplecore

import java.time.temporal.ChronoUnit

import ackcord._
import ackcord.syntax._
import ackcord.newcommands._
import ackcord.data.Permission
import ackcord.requests.CreateMessage
import akka.stream.scaladsl.Flow
import cats.syntax.all._

class NewCommandsController(requests: RequestHelper) extends CommandController[Id](requests) {

  val hello: Command[List[String]] = Command.withRequest { implicit m =>
    m.tChannel.sendMessage(s"Hello ${m.user.username}")
  }

  val copy: Command[Int] = Command.parsing[Int].withRequestOpt { implicit m =>
    m.message.tGuildChannel.value.map(_.sendMessage(s"You said ${m.parsed}"))
  }

  val guildInfo: Command[List[String]] = GuildCommand.withRequest { implicit m =>
    val guildName   = m.guild.name
    val channelName = m.tChannel.name
    val userNick    = m.guildMember.nick.getOrElse(m.user.username)

    m.tChannel.sendMessage(
      s"This guild is named $guildName, the channel is named $channelName and you are called $userNick"
    )
  }

  val parsingNumbers: Command[(Int, Int)] =
    Command.parsing((MessageParser[Int], MessageParser[Int]).tupled).asyncOptRequest { implicit m =>
      m.message.tGuildChannel.map(_.sendMessage(s"Arg 1: ${m.parsed._1}, Arg 2: ${m.parsed._2}"))
    }

  private val ElevatedCommand: CommandBuilder[GuildUserCommandMessage, List[String]] =
    GuildCommand.andThen(CommandFunction.needPermission[GuildUserCommandMessage](Permission.Administrator))

  val adminsOnly: Command[List[String]] = ElevatedCommand.withSideEffects { _ =>
    println("Command executed by an admin")
  }

  val timeDiff: Command[List[String]] = Command.async[SourceRequest] { implicit m =>
    import requestRunner._
    for {
      channel <- liftOptionT(m.message.channelId.tResolve)
      sentMsg <- run(channel.sendMessage("Msg"))
      _ <- {
        val time = ChronoUnit.MILLIS.between(m.message.timestamp, sentMsg.timestamp)
        run(channel.sendMessage(s"$time ms between command and response"))
      }
    } yield ()
  }

  val ping: Command[List[String]] = Command.streamed {
    Flow[CommandMessage[List[String]]]
      .map(m => CreateMessage.mkContent(m.message.channelId, "Pong"))
      .to(requests.sinkIgnore)
  }
}
