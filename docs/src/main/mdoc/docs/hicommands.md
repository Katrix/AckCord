---
layout: docs
title: High level API commands
---

# {{page.title}}
The high level AckCord API comes with built in command support. Before reading this, make sure you understand the shared command concepts.

In the high level API you can either just listen to the raw command events, or you can register a parsed command.

As before we create out client as usual, but with a small twist. This time we also pass in command settings to our client settings, which is where we specify our categories. We also import the commands package.
```scala mdoc:silent
import akka.NotUsed
import ackcord._
import ackcord.syntax._
import ackcord.commands._
import cats.Id
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
```scala mdoc
def registerParsedCommand(commands: CommandsHelper): Unit = {
  commands.registerCmd[NotUsed, Id](
  	refiner = CmdInfo(prefix = GeneralCommands, aliases = Seq("ping"), filters = Seq(CmdFilter.NonBot, CmdFilter.InGuild)),
    description = Some(CmdDescription("Ping", "Check if the bot is alive"))
  ) { cmd: ParsedCmd[NotUsed] =>
    println(s"Received ping command")
  }
}

futureClient.foreach { client =>
  registerParsedCommand(client)
}
```

Notice the type `CommandsHelper` there. So far we have been working with the main commands helper, the client object. The client object uses the command settings we passed to it when building the client. We can also make other `CommandsHelper`. Let's see how, in addition to see how raw commands are done.

```scala mdoc
val TestCommands = "?"
def registerRawCommand(client: DiscordClient, commands: CommandsHelper): Unit = {
  commands.onRawCmd {
    client.withCache[SourceRequest, RawCmd] { implicit c => {
        case RawCmd(message, TestCommands, "echo", args, _) =>
          import client.sourceRequesterRunner._
          for {
            channel <- optionPure(message.tGuildChannel)
            _       <- run(channel.sendMessage(s"ECHO: ${args.mkString(" ")}"))
          } yield ()
        case _ => client.sourceRequesterRunner.unit
      }
    }
  }
}

```

Here we create the new commands helper. Note that `?` is the only valid prefix here. `!` would not be valid.
```scala mdoc
futureClient.foreach { client =>
  val (shutdown, newHelper) = client.newCommandsHelper(CommandSettings(prefixes = Set(TestCommands), needsMention = true))
  registerRawCommand(client, newHelper)
}
```

```scala mdoc:invisible
settings.system.terminate()
```

## Access to the low level API
Accessing the low level API from the high level commands API is as simple as getting the `Commands` instance that is backing the `CommandsHelper` instance is as simple as calling the `commands` method on a `CommandsHelper`.
```scala mdoc
futureClient.foreach { client =>
  val commands: Commands = client.commands
}
```