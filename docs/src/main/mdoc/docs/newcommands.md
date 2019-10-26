---
layout: docs
title: New experimental commands
---

# {{page.title}}
**Notice: This feature is considered experimental at this time, and may change drastically or be dropped altogether.**

To use the new commands API, add this to your build file.
```scala
libraryDependencies += "net.katsstuff" %% "ackcord-commands-new" % "{{versions.ackcord}}"
```

Aside from the low and high level command APIs, AckCord also comes with a third much more experimental commands API. The goal of this commands API is to be as easy to use as the high level API, while being as powerful as the low level API. The API is very closely related to Play's action builders. If you are familiar with them, you'll feel right at home.

As before we create out client as usual. Note the extra import for the commands package. At the time of this writing, you need to add an explicit dependency on the new api. You will also have to fiddle a tiny bit with streams to use the new API from the high level API.
```scala mdoc:silent
import ackcord._
import ackcord.data._
import ackcord.syntax._
import ackcord.newcommands._
import ackcord.requests.Ratelimiter
import cats.~>
import akka.NotUsed
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import java.time.temporal.ChronoUnit
import scala.concurrent.Future

implicit val system: ActorSystem[Nothing]  = ActorSystem(Behaviors.ignore, "AckCord")
import system.executionContext

val token = "<token>"
val cache = Cache.create
val ratelimiter = system.systemActorOf(Ratelimiter(), "Ratelimiter")
val requests = new RequestHelper(BotAuthentication(token), ratelimiter)

val gatewaySettings = GatewaySettings(token)
DiscordShard.fetchWsGateway.foreach { wsUri =>
 val shard = system.systemActorOf(DiscordShard(wsUri, gatewaySettings, cache), "DiscordShard")
 //shard ! DiscordShard.StartShard
}
```

## The Command connector
Just like the low level commands API has the `Commands` object, the new API has the `CommandConnector` object. It serves most of the same purposes as the `Commands` object, with the exception that the connector does not do any parsing to figure out what "looks like" a command. To create it we need to pass is a `RequestHelper` and a source of eligible messages that can be commands. In most cases this is just any message sent to any guild.

We can create the connector like so
```scala mdoc:silent
val connector = new CommandConnector(
  cache.subscribeAPI.collectType[APIMessage.MessageCreate].map(m => m.message -> m.cache.current), 
  requests
)
``` 

## The CommandController
Now that we have our connector, we need some commands. The easiest way to create commands is to create them in a `CommandController`. The controller just serves as a collection of commands, and makes creating them easier. 

Let's start with creating two basic commands. One command that doesn't care about arguments and one that takes an int.
```scala mdoc:silent
class OurController(requests: RequestHelper) extends CommandController(requests) {
  
  //The command builder includes a few convenience functions for common things.
  //withRequest is one of those, that sends the request at the end of the command.
  val ping = Command.withRequest { implicit m =>
    //Most functions on Command takes a function from some CommandMessage object (the implicit m above) to something else
    //The CommandMessage contains lots of usefull stuff
    m.tChannel.sendMessage("Pong")
  }
  
  //By default the command just gives us back all the strings passed to it. We 
  //can change this behavior and parse a specific type with the parsing command.
  //Here we're also using the side effects method to do arbitrary work inside the execution of the command
  val intPrinter = Command.parsing[Int].withSideEffects { implicit m =>
    println(s"You sent ${m.parsed}")
  }
}
```

### Wait, where's the information about how to use the command, like name and prefix?
At the moment (this might very well change in the future, remember, still experimental), this is information you pass the along at the same time as registering the command. Even that's not entirely accurate. The new API doesn't really have a concept of a name or a symbol before the name when using the command. All it knows about is what's called the prefix parser. I'll get back to that shortly.

## Running the command
Now that we have our controller, and the connector, let's connect stuff. When connecting the commands, you need to give it a prefix parser. Calling `CommandConnector#prefix` will suffice for most cases. This takes a prefix symbol, a list of aliases, and if the command needs a mention or not.

Let's connect our commands.
```scala mdoc:silent
val controller = new OurController(requests)
connector.runNewCommand(connector.prefix("!", Seq("ping"), true), controller.ping)
connector.runNewCommand(connector.prefix("!", Seq("intPrinter"), true), controller.intPrinter)
```

### The prefix parser (advanced)
Ok, so we constructed a prefix parser using the method on the connector, but what is it exactly? In simple terms it's a function `(CacheSnapshot[F], Message) => F[MessageParser[Unit]]`. Given the current cache snapshot, and the message for a command, it returns a `MessageParser` that will consume some of the content of a message. If it succeeds in consuming that input, it will let the command execute using the remaining content not consumed by the prefix parser. This way you can control exactly how you want your commands to be parsed. The default parsing is `<mention if specifiec> <symbol><one of aliases>`.

## More information in the command
Ok, so we created our command, and can use them. However, so far they're not that much more useful than the old commands (other than getting rid of a bit of the boilerplate), where the new system really shines is composing command builders (the value called `Command` that we used to create the commands) that do different things, or hold different amount of data.

Let's create two new commands that interact with the guild they are used in. (I won't register them here, you already know how that works).
```scala mdoc:silent
class GuildCommandsController(requests: RequestHelper) extends CommandController(requests) {

  //Here we're composing a command function with out builder. This lets us 
  //include more information in the command message, 
  //filter out valid commands (like we're doing here), or many other things.
  //
  //The default command builder (Command) already blocks bot accounts, and 
  //therefore also includes the user that used the command. To carry along that 
  //information we need we must create a natural transformation from the previous
  //command message type, to our new type.
  val OurGuildCommand = Command.andThen(CommandBuilder.onlyInGuild { (chG, g) =>
      Lambda[UserCommandMessage ~> GuildUserCommandMessage](m => GuildCommandMessage.WithUser(chG, g, m.user, m))
    })
  
  //We can now use our new command builder like normal
  val memberCount = OurGuildCommand.withRequest { implicit m =>
    val guildChannel = m.tChannel //Now a guild channel
    val guild = m.guild
    val guildOwner = guild.owner //The implicit command message provides the cache snapshot
    guildChannel.sendMessage(
      s"Member count for guild is ${guild.memberCount} and owner is ${guildOwner.fold("Unknown")(_.username)}"
    )
  }
  
  //AckCord also comes with a few extra command builders out of the box that 
  //you would probably recreate anyway. One of these is GuildCommand that 
  //contains the user, guild, guild channel and guild member
  val myName = GuildCommand.withRequest { implicit m =>
    val name = m.guildMember.nick.getOrElse(m.user.username)
    m.tChannel.sendMessage(s"You name is $name")
  }
}
```

## Async stuff in commands
So far you've seen how to send a single request with commands, and how to do side effects in commands, but how do you do arbitrary async stuff in a command? That's where the `async` method on the command builder comes in. It let's your command execution return a type `F[Unit]` as long as F is streamable.

Let's see an example with `Future`.
```scala mdoc:silent
class AsyncCommandsController(requests: RequestHelper) extends CommandController(requests) {

  val timeDiff: Command[NotUsed] = Command.async[Future] { implicit m =>
    //The ExecutionContext is provided by the controller
    for {
      answer  <- requests.singleFuture(m.tChannel.sendMessage("Msg"))
      sentMsg <- Future.fromTry(answer.eitherData.toTry)
      time = ChronoUnit.MILLIS.between(m.message.timestamp, sentMsg.timestamp)
      _ <- requests.singleFuture(m.tChannel.sendMessage(s"$time ms between command and response"))
    } yield ()
  }
}
```


```scala mdoc:invisible
system.terminate()
```
