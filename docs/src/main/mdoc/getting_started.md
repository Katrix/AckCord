---
layout: docs
title: Getting Started
position: 1
---

{% assign versions = site.data.versions %}

# AckCord
*You do what you want, exactly how you want it.*

AckCord is a Scala library for Discord, using Akka. AckCord's focus is on letting you choose the level of abstraction you want. Want to work with the raw events from the gateway? Works for that. Maybe you don't want to bother with any of the underlying implementation and technicalities. Works for that too. Only interested in the REST requests? Pull in that module and ignore the rest.

While AckCord is still in active development, you can try AckCord by adding some of these to your `build.sbt` file.
```scala
resolvers += Resolver.JCenterRepository
libraryDependencies += "net.katsstuff" %% "ackcord"                 % "{{versions.ackcord}}" //For high level API, includes all the other modules
libraryDependencies += "net.katsstuff" %% "ackcord-core"            % "{{versions.ackcord}}" //Low level core API
libraryDependencies += "net.katsstuff" %% "ackcord-commands-core"   % "{{versions.ackcord}}" //Low to mid level Commands API
libraryDependencies += "net.katsstuff" %% "ackcord-lavaplayer-core" % "{{versions.ackcord}}" //Low level lavaplayer API
```

From there on you go with the API you have decided to use.

## Should I use the low level or high level API?
It depends on your knowledge with Scala and Akka. If you know what Akka Streams is, and have no problem using it, then I'd say go for the low level API. If you're familiar with other stream libraries, you might also want to try the low level API. Lastly, if you want to use AckCord the way it was meant to be used, and don't mind reading up on the documentation for Akka, then again go with the low level API. If none of those apply to you, then go with the high level API.

## The simplest bot

Most of these examples assume these two imports.
```scala mdoc:silent
import ackcord._
import ackcord.data._
```

AckCord comes with two (3 if you count handling dispatch events yourself) major APIs. A high level and a low level API. Let's see how you would log in from both the high level and the low level API.

First we need a token.
```scala mdoc
val token = "<token>" //Your Discord token
```

NOTE: For all codeblocks in this documentation, I'll comment out all logins as this is real code being run.

## Logging in from the high level API

To use the high level API of AckCord, you create the `ClientSettings` you want to use, and call build. This returns a future client. Using that client you can then listen for specific messages and events. Once you have set everything up, you call login.
```scala mdoc
//Keep your settings around. It contains useful objects.
val clientSettings = ClientSettings(token)
import clientSettings.executionContext

val futureClient = clientSettings.createClient()

futureClient.foreach { client =>
  client.onEvent[cats.Id] {
    case APIMessage.Ready(_) => println("Now ready")
    case _                   =>
  }
  
  //client.login()
}
```

## Logging in from the low level API

```scala mdoc:invisible
clientSettings.system.terminate()
```

```scala mdoc:reset:invisible
import ackcord._
import ackcord.data._

val token = "<token>"
```

When working with the low level API, you're the one responsible for setting stuff up, and knowing how it works.

First we need an actor system.
```scala mdoc:silent
import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink

implicit val system: ActorSystem  = ActorSystem("AckCord")
import system.dispatcher
```

Next we create the `Cache`, and the `RequestHelper`. The `Cache` helps you know when stuff happens, and keeps around the changes from old things that have happened. The `RequestHelper` helps you make stuff happen. I'd recommend looking into the settings used when creating both the `Cache` and `RequestHelper` if you want to fine tune your bot.
```scala mdoc:silent
val cache = Cache.create
val requests = RequestHelper.create(BotAuthentication(token))
```

Now that we have all the pieces we want, we can create our event listener. In the low level API, events are represented as a `Source` you can materialize as many times as you want.
```scala mdoc:silent
cache.subscribeAPI.collect {
  case APIMessage.Ready(c) => c
}.to(Sink.foreach(_ => println("Now ready"))).run()
```

Finally we can create our `GatewaySettings` and start the shard.
```scala mdoc
val gatewaySettings = GatewaySettings(token)
DiscordShard.fetchWsGateway.foreach { wsUri =>
 val shard = DiscordShard.connect(wsUri, gatewaySettings, cache, actorName = "DiscordShard")
 //shard ! DiscordShard.StartShard
}
```

```scala mdoc:invisible
system.terminate()
```

## Access to the low level API from the high level API
Accessing the low level API from the high level API is simple.
The shard actors can be gotten from the `shards` method on the `DiscordClient[F]`.
The cache can be gotten from the `cache` method on the `DiscordClient[F]`.

```scala mdoc:reset:invisible
import akka.actor.ActorRef
import ackcord._
import ackcord.data._

val clientSettings = ClientSettings("<token>")
import clientSettings.executionContext

val futureClient = clientSettings.createClient()
```

```scala mdoc
futureClient.foreach { client =>
  val shards: Seq[ActorRef] = client.shards
  val cache: Cache = client.cache
}
```

```scala mdoc:invisible
clientSettings.system.terminate()
```