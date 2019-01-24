---
layout: docs
title: High level API commands
---

# {{page.title}}
The high level AckCord API comes with built in command support. Before reading this, make sure you understand the shared command concepts.

In the high level API you can either just listen to the raw command events, or you can register a parsed command.

As before we create out client as usual, but with a small twist. This time we also pass in command settings to our client settings, which is where we specify our categories. We also import the commands package.
```tut:silent
import akka.NotUsed
import ackcord._
import ackcord.data._
import ackcord.syntax._
import ackcord.commands._
import cats.Monad
val token = "<token>"
val GeneralCommands = "!"
// IMPORTANT: We specify needsMention = true here. This is the default option.
// If you don't want to require a mention, turn this off.
val settings = ClientSettings(token, commandSettings = CommandSettings(prefixes = Set(GeneralCommands), needsMention = true))
import settings.executionContext

val futureClient = settings.createClient()
futureClient.foreach { client =>
  //client.login()
}
```

When you register a parsed command, you also pass in a refiner and a description for the command.
```tut
def registerParsedCommand[F[_]: Monad: Streamable](commands: CommandsHelper[F]): Unit = {
  commands.registerCmd[NotUsed, Id](
  	refiner = CmdInfo[F](prefix = GeneralCommands, aliases = Seq("ping"), filters = Seq(CmdFilter.NonBot, CmdFilter.InGuild)),
    description = Some(CmdDescription("Ping", "Check if the bot is alive"))
  ) { cmd: ParsedCmd[F, NotUsed] =>
    println(s"Received ping command")
  }
}

futureClient.foreach { client =>
  registerParsedCommand(client)
}
```

Notice the type `CommandsHelper[F]` there. So far we have been working with the main commands helper, the client object. The client object uses the command settings we passed to it when building the client. We can also make other `CommandsHelper[F]`. Let's see how, in addition to see how raw commands are done.

```tut
val TestCommands = "?"
def registerRawCommand[F[_]: Monad: Streamable](client: DiscordClient[F], commands: CommandsHelper[F]): Unit = {
  commands.onRawCmd {
    client.withCache[SourceRequest, RawCmd[F]] { implicit c => {
        case RawCmd(message, TestCommands, "echo", args, _) =>
          import client.sourceRequesterRunner._
          for {
            channel <- liftOptionT(message.tGuildChannel[F])
            _       <- run(channel.sendMessage(s"ECHO: ${args.mkString(" ")}"))
          } yield ()
        case _ => client.sourceRequesterRunner.unit
      }
    }
  }
}

```

Here we create the new commands helper. Note that `?` is the only valid prefix here. `!` would not be valid.
```tut
futureClient.foreach { client =>
  val (shutdown, newHelper) = client.newCommandsHelper(CommandSettings(prefixes = Set(TestCommands), needsMention = true))
  registerRawCommand(client, newHelper)
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