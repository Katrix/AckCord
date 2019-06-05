---
layout: docs
title: New incubating commands
---

# {{page.title}}
**Notice: This feature is considered experimental at this time, and may change drastically or be dropped altogether.**

Aside from the low and high level command APIs, AckCord also comes with a third much more experimental commands API. The goal of this commands API is to be as easy to use as the high level API, while being as powerful as the low level API. The API is very closely related to Play's action builders. If you are familiar with them, you'll feel right at home.

As before we create out client as usual. Note the extra import for the commands package. At the time of this writing, you need to add an explicit dependency on the new api. You will also have to fiddle a tiny bit with streams to use the new API from the high level API.
```tut:silent
import ackcord._
import ackcord.data._
import ackcord.syntax._
import ackcord.newcommands._
import cats.Monad
import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.{Flow, Sink, Keep}

implicit val system: ActorSystem  = ActorSystem("AckCord")
implicit val mat: Materializer = ActorMaterializer()
import system.dispatcher

val token = "<token>"
val cache = Cache.create
val requests = RequestHelper.create(BotAuthentication(token))

val gatewaySettings = GatewaySettings(token)
DiscordShard.fetchWsGateway.foreach { wsUri =>
 val shard = DiscordShard.connect(wsUri, gatewaySettings, cache, "DiscordShard")
 //shard ! DiscordShard.StartShard
}
```

## The Command connector
Just like the low level commands API has the `Commands` object, the new API has the `CommandConnector` object. It serves most of the same purposes as the `Commands` object, with the exception that the connector does not do any parsing to figure out what "looks like" a command. To create it we need to pass is a `RequestHelper` and a source of eligible messages that can be commands. In most cases this is just any message sent to any guild.

We can create the connector like so
```tut
val connector = new CommandConnector(
  cache.subscribeAPI.collectType[APIMessage.MessageCreate].map(m => m.message -> m.cache.current), 
  requests
)
``` 

## The CommandController
Now that we have our connector, we need some commands. The easiest way to create commands is to create them in a `CommandController`. The controller just serves as a collection of commands, and makes creating them easier. 

Let's create three commands. One command that doesn't care about arguments, one that checks the guild, and one that takes an int.
```tut:silent
class OurController(requests: RequestHelper) extends CommandController[Id](requests) {
  
  //The command builder includes a few convenience functions for common things.
  //withRequest is one of those, that sends the request at the end of the command.
  val ping = Command.withRequest { implicit m =>
    //Most functions on Command takes a function from some CommandMessage object (the implicit m above) to something else
    //The CommandMessage contains lots of usefull stuff
    m.tChannel.sendMessage("Pong")
  }
  
  //Here we're composing a command function with out builder. This lets us 
  //include more information in the command message, 
  //filter out valid commands (like we're doing here), or many other things.
  val memberCount = Command.andThen(CommandFunction.onlyInGuild).withRequest { implicit m =>
    val guildChannel = m.tChannel.asTGuildChannel.get
    val guild = guildChannel.guild.value.get //The implicit command message provides the cache snapshot
    guildChannel.sendMessage(s"Member count for guild is ${guild.memberCount}")
  }
  
  //By default the command just gives us back all the strings passed to it. We 
  //can change this behavior and parse a specific type with the parsing command.
  val intPrinter = Command.parsing[Int].withRequest { implicit m =>
    m.tChannel.sendMessage(s"You sent ${m.parsed}")
  }
}
```

### Wait, where's the information about how to use the command, like name and prefix?
At the moment (this might very well change in the future, remember, still experimental), this is information you pass the along at the same time as registering the command. Even that's not entirely accurate. The new API doesn't really have a concept of a name or a symbol before the name when using the command. All it knows about is what's called the prefix parser. I'll get back to that shortly.

## Running the command
Now that we have our controller, and the connector, let's connect stuff. When connecting the commands, you need to give it a prefix parser. Calling `CommandConnector#prefix` will suffice for most cases. This takes a prefix symbol, a list of aliases, and if the command needs a mention or not.

Let's connect our commands.
```tut
val controller = new OurController(requests)
connector.runNewCommand(connector.prefix("!", Seq("ping"), true), controller.ping)
connector.runNewCommand(connector.prefix("!", Seq("memberCount"), true), controller.memberCount)
connector.runNewCommand(connector.prefix("!", Seq("intPrinter"), true), controller.intPrinter)
```

### The prefix parser (advanced)
Ok, so we constructed a prefix parser using the method on the connector, but what is it exactly? In simple terms it's a function `(CacheSnapshot[F], Message) => F[MessageParser[Unit]]`. Given the current cache snapshot, and the message for a command, it returns a `MessageParser` that will consume some of the content of a message. If it succeeds in consuming that input, it will let the command execute using the remaining content not consumed by the prefix parser. This way you can control exactly how you want your commands to be parsed. The default parsing is `<mention if specifiec> <symbol><one of aliases>`.

```tut:invisible
system.terminate()
```
