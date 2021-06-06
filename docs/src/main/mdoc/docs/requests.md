---
layout: docs
title: Making Requests
---

# {{page.title}}
Requests are how most bot actions are done. Stuff like sending messages, 
creating channels and managing users. AckCord represents every request as an
object you construct. There are many ways to send an request, but the most 
common are through the end action on controllers, and using `RequestsHelper`. 
To create a request object, you can either just construct it normally, or use 
the syntax package.
```scala mdoc:invisible
import ackcord._
import ackcord.data._
import scala.concurrent.Await
import scala.concurrent.duration.Duration

val clientSettings = ClientSettings("")
import clientSettings.executionContext
val client = Await.result(clientSettings.createClient(), Duration.Inf)
```
```scala mdoc:silent
import ackcord.requests._
import ackcord.syntax._

client.onEventSideEffects { implicit c => 
  {
    case APIMessage.ChannelCreate(_, channel: TextGuildChannel, _, _) =>
      //There is also CreateMessage.mkContent for this specific pattern
      val constructManually = CreateMessage(channel.id, CreateMessageData(content = "Hello World"))
      val usingSyntax = channel.sendMessage(content = "Hello World")
  }
}
```

Next you need to send the request. For this example we will use `RequestsHelper`. 
This object can be found on the client. That also means it's time to move away
from `onEventSideEffects`. Even though sending requests can never 
return option, the `run` command will return an `OptFuture[A]`. 
`OptFuture[A]` is a wrapper around `Future[Option[A]]`. This is done
due to the many cache lookup methods that return `Option`s.
When your event handler returns `OptFuture[Unit]`, you're recommended 
to use `DiscordClient#onEventAsync` and `ActionBuilder#asyncOpt` instead.

```scala mdoc:silent
client.onEventAsync { implicit c => 
  {
    case APIMessage.ChannelCreate(_, channel: TextGuildChannel, _, _) =>
      client.requestsHelper.run(channel.sendMessage(content = "Hello World")).map(_ => ())
  }
}
```

The `RequestsHelper` object also contains lots of small helpers to deal with 
`OptFuture[A]` and requests.

```scala mdoc:silent
client.onEventAsync { implicit c => 
  {
    case APIMessage.ChannelCreate(_, channel, _, _) =>
      import client.requestsHelper._
      for {
        //optionPure lifts an Option into the dsl
        guildChannel <- optionPure(channel.asGuildChannel)
        guild        <- optionPure(guildChannel.guild)
        //runOption runs an Option[Request].
        msg          <- runOption(guild.textChannels.headOption.map(_.sendMessage("FIRST")))
        //Running a request without using the extra syntax
        _            <- run(CreateMessage.mkContent(msg.channelId, "Another message"))
      } yield ()
  }
}
```

```scala mdoc:invisible
clientSettings.system.terminate()
```