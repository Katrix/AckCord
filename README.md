# AckCord
AckCord is a Scala library using Akka for the Discord API. Unlike many other libraries, AckCord hides little of the underlying aspects, and gives the user much more freedom in how they want to set up their bot. Want to use a custom cache, or no cache at all, sure thing, just wire up the cache with the other actors instead of using the defaults. Have no need for the websocket part of the library? All good, just use the REST part and forget about the other parts.

While AckCord is still in active development, and no real version has been released so far, you can try AckCord by adding this to your `build.sbt` file.
```scala
resolvers += "jitpack" at "https://jitpack.io"
libraryDependencies += "com.github.Katrix-.AckCord" % "ackCord" % "master-SNAPSHOT"
```

# Usage

To use AckCord in the normal way you need to get an instance of the Discord Actor.
```scala
implicit val system: ActorSystem  = ActorSystem("AckCord")
implicit val mat:    Materializer = ActorMaterializer()
import system.dispatcher

val eventStream = new EventStream(system)

val settings = ClientSettings(token = "yourTokenHere")
DiscordClient.fetchWsGateway.map(settings.connect(eventStream, _)).onComplete {
 case Success(client) =>
   client ! DiscordClient.StartClient
 case Failure(e) =>
   println("Could not connect to Discord")
   throw e
}
```

All the normal events are published on the event stream you pass in when you connect. From there you just create actors, subscribe them to the event stream and wait for the events to roll in.
```scala
class MyActor extends Actor {
  def receive: Receive = {
    case APIMessage.Ready(c, _) => doStuff()
  }
}
object MyActor {
  def props: Props = Props(new MyActor)
}

//In your main actor
eventStream.subscribe(context.actorOf(MyActor.props, "MyActor"), classOf[APIMessage.Ready])
```

## The CacheSnapshot
You'll probably encounter the `CacheSnapshot` relatively early. AckCord handles the concept of a cache my having an object called the CacheSnapshot. Many of the commands you find in AckCord takes an implicit cache(although there exists alternatives where it makes sense), to allow you to operate on the current state. When you receive an event, you get two cache snapshots. The current on that contains all the changes that this even brought, and the previous one, without any of the changes caused by that event. You just have to choose which one you want to use (it's normally the current one).

## The GuildRouter
Often you find yourself wanting to have an actor specific for each guild the bot is in. You can use the `GuildRouter` to achieve that. While not a real router, it gets the job done nicely. To use the `GuildRouter`, simply pass it a `Props` of the actor you want one of for each guild, subscribe the `GuildRouter` to some event, and it will take care of the rest. The `GuildRouter` also allows you to specify a separate actor to send all messages which could, but doesn't have a guild. For example when a message is created. It could be a message in a guild channel, or it could be a message in a DM channel.
```scala
val nonGuildHandler = context.actorOf(MyActor.props, "MyActorNonGuild")
context.actorOf(GuildRouter.props(MyActor.props, Some(nonGuildHandler)), "MyActor")
```

## Commands
You probably also want some commands for your bot. AckCord has a seperate module that makes dealing with commands easier. First add a dependency on the command module.
```scala
libraryDependencies += "com.github.Katrix-.AckCord" % "ackCordCommands" % "master-SNAPSHOT"
```

### Command handlers
While you can use normal actors, AckCord also gives you special command handler actors to reduce boilerplate and make stuff a bit easier. AckCord allows you to both parse your command arguments for yourself, or get some help. Depending on which of those behaviors you want, you'll want to go with a different handler.
```scala
class EchoCommand(val client: ClientActor) extends CommandActor {
  override def handleCommand(msg: Message, args: List[String])(implicit c: CacheSnapshot): Unit = {
    msg.tChannel.foreach(client ! _.sendMessage(args.mkString(" ")))
  }
}
object EchoCommand {
  def props(client: ClientActor): Props = Props(new EchoCommand(client))
}

class GetUsernameCommand(val client: ClientActor) extends ParsedCommandActor[User] {
  override def handleCommand(msg: Message, args: User, remaining: List[String])(implicit c: CacheSnapshot): Unit = {
    msg.tChannel.foreach(client ! _.sendMessage(args.username))
  }
}
object GetUsernameCommand {
  def props(client: ClientActor): Props = Props(new GetUsernameCommand(client))
}
```

### CommandRouter
Next you need something to send the command message to these actors. That's where the `CommandRouter`(still not a real router) comes in. While you can register commands to it when it's already started, I'd recommend you to do so when starting the actor. First though we need a `CmdCategory` for our actor which provides the name and prefix for the commands, and a `CommandParser` for our parsed command. Last we need an error handler which handles what happens if no command is specified at all, or if an unknown command is used.
```scala
object OurCategories {
  object ! extends CmdCategory("!", "Generic commands")
}
val getUsernameParser = CommandParser.props(MessageParser[User], GetUsernameCommand.props(client))
```

Once we have all of that we can finally set up our command router.
```scala


val commandRouter: Props = CommandRouter.props(
  needMention = true,
  Map(
    OurCategories.! -> Map(
      "echo" -> EchoCommand.props(client),
      "getusername" -> getUsernameParser
    )
  ),
  ExampleErrorHandler.props(client)
)

eventStream.subscribe(context.actorOf(commandRouter, "OurCommands"), classOf[APIMessage.MessageCreate])
```

### CommandMeta
Creating the map for the commands is tiresome tough. That's why we have what's called `CommandMeta` which normally sits on the companion object of the command together with the props. `CommandMeta` allows you to group together the category, aliases, handler, and command information into one object. If we decided to use it for `GetUsernameCommand` (it can only be used with parsed commands), it would look something like this.
```scala
def cmdMeta(client: ClientActor): CommandMeta[User] =
  CommandMeta[User](
    category = OurCategories.!,
    alias = Seq("username", "getusername"),
    handler = props(client)
  )
```

When you have many `CommandMeta`s you can create a map you can pass to the `CommandRouter` with a single method.
```scala
val commands: Seq[CommandMeta] = ???
CommandMeta.routerMap(commands, client)
```

# More information
You can find more info in the examples, and the wiki. Or you can look through the project and read the ScalaDoc.