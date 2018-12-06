---
layout: docs
title: Low level API commands
---

# {{page.title}}
If you want to work with commands from the low level API, you have to add a dependency on the commands module. Before reading this, make sure you understand the shared command concepts.

As before we create out client as usual. Note the extra import for the commands package.
```tut:silent
import net.katsstuff.ackcord._
import net.katsstuff.ackcord.data._
import net.katsstuff.ackcord.syntax._
import net.katsstuff.ackcord.commands._
import cats.{Id, Monad}
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

## The Commands object
Just like the job of the `Cache` object is to keep track of the current events and state of the application, it's the job of the `Commands` object to keep track of the current commands in the application. To get a `Commands` instance, call `CoreCommands.create`. From there you have access to a source of raw commands that can be materialized as many times as needed.
```tut
val GeneralCommands = "!"
val commands = CoreCommands.create(CommandSettings[Id](prefixes = Set(GeneralCommands), needsMention = true), cache, requests)
```

Let's create a raw command, using the `Source` found the the `Commands` object.
```tut
def rawCommandEcho[F[_]: Monad: Streamable] = Flow[RawCmdMessage[F]].collect {
  case RawCmd(msg, GeneralCommands, "echo", args, c) =>
    implicit val cache: CacheSnapshot[F] = c
    Streamable[F].optionToSource(msg.tGuildChannel.map(_.sendMessage(s"ECHO: ${args.mkString(" ")}")))
}.flatMapConcat(identity).to(requests.sinkIgnore)

commands.subscribe.to(rawCommandEcho).run()
```

## CmdFactory
Often times, working with the raw commands objects directly can be kind of tiresome and usually involves a lot of boilerplate. For that reason, Ackcord also provides several types of `CmdFactory` which will do the the job of selecting the right commands, possibly parsing it, running the code for the command, and more. You'll choose a different factory type depending on if your command parses the arguments it receives or not. The factory also supplies extra information about the command, like an optional description and filters. We'll go over each of these one by one.

### Running the code
AckCord represents the code to run for a command as a Sink that can be connected to the command messages. While you can use the materialized value of running the sink, in most cases you probably only want use an ignoring sink as the last step of the pipeline.

## Other helpers
There are a few more helpers that you can use when writing commands. The first one is `CmdFlow[F]` and `ParsedCmdFlow[F, A]`, which helps you construct a flow with an implicit cache snapshot. The next is the `requestRunner` methods on the command factory objects, which lets you create factories that takes a `(RequestRunner[SourceRequest, F], <cmdtype>[F]) => SourceRequest[Unit]` instead.

## Putting it all together
So now that we know what all the different things to, let's create our factories.
```tut
def getUsernameCmdFactory[F[_]: Monad: Streamable] = ParsedCmdFactory.requestRunner[F, User](
  refiner = CmdInfo(prefix = GeneralCommands, aliases = Seq("getUsername")),
  run = implicit c => (runner, cmd) => {
    import runner._
    for {
      channel <- liftOptionT(cmd.msg.tGuildChannel[F])
      _       <- run(channel.sendMessage(s"Username for user is: ${cmd.args.username}"))
    } yield ()
  },
  description = Some(CmdDescription(name = "Get username", description = "Get the username of a user"))
)

commands.subscribe(getUsernameCmdFactory[Id])(Keep.left)
```

## Help command
AckCord also provides the basics for a help command if you want something like that in the form of the abstract actor `HelpCmd`. You use it by sending `HelpCmd.AddCmd` with the factory for the command, and the lifetime of the command. You get the lifetime of the command as a result of registering the command.

```tut:invisible
system.terminate()
```