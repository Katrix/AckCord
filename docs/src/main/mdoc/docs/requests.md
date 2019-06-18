---
layout: docs
title: Making Requests
---

# {{page.title}}
What use is a Discord library if you can't make REST requests? There are two parts to this, creating a request, and running it. Both have many ways to do things. In AckCord, you don't do request by calling methods. Instead you first create a request object which describes everything about your request. The headers to use, the body to use. The uri to make the request to, and son on.

## Creating a request
First we need a request object to send. There are two main ways to get such a request object. The first one is to create `Request` objects with requests from the `ackcord.http.rest` and `ackcord.http.images` packages manually. The second is to import `ackcord.syntax._` and use the extensions methods provided by that. If you use the low level API, you can also specify a context object as part of the request, which you get back from the response. If you need some sort of order from your request, you use this, as requests are sent and received in any order.

## Running the request
To run requests you would either use a `RequestHelper`, or a `RequestRunner`. 

`RequestHelper` is normally used in low level API where you want to model your behavior as a stream. Here you represent your requests as a `Source`, and the act of sending them as a `Flow` found in `RequestHelper`. You then get a source of response objects. You have to create the `RequestHelper` yourself.

`RequestRunner` helps you use `RequestHelper` from higher level code. While you're still dealing with `Source`s. Generally you just put all your code in a for comprehensions. Sometimes there are dedicated versions of methods for using a `RequestRunner`.

## Example

```scala mdoc:silent
import ackcord._
import ackcord.data._
import ackcord.syntax._
import ackcord.requests.CreateMessage
val token = "<token>"
val settings = ClientSettings(token)
import settings.executionContext

val futureClient = settings.createClient()

futureClient.foreach { client =>
  client.onEvent {
    client.withCache[SourceRequest, APIMessage] { implicit c => {
        case APIMessage.ChannelCreate(channel, _) =>
          import client.sourceRequesterRunner._
          for {
            //optionPure lifts an Option into the dsl
            guildChannel <- optionPure(channel.asGuildChannel)
            //liftOptionT lifts an OptionT into the dsl
            guild        <- liftOptionT(guildChannel.guild)
            //optionRequest lifts an optional request into the dsl.
            msg          <- runOption(guild.tChannels.headOption.map(_.sendMessage(s"${guildChannel.name} was deleted")))
            //Creating a request without using the extra syntax
            _            <- run(CreateMessage.mkContent(msg.channelId, "Another message"))
          } yield ()
        case _ => client.sourceRequesterRunner.unit
      }
    }
  }
  
  //client.login()
}
```

## Access to the low level API
Accessing the low level API from the high level API is as simple as getting the `RequestHelper` instance in the `DiscordClient[F]` instance.
```scala mdoc
futureClient.foreach { client =>
  val requests: RequestHelper = client.requests
}
```

```scala mdoc:invisible
settings.system.terminate()
```