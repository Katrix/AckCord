---
layout: docs
title: Getting Started
position: 1
---

# AckCord
*You do what you want, exactly how you want it.*

AckCord is a Scala library for Discord, using Akka. AckCord's focus is on letting you choose the level of abstraction you want. Want to work with the raw events from the gateway? Works for that. Maybe you don't want to bother with any of the underlying implementation and technicalities. Works for that too. Only interested in the REST requests? Pull in that module and ignore the rest.

While AckCord is still in active development, you can try AckCord by adding some of these to your `build.sbt` file.
```scala
libraryDependencies += "net.katsstuff" %% "ackcord"                 % "0.10" //For high level API, includes all the other modules
libraryDependencies += "net.katsstuff" %% "ackcord-core"            % "0.10" //Low level core API
libraryDependencies += "net.katsstuff" %% "ackcord-commands-core"   % "0.10" //Low to mid level Commands API
libraryDependencies += "net.katsstuff" %% "ackcord-lavaplayer-core" % "0.10" //Low level lavaplayer API
```

From there on you go with the API you have decided to use.

Most of these examples assume these two imports.
```tut:silent
import net.katsstuff.ackcord._
import net.katsstuff.ackcord.data._
```

AckCord comes with two (3 if you count handling dispatch events yourself) major APIs. A high level and a low level API. Let's see how you would log in from both the high level and the low level API.

First we need a token.
```tut
val token = "<token>" //Your Discord token
```

NOTE: For all codeblocks in this documentation, I'll comment out all logins as this is real code being run.

## Logging in from the high level API

To use the high level API of AckCord, you create the `ClientSettings` you want to use, and call build. This returns a future client. Using that client you can then listen for specific messages and events. Once you have set everything up, you call login.
```tut
val clientSettings = ClientSettings(token) //Keep your settings around. It contains useful objects.
import clientSettings.executionContext

val futureClient = clientSettings.build()

futureClient.foreach { client =>
  client.onEvent {
    case APIMessage.Ready(_) => println("Now ready")
  }
  
  //client.login()
}
```

## Logging in from the low level API

For the low level API, you need to do most of the setup yourself. That includes creating the actor system, a stream materializer, the cache and the request helper.

```tut
import akka.actor.{ActorSystem, ActorRef}
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.Sink

implicit val system: ActorSystem  = ActorSystem("AckCord")
implicit val mat: Materializer = ActorMaterializer()
//import system.dispatcher We use the ExecutionContext imported above for this example too

val cache = Cache.create //The Cache keeps track of all the state, and provides a way to listen to changes in that state
val requests = RequestHelper.create(BotAuthentication(token)) //The RequestHelper allows us to make REST requests to Discord

//In the low level API, the events are represented as a Source you can materialize as many times you want.
cache.subscribeAPI.collect {
  case APIMessage.Ready(c) => c
}.to(Sink.foreach(_ => println("Now ready"))).run()

val gatewaySettings = GatewaySettings(token)
DiscordShard.fetchWsGateway.foreach { wsUri =>
 val shard = DiscordShard.connect(wsUri, gatewaySettings, cache, "DiscordShard")
 //shard ! DiscordShard.StartShard
}
```

## Access to the low level API from the high level API
Accessing the low level API from the high level API is simple.
The shard actors can be gotten from the `shards` method on the `DiscordClient[F]`.
The cache can be gotten from the `cache` method on the `DiscordClient[F]`.

```tut
futureClient.foreach { client =>
  val shards: Seq[ActorRef] = client.shards
  val cache: Cache = client.cache
}
```

```tut:invisible
clientSettings.system.terminate()
system.terminate()
```