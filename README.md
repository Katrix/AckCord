# AckCord
*You do what you want, exactly how you want it.*

AckCord is a Scala library using Akka for the Discord API. Unlike many other libraries, AckCord hides little of the underlying aspects, and gives the user much more freedom in how they want to set up their bot. Want to use a custom cache, or no cache at all, sure thing, just wire up the cache with the other actors instead of using the defaults. Have no need for the websocket part of the library? All good, just use the REST part and forget about the other parts.

While AckCord is still in active development, you can try AckCord by adding this to your `build.sbt` file.
```scala
libraryDependencies += "net.katsstuff" %% "ackcord" % "0.7"
```

# Usage

To use AckCord in the normal way you need to get an instance of the Discord Actor.
```scala
implicit val system: ActorSystem  = ActorSystem("AckCord")
implicit val mat:    Materializer = ActorMaterializer()
import system.dispatcher

val eventStream = Cache.create

val settings = ClientSettings(token = "yourTokenHere")
DiscordClient.fetchWsGateway.map(settings.connect(_, cache)).onComplete {
 case Success(client) =>
   client ! DiscordClient.StartClient
 case Failure(e) =>
   println("Could not connect to Discord")
   throw e
}
```

All the normal events are published to a source found in the cache object. This source can be materialized as many times as needed. The cache itself gives you some convenience methods to subscribe to the source. 
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

## The CacheSnapshot and CacheState
You will probably encounter the `CacheSnapshot` and `CacheState` relatively early. Cord handles the concept of a cache my having an object called the CacheSnapshot. In many cases you might be interested in the state of something both before and after some event. That's where `CacheState` comes in, which keeps track of the current, and previous snapshot. Many of the methods you find in AckCord takes an implicit cache(although there exists alternatives where it makes sense), to allow you to operate on the current state. When you receive an event, you receive a `CacheState`. You then have to choose which one you want to use (it's normally the current one) by marking it as implicit.

## Sending requests
To send requests in AckCord, you first need a request object to sent. There are two main ways to get such a request object. The first one is to create `Request` objects with requests from the `RESTRequests` and `ImageRequest` object manually. The second is to import `net.katsstuff.ackcord.syntax._` and use the extensions methods provided by that. However you do it, you can also specify a context object as part of the request, which you get back from the response. If you need some sort of order from your request, this is how you do it, as you do not always receive responses in the same order you sent the requests.

Now that you have the request object, you have to send it. Again, there are two main ways to do this. 

The first is to convert the request objects into a `Source`, and connect that source to one of the flows found in `RequestStreams`. If you don't know which one to use, stick with `RequestStreams.simpleRequestFlow`.

The second is to use RequestDSL. RequestDSL is an DSL that allows you to flatMap and transform your requests in monadic ways. To use it you still need a request flow, but here you supply one flow which will the  run multiple requests. Using it is simple.

Running this code would send two messages, "Foo" and "Bar" after each other, and then return a source of the second message's id. You also get utility methods like `init`, `pure`, `maybePure` and `maybeRequest` to life values into the RequestDSL.
```scala
val msgId = RequestDSL(RequestStreams.simpleRequestFlow(token)) {
  import RequestDSL._
  for {
    _ <- channel.sendMessage("Foo")
    msg <- channel.sendMessage("Bar")
  } yield msg.id
}
```

## The GuildRouter
Often you find yourself wanting to have an actor specific for each guild the bot is in. You can use the `GuildRouter` to achieve that. While not a real router, it gets the job done nicely. To use the `GuildRouter`, simply pass it a `Props` of the actor you want one of for each guild, subscribe the `GuildRouter` to some event, and it will take care of the rest. The `GuildRouter` also allows you to specify a separate actor to send all messages which could, but does not have a guild. For example when a message is created. It could be a message in a guild channel, or it could be a message in a DM channel.
```scala
val nonGuildHandler = context.actorOf(MyActor.props, "MyActorNonGuild")
context.actorOf(GuildRouter.props(MyActor.props, Some(nonGuildHandler)), "MyActor")
```

## Commands
You probably also want some commands for your bot. AckCord has a separate module that makes dealing with commands easier. First add a dependency on the command module.
```scala
libraryDependencies += "net.katsstuff" %% "ackcord-commands" % "0.7"
```

#### CmdCategory
AckCord uses objects to mark what category a command is in. A category takes a prefix, and a name. Generally you specify your categories like this. You need to specify all your categories in advance with AckCord.
```scala
object OurCategories {
  object ! extends CmdCategory("!", "Generic commands")
}
```

### The Commands object
Commands in AckCord relies on the idea of most commands looking a certain way that is `[mention] <prefix><command> [args...]`, where mention is a setting you toggle when using commands. What AckCord then does is to listen to all created messages, and convert the ones that look like commands into raw commands.

Just like the job of the `Cache` object is to keep track of the current events, and the state of the application, it's the job of the `Commands` object to keep track of the current commands in the application. To get a `Commands` instance, call `Commands.create`. From there you have access to a source of raw commands that can be materialized as many times as needed.
```scala
Commands.create(needMention = true, Set(OurCategories.!), cache, token)
```

### CmdFactory
Often times, working with the raw commands objects directly can be kind of tiresome and usually involves a lot of boilerplate. For that reason, Ackcord also provides several `CmdFactory` which will the the job of selecting the right commands, possibly parsing it, running the code for the command, and more. You'll choose a different factory type depending on if your actor parses the arguments it receives or not. The factory also supplies extra information about the command, like an optional description and filters. We'll go over each of these one by one.

#### CmdFilter
Often times you have a command that can only be used in a guild, or with those with a specific permission, or by non bots. For cases like those you can use a `CmdFilter`. The filter will stop the command before it arrives at your actor, and send an error message in return.

#### Running the code
AckCord represents the code to run for a command as a Sink that can be connected to the command messages. If you instead want to use an actor, you can do that too using `Sink.actorRef`. While you can use the materialized value of running the sink, in most cases you probably only want use an ignoring sink as the last step of the pipeline.

### Other helpers
There are a few more helpers that you can use when writing commands. The first one is `CmdFlow` and `ParsedCmdFlow[A]` which gives helps you construct a flow with an implicit cache snapshot. The next is the `requestDSL` methods on the command factory objects, which lets you create factories that take a flow from a `Cmd` or `ParsedCmd[A]` to a `RequestDSL[_]` instead.

### Putting it all together
So now that we know what all the different things to, let's create our factories.
```scala
//Normal unparsed command doing the request sending itself
val EchoCmdFactory = BaseCmdFactory(
  category = OurCategories.!,
  aliases = Seq("echo"),
  sink = (token, system, mat) =>
    CmdFlow
      .mapConcat { implicit c => cmd =>
        cmd.msg.tChannel.map(_.sendMessage(cmd.args.mkString(" "))).toList
      }
      .via(RequestStreams.simpleRequstFlow(token)(system, mat))
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

# More information
You can find more info in the examples, the wiki and the ScalaDoc.

Or you can just join the Discord server (we got cookies).

[![](https://discordapp.com/api/guilds/399373512072232961/embed.png?style=banner1)](https://discord.gg/fdKBnT) 
