---
layout: docs
title: Making Requests
---

# {{page.title}}
What use is a Discord library if you can't make REST requests? There are two parts to this, creating a request, and running it. Both have many ways to do things.

## Creating a request
First we need a request object to send. There are two main ways to get such a request object. The first one is to create `Request` objects with requests from the `http.rest` and `http.images` packages manually. The second is to import `net.katsstuff.ackcord.syntax._` and use the extensions methods provided by that. If you use the low level API, you can also specify a context object as part of the request, which you get back from the response. If you need some sort of order from your request, you use this, as requests are sent and received in any order.

## Running the request
To run requests you would either use a `RequestHelper`, or a `RequestDSL` object. 

`RequestHelper` is normally used in low level API where you want to model your behavior as a stream. Here you represent your requests as a `Source`, and the act of sending them as a `Flow` found in `RequestHelper`. You have to create the `RequestHelper` yourself.

`RequestDSL` while more limited than `RequestHelper` allows you to model your behavior as a sequence of requests using monadic operations. When using `RequestDSL`, you also need something to run it. Either you have to run it yourself using a `Flow` gotten from `RequestHelper`, or some methods can run it for you. For the high level API, all methods that have DSL in their name expects a `RequestDSL`, and will run it when needed.

## Example

```tut
import net.katsstuff.ackcord._
import net.katsstuff.ackcord.data._
import net.katsstuff.ackcord.syntax._
import net.katsstuff.ackcord.http.rest.CreateMessage
val token = "<token>"
val settings = ClientSettings(token)
import settings.executionContext

val futureClient = settings.build()

futureClient.foreach { client =>
  client.onEventDSLC { implicit c => {
    case APIMessage.ChannelCreate(channel, _) =>
      import RequestDSL._
      for {
        //optionPure lifts an Option into the dsl
        guildChannel <- optionPure(channel.asGuildChannel)
        //liftOptionT lifts an OptionT into the dsl
        guild        <- liftOptionT(guildChannel.guild)
        //optionRequest lifts an optional request into the dsl. Normal requests don't need to be lifted
        msg          <- optionRequest(guild.tChannels.headOption.map(_.sendMessage(s"${guildChannel.name} was deleted")))
        //Creating a request without using the extra syntax
        _            <- CreateMessage.mkContent(msg.channelId, "Another message")
      } yield ()
    }
  }
  
  //client.login()
}
```

## Access to the low level API
Accessing the low level API from the high level API is as simple as getting the `RequestHelper` instance that is backing the `DiscordClient[F]` instance is as simple as calling the `requests` method on a `DiscordClient[F]`.
```tut
futureClient.foreach { client =>
  val requests: RequestHelper = client.requests
}
```

```tut:invisible
settings.system.terminate()
```