---
layout: docs
title: High level API commands
---

# {{page.title}}

The high level AckCord API comes with built in command support. Before reading this, make sure you understand the shared command concepts.

In the high level API you can either just listen to the raw command events, or you can register a parsed command.

As before we create out client as usual, but with a small twist. This time we also pass in command settings to our client settings, which is where we specify our categories. We also import the commands package.
```tut
import net.katsstuff.ackcord._
import net.katsstuff.ackcord.data._
import net.katsstuff.ackcord.syntax._
import net.katsstuff.ackcord.commands._
import cats.{Monad, Id}
val token = "<token>"
val GeneralCommands = CmdCategory("!", "General commands")
val settings = ClientSettings(token, commandSettings = CommandSettings(categories = Set(GeneralCommands)))
import settings.executionContext

val futureClient = settings.build()
futureClient.foreach { client =>
  //client.login()
}
```

When you register a parsed command, you also pass in the aliases for the command, filters to use for the command, and a description for the command.
```tut
def registerParsedCommand[F[_]: Monad: Streamable](commands: CommandsHelper[F]): Unit = {
  commands.registerCommand(
    category = GeneralCommands,
    aliases = Seq("ping"),
    filters = Seq(CmdFilter.NonBot, CmdFilter.InGuild),
    description = Some(CmdDescription("Ping", "Check if the bot is alive"))
  ) { cmd: ParsedCmd[F, Int] =>
    println(s"Received ping command with arg ${cmd.args}")
  }
}

futureClient.foreach { client =>
  registerParsedCommand(client)
}
```

Notice the type `CommandsHelper[F]` there. So far we have been working with the main commands helper, the client object. The client object uses the command settings we passed to it when building the client. We can also make other `CommandsHelper[F]`s though. Let's see how, in addition to see how raw commands are done.

```tut
val TestCommands = CmdCategory("?", "Test commands")
def registerRawCommand[F[_]: Monad: Streamable](commands: CommandsHelper[F]): Unit = {
  commands.onRawCommandDSLC { implicit c => {
      case RawCmd(message, TestCommands, "echo", args, _) =>
        import RequestDSL._
        for {
          channel <- liftOptionT(message.tGuildChannel[F])
          _       <- channel.sendMessage(s"ECHO: ${args.mkString(" ")}")
        } yield ()
    }
  }
}

futureClient.foreach { client =>
  val (shutdown, newHelper) = client.newCommandsHelper(CommandSettings(categories = Set(TestCommands)))
  registerRawCommand(newHelper)
}
```

```tut:invisible
settings.system.terminate()
```

## Access to the low level API
Accessing the low level API from the high level commands API is as simple as getting the `Commands[F]` instance that is backing the `CommandsHelper[F]` instance is as simple as calling the `commands` method on a `CommandsHelper[F]`.
```tut
futureClient.foreach { client =>
  val commands: Commands[Id] = client.commands
}
```