---
layout: docs
title: Slash Commands
---

# {{page.title}}
Slash Commands are the new way of interacting with Discord bots. AckCord comes
with a built-in slash commands framework.

```scala mdoc:invisible
import ackcord._
import ackcord.data._
import scala.concurrent.Await
import scala.concurrent.duration.Duration

val clientSettings = ClientSettings("")
val client = Await.result(clientSettings.createClient(), Duration.Inf)
```

## Commands
The simplest command you can make is a `/ping` command.
```scala mdoc:silent
import ackcord.interactions._
import ackcord.interactions.commands._

class MyPingCommands(requests: Requests) extends CacheApplicationCommandController(requests) {

  val pongCommand = SlashCommand.command("ping", "Check if the bot is alive") { _ =>
    sendMessage("Pong")
  }
}
```

### Arguments
```scala mdoc:silent
class MyParameterCommands(requests: Requests) extends CacheApplicationCommandController(requests) {

  // Single argument
  val echoCommand = SlashCommand
    .withParams(string("message", "The message to send back"))
    .command("echo", "Echoes a message you send") { implicit i =>
      sendMessage(s"ECHO: ${i.args}")
    }

  // Multiple arguments
  val multiArgsCommand = SlashCommand
    .withParams(string("message", "The message to send back") ~ string("intro", "The start of the message"))
    .command("echoWithPrefix", "Echoes a message you send") { implicit i =>
      sendMessage(s"${i.args._1}: ${i.args._2}")
    }

  // Optional arguments
  val optArgsCommand = SlashCommand
    .withParams(string("message", "The message to send back").notRequired)
    .command("echoOptional", "Echoes an optional message you send") { implicit i =>
      sendMessage(s"ECHO: ${i.args.getOrElse("No message")}")
    }
}
```

### Autocomplete
Discord allows you to autocomplete arguments to allow the user to enter better arguments.

This command will bring a small menu up with suggestions for the arguments, it will show 3 options, each a differently multiplied version of the number you have initially typed.
```scala mdoc:silent
class MyAutocompleteCommands(requests: Requests) extends CacheApplicationCommandController(requests) {

  val autocompleteCommand = SlashCommand
    .withParams(string("auto", "An autocomplete parameter").withAutocomplete(s => Seq(s * 2, s * 3, s * 4)))
    .command("simple-autocomplete", "A simple autocomplete command") { i =>
      sendMessage(s"Res: ${i.args}")
    }
}
```

### Async
Async commands allow you to inform the user there will be a slight delay before the bot responds, useful for commands that fetch external resources or do a lot of work.
```scala mdoc:silent
class MyAsyncCommands(requests: Requests) extends CacheApplicationCommandController(requests) {

  // This command will very quickly show a loading message in discord.
  val asyncCommand = SlashCommand.command("async", "An async test command") { implicit i =>
    async(implicit token => sendAsyncMessage("Async message"))
  }
}
```
This will edit the message you've sent, after it has been received in Discord but will not show the user a loading message.
```scala mdoc:silent
class MyAsyncEditCommands(requests: Requests) extends CacheApplicationCommandController(requests) {

  // This command will show a loading message in discord, send a message and edit the original message (which would mark the end of the async interaction).
  val asyncEditCommand = SlashCommand
    .withParams(string("par1", "The first parameter") ~ string("par2", "The second parameter"))
    .command("asyncEdit", "An async edit test command") { implicit i =>
      sendMessage("An instant message").doAsync { implicit token =>
        editOriginalMessage(content = JsonSome("An instant message (with an edit)"))
      }
    }
}
```

## Command groups
Discord allow you to group commands together, this is useful for when you have a lot of commands that are similar.

This example will register the commands: `/group foo` and `/group bar`
```scala mdoc:silent
class MyGroupedCommands(requests: Requests) extends CacheApplicationCommandController(requests) {

  val groupCommand = SlashCommand.group("group", "Group test")(
    SlashCommand.command("foo", "Sends foo")(_ => sendMessage("Foo")),
    SlashCommand.command("bar", "Sends bar")(_ => sendMessage("Bar"))
  )
}
```

## Registering commands with Discord
You need to register slash commands with discord for them to appear, it can be done like this.

Commands that are globally registered can take up to an hour to propagate to all servers so using guild commands in development is recommended.
```scala mdoc:silent
val myPingCommands = new MyPingCommands(client.requests)
val myParameterCommands = new MyParameterCommands(client.requests)

val myAutocompleteCommands = new MyAutocompleteCommands(client.requests)
val myAsyncCommands = new MyAsyncCommands(client.requests)
val myAsyncEditCommands = new MyAsyncEditCommands(client.requests)

client.onEventSideEffectsIgnore {
  case msg: APIMessage.Ready =>
    
    // Create the commands globally in all discords.
    InteractionsRegistrar.createGlobalCommands(
      msg.applicationId, // Client ID
      client.requests,
      replaceAll = true, // Boolean whether to replace all existing
      // CreatedGuildCommand*
      myPingCommands.pongCommand,
      myParameterCommands.echoCommand,
      myParameterCommands.multiArgsCommand,
      myParameterCommands.optArgsCommand
    )
    
    val myGuildId = GuildId("<some id here>")
    
    // Create the commands in a specific discord.
    InteractionsRegistrar.createGuildCommands(
      msg.applicationId, // Client ID
      myGuildId, // Guild ID
      client.requests,
      replaceAll = true, // Boolean whether to replace all existing
      // CreatedGuildCommand*
      myAutocompleteCommands.autocompleteCommand,
      myAsyncCommands.asyncCommand,
      myAsyncEditCommands.asyncEditCommand
    )
}
```

## Registering commands with Ackcord
When you have created all your slash commands and registered them with discord you can register them with ackcord.
```scala mdoc:silent
client.onEventSideEffectsIgnore { case msg: APIMessage.Ready =>
  client.runGatewayCommands(msg.applicationId.asString)(
    myParameterCommands.echoCommand,
    myParameterCommands.multiArgsCommand,
    myParameterCommands.optArgsCommand,
    myAutocompleteCommands.autocompleteCommand,
    myAsyncCommands.asyncCommand,
    myAsyncEditCommands.asyncEditCommand
  )
}
```

```scala mdoc:invisible
clientSettings.system.terminate()
```