# AckCord
*You do what you want, exactly how you want it.*

AckCord is a Scala library for Discord, using Akka. While it does give you a high level access to things like many other libraries, AckCord also exposes the underlying low level aspects, allowing you to write efficient code also in more complex cases. Want to use a different cache, or none at all, sure, just don't wire up that part. Want to only do REST requests in for example an OAuth application, easy.

While AckCord is still in active development, you can try AckCord by adding some of these to your `build.sbt` file.
```scala
libraryDependencies += "net.katsstuff" %% "ackcord"            % "0.8" //For high level API, includes all the other modules
libraryDependencies += "net.katsstuff" %% "ackcord-core"       % "0.8" //Low level core API
libraryDependencies += "net.katsstuff" %% "ackcord-commands"   % "0.8" //Low level commands API
libraryDependencies += "net.katsstuff" %% "ackcord-lavaplayer" % "0.8" //Low level lavaplayer API
```

# Usage
AckCord comes with both a high level API, and a how level one. Let's go over the shared concepts, and the high level API first.

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

## CacheSnapshot and CacheState
All the state in AckCord is stored in the `CacheState`, which is passed as a part of all API messages to you. `CacheState` holds two snapshots(`CacheSnapshot`) of the cache. One before the message was processed, and one after. Many methods in AckCord uses an implicit snapshot though, so it would be nice to more easily get access to it. That's where the `<thing>C` methods for the high level, and `CmdFlow` and `ParsedCmdFlow` comes in for the low level. Using these we can extract the current cache into a separate function, used like so for the high level API.
```scala
client.onEventC { implicit c => {
    case APIMessage.Ready(_) => println("Now ready")
  }
}
```

## Making requests
What use is a Discord API if you can't make REST request? There are two parts to this, creating a request, and running it. Both have many ways to do things.

### Creating a request
First we need a request object to send. There are two main ways to get such a request object. The first one is to create `Request` objects with requests from the `RESTRequests` and `ImageRequest` object manually. The second is to import `net.katsstuff.ackcord.syntax._` and use the extensions methods provided by that. If you use the low level API, you can also specify a context object as part of the request, which you get back from the response. If you need some sort of order from your request, this is how you do it, as you do not always receive responses in the same order you sent the requests.

### Running the request
To run requests in AckCord you would either use a `RequestHelper`, or a `RequestDSL` object. 

`RequestHelper` is normally used in low level API where you want to model your behavior as a stream, although you can use it in the high level API too. Here you represent your requests as a `Source`, and the act of sending them as a `Flow` found in `RequestHelper`. For the high level API, you can find it in the client. For the low level APi you have to create it yourself at some point.

`RequestDSL` while more limited than `RequestHelper` allows you to model your behavior as a sequence of requests using monadic operations. When using `RequestDSL`, you also need something to run it. Either have to run it yourself using a flow gotten from `RequestHelper`, or some methods can run it for you. For the high level API, all methods that have DSL in their name expects a `RequestDSL`, and will run that when needed.

### Example
```scala
client.onEventDSLC { implicit c => {
    case APIMessage.ChannelCreate(_) =>
      for {
        //maybePure lifts an Option into the dsl
        guildChannel <- maybePure(channel.asGuildChannel)
        guild        <- maybePure(guildChannel.guild)
        //maybeRequest lifts an optional request into the dsl. Normal requests don't need to be lifted
        msg          <- maybeRequest(guild.tChannels.headOption.map(_.sendMessage(s"${guildChannel.name} was deleted")))
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
  client.onRawCommandDSLC { implicit c => {
      case RawCmd(message, GeneralCommands, "echo", args, _) =>
        for {
          channel <- maybePure(message.tGuildChannel)
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
  
  client.login()
}
```

When you register a parsed command, you also pass in the aliases for the command, filters to use for the command, and a description for the command. Most of these are what they sound like, but filters might not be as familiar.

### CmdFilter
Often times you have a command that can only be used in a guild, or with those with a specific permission, or by non bots. For cases like those you can use a `CmdFilter`. The filter will stop the command before it arrives at your handler, and send an error message in return.

# The low level API
Until now we have covered topics that are used in either just the high level API, or both the high and low level API. Let's now look more closely on things which don't concern the high level API, starting with how to login with the low level API.

To use AckCord in with the low level API you need to get an instance of a Discord shard actor.
```scala
implicit val system: ActorSystem  = ActorSystem("AckCord")
implicit val mat:    Materializer = ActorMaterializer()
import system.dispatcher

val cache = Cache.create //The Cache keeps track of all the state, and provides a way to listen to changes in that state
val requests = RequestHelper(BotAuthentication(settings.token)) //The RequestHelper allows us to make REST requests to Discord

val settings = CoreClientSettings(token)
DiscordClient.fetchWsGateway.map(settings.connect(_, cache)).foreach { shard =>
 shard ! DiscordShard.StartShard
}
```

All the normal messages are published to a source found in the cache object. This source can be materialized as many times as needed. The cache itself gives you some convenience methods to subscribe to the source. 
```scala
class MyActor extends Actor {
  def receive: Receive = {
    case APIMessage.Ready(state) => doStuff()
  }
}
object MyActor {
  def props: Props = Props(new MyActor)
}

//Somewhere with access to the cache
cache.subscribeAPIActor(context.actorOf(MyActor.props, "MyActor"), completeMessage = PoisonPill, classOf[APIMessage.Ready])
```

## The GuildRouter
Often you find yourself wanting to have an actor specific for each guild the bot is in. You can use the `GuildRouter` to achieve that. While not a real router, it gets the job done nicely. To use the `GuildRouter`, simply pass it a `Props` of the actor you want one of for each guild, subscribe the `GuildRouter` to some event, and it will take care of the rest. The `GuildRouter` also allows you to specify a separate actor to send all messages which could, but does not have a guild. For example when a message is created. It could be a message in a guild channel, or it could be a message in a DM channel.
```scala
val nonGuildHandler = context.actorOf(MyActor.props, "MyActorNonGuild")
context.actorOf(GuildRouter.props(MyActor.props, Some(nonGuildHandler)), "MyActor")
```

## Commands
If you want to work with commands from the low level API, you have to add a dependency on the commands module. I'd recommend you to at least skim over the high level commands API before reading this, as they share many concepts.

### The Commands object
Just like the job of the `Cache` object is to keep track of the current events, and the state of the application, it's the job of the `Commands` object to keep track of the current commands in the application. To get a `Commands` instance, call `Commands.create`. From there you have access to a source of raw commands that can be materialized as many times as needed.
```scala
Commands.create(needMention = true, Set(OurCategories.!), cache, requests)
```

### CmdFactory
Often times, working with the raw commands objects directly can be kind of tiresome and usually involves a lot of boilerplate. For that reason, Ackcord also provides several `CmdFactory` which will the the job of selecting the right commands, possibly parsing it, running the code for the command, and more. You'll choose a different factory type depending on if your actor parses the arguments it receives or not. The factory also supplies extra information about the command, like an optional description and filters. We'll go over each of these one by one.

#### Running the code
AckCord represents the code to run for a command as a Sink that can be connected to the command messages. If you instead want to use an actor, you can do that too using `Sink.actorRef`. While you can use the materialized value of running the sink, in most cases you probably only want use an ignoring sink as the last step of the pipeline.

### Other helpers
There are a few more helpers that you can use when writing commands. The first one is `CmdFlow` and `ParsedCmdFlow[A]` as mentioned earlier, which gives helps you construct a flow with an implicit cache snapshot. The next is the `requestDSL` methods on the command factory objects, which lets you create factories that take a flow from a `Cmd` or `ParsedCmd[A]` to a `RequestDSL[_]` instead.

### Putting it all together
So now that we know what all the different things to, let's create our factories.
```scala
//Normal unparsed command doing the request sending itself
val EchoCmdFactory = BaseCmdFactory(
  category = OurCategories.!,
  aliases = Seq("echo"),
  sink = requests =>
    CmdFlow
      .mapConcat { implicit c => cmd =>
        cmd.msg.tChannel.map(_.sendMessage(cmd.args.mkString(" "))).toList
      }
      .via(requests.flow)
      .to(Sink.ignore),
  description = Some(CmdDescription(name = "Echo", description = "Replies with the message sent to it"))
)

//Parsed command using RequestDSL
val GetUsernameCmdFactory = ParsedCmdFactory.requestDSL[User](
  category = OurCategories.!,
  aliases = Seq("getUsername"),
  flow = ParsedCmdFlow[User]
    .map { implicit c => cmd =>
      import RequestDSL._
      for {
        channel <- maybePure(cmd.msg.tChannel)
        _       <- channel.sendMessage(s"Username for user is: ${cmd.args.username}")
      } yield ()
    },
  description = Some(CmdDescription(name = "Get username", description = "Get the username of a user"))
)

commands.subscribe(EchoCmdFactory)(Keep.left)
commands.subscribe(GetUsernameCmdFactory)(Keep.left)
```

### Help command
AckCord also provides the basics for a help command if you want something like that in the form of the abstract actor `HelpCmd`. You use it by sending `HelpCmd.AddCmd` with the factory for the command, and the lifetime of the command. You get the lifetime of the command as a result of registering the command.

## Lavaplayer module
AckCord also provides a module to use Lavaplayer to send music to Discord. To do that, first create an instance of the `LavaplayerHandler` actor. This actor should be shared for an entire guild. Check the objects in the companion object of `LavaplayerHandler` for the valid messages you can send it.

# More information
You can find more info in the examples, the wiki and the ScalaDoc.

Or you can just join the Discord server (we got cookies).

[![](https://discordapp.com/api/guilds/399373512072232961/embed.png?style=banner1)](https://discord.gg/fdKBnT) 
