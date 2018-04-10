# AckCord
*You do what you want, exactly how you want it.*

AckCord is a Scala library for Discord, using Akka. AckCord's focus is on letting choose the level of abstraction you want. Want to work with the raw events from the gateway? Or maybe you don't want to bother with any of the underlying implementation and technicalities. Works for that too. Only interested in the REST requests, then pull in that module and ignore the rest.

While AckCord is still in active development, you can try AckCord by adding some of these to your `build.sbt` file.
```scala
libraryDependencies += "net.katsstuff" %% "ackcord"                 % "0.10" //For high level API, includes all the other modules
libraryDependencies += "net.katsstuff" %% "ackcord-core"            % "0.10" //Low level core API
libraryDependencies += "net.katsstuff" %% "ackcord-commands-core"   % "0.10" //Low to mid level Commands API
libraryDependencies += "net.katsstuff" %% "ackcord-lavaplayer-core" % "0.10" //Low level lavaplayer API
```

# Usage
AckCord comes with two (3 if you count handling dispatch events yourself) major APIs. A high level API, and a how level one. Let's go over the shared concepts, and the high level API first.

To use the high level API of AckCord, you create the `ClientSettings` you want to use, and call build. This returns a future client. Using that client you can then listen for specific messages and events. Once you have set everything up, you call login.
```scala
//The settings contain your token, and other misc stuff like shard info, request settings, command settings, the actor system, and so on
val settings = ClientSettings(token)
settings.build().foreach { client =>
  client.onEvent {
    case APIMessage.Ready(_) => println("Now ready")
  }
  
  client.login()
}
```

## CacheSnapshot
All access to a cache in AckCord is represented as a `CacheSnapshot[F]` where F is some type constructor like `Future` or `Id`. Many methods in AckCord uses an implicit cache to let you access different stuff.

In anything above core (includes both the high and low level API), you might also sometimes be passed a `CacheState`, which represents the state of the cache before, and after the changes from that event were applied.

Often times when overriding methods, an implicit cache snapshot will already be present. In some cases when dealing with a function instead though, you may want to extract the cache snapshot as a different parameter to mark it implicit. That's where the `<thing>C` methods for the high level, and `CmdFlow` and `ParsedCmdFlow` comes in for the low level. Using these we can extract the current cache snapshot into a separate function, used like so for the high level API.
```scala
client.onEventC { implicit c => {
    case APIMessage.Ready(_) => println("Now ready")
  }
}
```

Ok course you could also create an instance of `EventHandler`, and register that instead.
```scala
client.registerHandler {
  new EventHandler[APIMessage.Ready, Unit] {
    override def handle[F[_]: Monad](message: APIMessage.Ready)(implicit c: CacheSnapshot[F]): Unit = 
      println("Now ready")
  }
}
```

## Making requests
What use is a Discord API if you can't make REST request? There are two parts to this, creating a request, and running it. Both have many ways to do things.

### Creating a request
First we need a request object to send. There are two main ways to get such a request object. The first one is to create `Request` objects with requests from the `http.rest` and `http.images` packages manually. The second is to import `net.katsstuff.ackcord.syntax._` and use the extensions methods provided by that. If you use the low level API, you can also specify a context object as part of the request, which you get back from the response. If you need some sort of order from your request, this is how you do it, as you do not always receive responses in the same order you sent the requests.

### Running the request
To run requests in AckCord you would either use a `RequestHelper`, or a `RequestDSL` object. 

`RequestHelper` is normally used in low level API where you want to model your behavior as a stream, although you can use it in the high level API too. Here you represent your requests as a `Source`, and the act of sending them as a `Flow` found in `RequestHelper`. For the high level API, you can find a `RequestHelper` in the client. For the low level APi you have to create it yourself at some point.

`RequestDSL` while more limited than `RequestHelper` allows you to model your behavior as a sequence of requests using monadic operations. When using `RequestDSL`, you also need something to run it. Either have to run it yourself using a flow gotten from `RequestHelper`, or some methods can run it for you. For the high level API, all methods that have DSL in their name expects a `RequestDSL`, and will run that when needed.

### Example

NOTE: This example assumes that the cache type is `Id`. Normally you want to write your code to any arbitrary cache type `F`. From here on, all examples will be written against any arbitrary cache type `F`.
```scala
client.onEventDSLC { implicit c => {
    case APIMessage.ChannelCreate(_) =>
      import RequestDSL._
      for {
        //optionPure lifts an Option into the dsl
        guildChannel <- optionPure(channel.asGuildChannel)
        guild        <- optionPure(guildChannel.guild)
        //maybeRequest lifts an optional request into the dsl. Normal requests don't need to be lifted
        msg          <- optionRequest(guild.tChannels.headOption.map(_.sendMessage(s"${guildChannel.name} was deleted")))
        //Creating a request without using the extra syntax
        _            <- CreateMessage.mkContent(msg.channelId, "Another message")
      } yield ()
  }
}
```

## The high level commands API
Commands in AckCord relies on the idea of most commands looking a certain way, that is `[mention] <prefix><command> [args...]`, where mention is a setting you toggle when using commands. What AckCord then does is to listen to all created messages, and convert the ones that look like commands into raw commands.

When you first construct your bot, you pass it a lot of settings. In these are the command settings. The important part I want to focus on here are the categories. The categories you pass in tells AckCord what it should watch out for when parsing commands. A categories are what decides which prefixes are valid. They also come with a description. 

For running code when a command is used, you can either just listen for the raw command events, or you can register a parsed command. Both will be shown below.
```scala
val GeneralCommands = CmdCategory("!", "General commands")
val settings = ClientSettings(token, commandSettings = CommandSettings(categories = Set(GeneralCommands)))
settings.build().foreach { client =>
  registerCommands(client)
  client.login()
}

def registerCommands[F[_]: Monad](commands: CommandsHelper[F]): Unit = {
  commands.onRawCommandDSLC { implicit c => {
      case RawCmd(message, GeneralCommands, "echo", args, _) =>
        import RequestDSL._
        for {
          //liftOptionT is used to lift values of OptionT[F, A] into the dsl
          channel <- liftOptionT(message.tGuildChannel)
          _       <- channel.sendMessage(s"ECHO: ${args.mkString(" ")}")
        } yield ()
    }
  }
  
  client.registerCommand(
    category = GeneralCommands,
    aliases = Seq("ping"),
    filters = Seq(CmdFilter.NonBot, CmdFilter.InGuild),
    description = Some(CmdDescription("Ping", "Check if the bot is alive"))
  ) { cmd: ParsedCmd[Int] =>
    println(s"Received ping command with arg ${cmd.args}")
  }
}
```

When you register a parsed command, you also pass in the aliases for the command, filters to use for the command, and a description for the command. Most of these are what they sound like, but filters might not be as familiar.

### CmdFilter
Often times you have a command that can only be used in a guild, or by those with a specific permission, or by non bots. For cases like those you can use a `CmdFilter`. The filter will stop the command before it arrives at your handler, and send an error message instead.

# The low level API
Until now we have covered topics that are used in either just the high level API, or both the high and low level API. Let's now look more closely on things which don't concern the high level API, starting with how to login with the low level API.

To use AckCord in with the low level API you need to get an instance of a Discord shard actor.
```scala
implicit val system: ActorSystem  = ActorSystem("AckCord")
implicit val mat:    Materializer = ActorMaterializer()
import system.dispatcher

val cache = Cache.create //The Cache keeps track of all the state, and provides a way to listen to changes in that state
val requests = RequestHelper(BotAuthentication(settings.token)) //The RequestHelper allows us to make REST requests to Discord

val settings = GatewaySettings(token)
DiscordShard.fetchWsGateway.foreach { wsUri =>
 val shard = DiscordShard.connect(wsUri, settings, cache, "DiscordShard")
 shard ! DiscordShard.StartShard
}
```

All the normal messages are published to a source found in the cache object. This source can be materialized as many times as needed. The cache itself gives you some convenience methods to subscribe to the source. 
```scala
cache.subscribeAPI.collect {
  case APIMessage.Ready(CacheState(c, _)) => doStuff()
}.to(Sink.ignore).run()
```

## Commands
If you want to work with commands from the low level API, you have to add a dependency on the commands module. I'd recommend you to at least skim over the high level commands API before reading this, as they share many concepts.

### The Commands object
Just like the job of the `Cache` object is to keep track of the current events, and the state of the application, it's the job of the `Commands` object to keep track of the current commands in the application. To get a `Commands` instance, call `CoreCommands.create`. From there you have access to a source of raw commands that can be materialized as many times as needed.
```scala
CoreCommands.create(needMention = true, Set(OurCategories.!), cache, requests)
```

### CmdFactory
Often times, working with the raw commands objects directly can be kind of tiresome and usually involves a lot of boilerplate. For that reason, Ackcord also provides several types of `CmdFactory` which will do the the job of selecting the right commands, possibly parsing it, running the code for the command, and more. You'll choose a different factory type depending on if your command parses the arguments it receives or not. The factory also supplies extra information about the command, like an optional description and filters. We'll go over each of these one by one.

#### Running the code
AckCord represents the code to run for a command as a Sink that can be connected to the command messages. While you can use the materialized value of running the sink, in most cases you probably only want use an ignoring sink as the last step of the pipeline.

### Other helpers
There are a few more helpers that you can use when writing commands. The first one is `CmdFlow[F]` and `ParsedCmdFlow[F, A]` as mentioned earlier, which helps you construct a flow with an implicit cache snapshot. The next is the `requestDSL` methods on the command factory objects, which lets you create factories that take a flow from a `Cmd[F]` or `ParsedCmd[F, A]` to a `RequestDSL[B]` instead.

### Putting it all together
So now that we know what all the different things to, let's create our factories.
```scala
//Normal unparsed command doing the request sending itself
def echoCmdFactory[F[_]: Monad: Streamable] = BaseCmdFactory(
  category = OurCategories.!,
  aliases = Seq("echo"),
  sink = requests =>
    CmdFlow[F]
      .flatMapConcat { implicit c => cmd =>
        Streamable[F].optionToSource(cmd.msg.tChannel.map(_.sendMessage(cmd.args.mkString(" "))))
      }
      .to(requests.sinkIgnore),
  description = Some(CmdDescription(name = "Echo", description = "Replies with the message sent to it"))
)

//Parsed command using RequestDSL
def getUsernameCmdFactory[F[_]: Monad: Streamable] = ParsedCmdFactory.requestDSL[User](
  category = OurCategories.!,
  aliases = Seq("getUsername"),
  flow = ParsedCmdFlow[F, User]
    .map { implicit c => cmd =>
      import RequestDSL._
      for {
        channel <- liftOptionT(cmd.msg.tChannel)
        _       <- channel.sendMessage(s"Username for user is: ${cmd.args.username}")
      } yield ()
    },
  description = Some(CmdDescription(name = "Get username", description = "Get the username of a user"))
)

commands.subscribe(echoCmdFactory)(Keep.left)
commands.subscribe(getUsernameCmdFactory)(Keep.left)
```

### Help command
AckCord also provides the basics for a help command if you want something like that in the form of the abstract actor `HelpCmd`. You use it by sending `HelpCmd.AddCmd` with the factory for the command, and the lifetime of the command. You get the lifetime of the command as a result of registering the command.

## Lavaplayer module
AckCord also provides a module to use Lavaplayer to send music to Discord. To do that, first create an instance of the `LavaplayerHandler` actor. This actor should be shared for an entire guild. Check the objects in the companion object of `LavaplayerHandler` for the valid messages you can send it.

# The many modules of AckCord
While the modules listed at the start of this readme are the main modules you want to depend on, AckCord has many more if you feel that you don't need the full package.

```scala
libraryDependencies += "net.katsstuff" %%% "ackcord-data"            % "0.10" //All the data objects like channel, message user and so on. Also exists for Scala.js
libraryDependencies += "net.katsstuff" %%  "ackcord-network"         % "0.10" //The base network module of AckCord
libraryDependencies += "net.katsstuff" %%  "ackcord-rest"            % "0.10" //All the rest requests in AckCord
libraryDependencies += "net.katsstuff" %%  "ackcord-image"           % "0.10" //All the image requests in AckCord
libraryDependencies += "net.katsstuff" %%  "ackcord-oatuh"           % "0.10" //A module for making OAuth calls
libraryDependencies += "net.katsstuff" %%  "ackcord-websockets"      % "0.10" //The base websockets module of AckCord
libraryDependencies += "net.katsstuff" %%  "ackcord-gateway"         % "0.10" //The gateway module of AckCord
libraryDependencies += "net.katsstuff" %%  "ackcord-voice"           % "0.10" //The voice module of AckCord
libraryDependencies += "net.katsstuff" %%  "ackcord-util"            % "0.10" //Misc utilities for AckCord that does not rely on the memory cache snapshot
libraryDependencies += "net.katsstuff" %%  "ackcord-commands"        % "0.10" //The base commands API module. Does not work without an implementation
libraryDependencies += "net.katsstuff" %%  "ackcord-lavaplayer"      % "0.10" //The base lavaplayer API module. Does not work without an implementation
libraryDependencies += "net.katsstuff" %%  "ackcord-core"            % "0.10" //Low level core API. Provides the in memory cache
libraryDependencies += "net.katsstuff" %%  "ackcord-commands-core"   % "0.10" //Provides an implementation for the commands module using core
libraryDependencies += "net.katsstuff" %%  "ackcord-lavaplayer-core" % "0.10" //Provides an implementation for the lavaplayer module using core
libraryDependencies += "net.katsstuff" %%  "ackcord"                 % "0.10" //For high level API, includes all the other modules
```

# More information
You can find more info in the examples, the wiki and the ScalaDoc.

Or you can just join the Discord server (we got cookies).

[![](https://discordapp.com/api/guilds/399373512072232961/embed.png?style=banner1)](https://discord.gg/fdKBnT) 
