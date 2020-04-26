---
layout: docs
title: Commands
---

# {{page.title}}
Commands are probably the most common way to interact with bots. AckCord comes 
with a built in commands framework. In use it resembles the API exposed 
by `EventsController` (It's probably more accurate to say the events 
controller resembles the commands controller). It's use looks something like 
this.

```scala mdoc:invisible
import ackcord._
import ackcord.data._
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import cats.Id
import cats.instances.future._

val clientSettings = ClientSettings("")
import clientSettings.executionContext
val client = Await.result(clientSettings.createClient(), Duration.Inf)
```
```scala mdoc:silent
import ackcord.commands._
import ackcord.syntax._
import akka.NotUsed

class MyCommands(requests: Requests) extends CommandController(requests) {
  val hello: NamedCommand[NotUsed] = Command
    .named("!", Seq("hello"), mustMention = true)
    .withRequest(m => m.textChannel.sendMessage(s"Hello ${m.user.username}"))
}
```

Use `named` to give the command a name. You can also name it later if you don't 
name it here. For the execution of the command itself, you have all the same 
options you had with the events controllers. For simple commands `withRequest`, 
which sends the return request of the command automatically, is probably enough.

Like with events we need to register our commands. We do this using an 
`CommandConnector`. You can find one at `DiscordClient#commands`.

```scala mdoc:silent
val myCommands = new MyCommands(client.requests)
client.commands.runNewNamedCommand(myCommands.hello)
```

There are many useful `CommandBuilder`s. Make sure to give them all a look.

```scala mdoc:invisible
clientSettings.system.terminate()
```
