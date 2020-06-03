---
layout: docs
title: Your first bot (Low level)
---

{% assign versions = site.data.versions %}

# Your first bot
Let's build the simplest low level bot you can using AckCord. The only thing it 
will do is to log in, and print a line to the console when it has done so.

First add AckCord to your project by adding these statements to your `build.sbt` file.
```scala
libraryDependencies += "net.katsstuff" %% "ackcord-core" % "{{versions.ackcord}}"
```

Most of these examples assume these two imports.
```scala mdoc:silent
import ackcord._
import ackcord.data._
```

Next we need a bot token. You can get one by going to 
[https://discord.com/developers/applications](https://discord.com/developers/applications), 
creating a new application, and then creating a bot for your application.
```scala mdoc:silent
val token = "<token>" //Your Discord token. Be very careful to never give this to anyone else
```

When working with the low level API, you're the one responsible for setting 
stuff up, and knowing how it works. Therefore there isn't one correct way to do 
this. What I will show here is a very barebones way to do it, but ultimately 
you're the one that will have to decide how to do it.

First we need an actor system. In most applications you probably already have 
one lying around.
```scala mdoc:silent
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import akka.stream.scaladsl.Sink
import ackcord.requests.Ratelimiter

implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.ignore, "AckCord")
import system.executionContext
```

Next we create the `Cache`, and the `RequestHelper`. The `Cache` helps you know 
when stuff happens, and keeps around the changes from old things that have 
happened. The `RequestHelper` helps you make stuff happen. I'd recommend 
looking into the settings used when creating both the `Cache` and `RequestHelper` 
if you want to fine tune your bot.
```scala mdoc:silent
val cache = Cache.create()
val ratelimiter = system.systemActorOf(Ratelimiter(), "Ratelimiter")
val requests = new RequestHelper(BotAuthentication(token), ratelimiter)
```

Now that we have all the pieces we want, we can create our event listener. 
In the low level API, events are represented as a `Source` you can materialize 
as many times as you want.
```scala mdoc:silent
cache.subscribeAPI.collect {
  case APIMessage.Ready(c) => c
}.to(Sink.foreach(_ => println("Now ready"))).run()
```

Finally we can create our `GatewaySettings` and start the shard.
```scala mdoc:silent
val gatewaySettings = GatewaySettings(token)
DiscordShard.fetchWsGateway.foreach { wsUri =>
  val shard = system.systemActorOf(DiscordShard(wsUri, gatewaySettings, cache), "DiscordShard")
  //shard ! DiscordShard.StartShard
}
```

```scala mdoc:invisible
system.terminate()
```