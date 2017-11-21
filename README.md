# AckCord
*You do what you want, exactly how you want it.*

AckCord is a Scala library using Akka for the Discord API. Unlike many other libraries, AckCord hides little of the underlying aspects, and gives the user much more freedom in how they want to set up their bot. Want to use a custom cache, or no cache at all, sure thing, just wire up the cache with the other actors instead of using the defaults. Have no need for the websocket part of the library? All good, just use the REST part and forget about the other parts.

While AckCord is still in active development, you can try AckCord by adding this to your `build.sbt` file.
```scala
libraryDependencies += "net.katsstuff" %% "ackcord" % "0.6"
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
There are two ways to send requests in AckCord. The first one is to create `RequestWrapper` objects with requests from the `RESTRequests` object. The second is to import `net.katsstuff.ackcord.syntax._` and use the extensions methods provided by that. However you do it, when sending requests, there are two more things you can provide as part of the request. The first is the request context. This is an object that allows you to keep track of what request was made in what context. The second is an actor to send the response to. When using the syntax import, this is by default set to the actor that sent the request. Once you have your request wrapper, you need to send it somewhere. You can either use one of the flows in `RequestStreams` (use simpleRequestFlow if you don't know which one you need) or send it to the discord client actor.

## The GuildRouter
Often you find yourself wanting to have an actor specific for each guild the bot is in. You can use the `GuildRouter` to achieve that. While not a real router, it gets the job done nicely. To use the `GuildRouter`, simply pass it a `Props` of the actor you want one of for each guild, subscribe the `GuildRouter` to some event, and it will take care of the rest. The `GuildRouter` also allows you to specify a separate actor to send all messages which could, but does not have a guild. For example when a message is created. It could be a message in a guild channel, or it could be a message in a DM channel.
```scala
val nonGuildHandler = context.actorOf(MyActor.props, "MyActorNonGuild")
context.actorOf(GuildRouter.props(MyActor.props, Some(nonGuildHandler)), "MyActor")
```

## Commands
You probably also want some commands for your bot. AckCord has a separate module that makes dealing with commands easier. First add a dependency on the command module.
```scala
libraryDependencies += "net.katsstuff" %% "ackcord-commands" % "0.6"
```

### Command handlers
While you can use normal actors, AckCord also gives you special command handler actors to reduce boilerplate and make stuff a bit easier. AckCord allows you to both parse your command arguments for yourself, or get some help. Depending on which of those behaviors you want, you'll want to go with a different handler.
```scala
class EchoCmd(val client: ClientActor) extends CmdActor {
  override def handleCommand(msg: Message, args: List[String])(implicit c: CacheSnapshot): Unit = {
    msg.tChannel.foreach(client ! _.sendMessage(args.mkString(" ")))
  }
}

class GetUsernameCmd(val client: ClientActor) extends ParsedCmdActor[User] {
  override def handleCommand(msg: Message, args: User, remaining: List[String])(implicit c: CacheSnapshot): Unit = {
    msg.tChannel.foreach(client ! _.sendMessage(args.username))
  }
}
```

### CmdFactory
Next you need some way to construct your actor in a proper way. A `CmdFactory` is used for this. You'll choose a different factory depending on if your actor parses the arguments it receives or not. The factory also supplies extra information about the command, like what category it's in (prefix), it's aliases, an optional description and filters. We'll go over each of these one by one.

#### CmdCategory
AckCord uses objects to mark what category a command is in. A category takes a prefix, and a name. Generally you specify your categories like this.
```scala
object OurCategories {
  object ! extends CmdCategory("!", "Generic commands")
}
```

#### Aliases
The aliases of a command is represented as a simple `Seq[String]`. All aliases will automatically be converted to lowercase.

#### CmdDescription
You can also supply an optional description about your command, which contains a display name, a description, and a usage which defaults to empty.

#### CmdFilter
Often times you have a command that can only be used in a guild, or with those with a specific permission, or by non bots. For cases like those you can use a `CmdFilter`. The filter will stop the command before it arrives at your actor, and send an error message in return.

#### Putting it all together
So now that we know what all the different things to, let's create our factories.
```scala
object EchoCmdFactory
  extends BaseCmdFactory(
    category = OurCategories.!,
    aliases = Seq("echo"),
    cmdProps = client => Props(new EchoCmd(client)),
    description = Some(CmdDescription(name = "Echo", description = "Replies with the message sent to it"))
  )

object GetUsernameCmdFactory
  extends ParsedCmdFactory[NotUsed](
    category = OurCategories.!,
    aliases = Seq("ping"),
    cmdProps = client => Props(new GetUsernameCmd(client)),
    description = Some(CmdDescription(name = "Get username", description = "Get the username of a user"))
  )
```

### CommandRouter
Next you need something to send the command message to these actors. That's where the `CmdRouter`comes in (still not a real router). When creating the router pass in if we require a mention before the command, an an error handler which handles what happens if no command is specified at all, or if an unknown command is used, and an optional help command factory, which will be sent the descriptions and names of all registered commands for this router. For this example I leave out the help command.
```scala
val cmdRouter = context.actorOf(
  CmdRouter.props(client, needMention = true, ExampleErrorHandler.props(client), None),
  "OurCommands"
)

cache.subscribeAPIActor(cmdRouter, completeMessage = DiscodClien.ShutdownClient, classOf[APIMessage.MessageCreate])
```

Next we need to register our commands. To do this, send the `RegisterCmd` message to the router. If we had specified a help command, it would then also be registered with the help command.
```scala
cmdRouter ! CmdRouter.RegisterCmd(EchoCmdFactory)
cmdRouter ! CmdRouter.RegisterCmd(GetUsernameCmdFactory)
```

# More information
You can find more info in the examples, and the wiki. Or you can look through the project and read the ScalaDoc.
