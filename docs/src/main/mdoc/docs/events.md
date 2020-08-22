---
layout: docs
title: Events
---

# {{page.title}}
Whenever something happens in Discord, it sends an event to your client. This 
can for example be: someone sends, updates, or deletes a message, your bot joins 
a new guild, or someone creates a new channel.

There are two main ways to listen to these events. The first is through 
`DiscordClient#onEventSideEffects` and friends. We'll use 
`DiscordClient#onEventSideEffectsIgnore` here, and go over the other forms in 
the next section.
```scala mdoc:invisible
import ackcord._
import ackcord.data._
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import cats.Id
import cats.instances.future._

val clientSettings = ClientSettings("")
import clientSettings.executionContext
val client = Await.result(clientSettings.createClient(), Duration.Inf)
```
```scala mdoc:silent
client.onEventSideEffectsIgnore {
  case APIMessage.MessageCreate(_, message, _) => println(message.content)
}
```

There is nothing that decides where you have to start listening to an event. 
You can do it anywhere, even inside another listener.

The return type of this method call is `EventRegistration[Mat]`, but most of 
often just `EventRegistration[NotUsed]`. This value lets you know when an event 
listener stops, and a way to stop it.

The second way to listen for events is using an `EventsController`. If you've 
ever used controllers and actions in the Play framework, then the base idea is 
the same. To start you pick an event listener builder that best fits your need. 
Some examples are `Event`, `TextChannelEvent` and `GuildUserEvent`. Depending on 
which event you chose, it might contain some extra information surrounding the 
event. You then call `to` with the event type you are listening to. From there 
you call one of the methods on it that bests suits you need, 
like `withSideEffects`, `withRequest`, `async`, and so on.

```scala mdoc:silent
class MyListeners(requests: Requests) extends EventsController(requests) {
  val onCreate = TextChannelEvent.on[APIMessage.MessageCreate].withSideEffects { m =>
    println(m.event.message.content)
  }
}
```

These listeners also need to be registered. 
That can be done using `DiscordClient#registerListener`.

```mdoc:silent
val myListeners = new MyListeners(client.requests)
client.registerListener(myListeners.onCreate)
```

Here is a small example that illustrates the fact that you can define and use 
your listeners anywhere. There is nothing special about them.
```mdoc:silent
val MessageEvent: EventListenerBuilder[TextChannelEventListenerMessage, APIMessage.MessageCreate] =
  TextChannelEvent.on[APIMessage.MessageCreate]

val createListeners: EventListener[APIMessage.MessageCreate, NotUsed] =
  MessageEvent.withSideEffects { m =>
    val startMessage = m.event.message

    if (startMessage.content.startsWith("start listen ")) {
      val inChannel  = m.channel.id
      val identifier = startMessage.content.replaceFirst("start listen ", "")

      val listener = client.registerListener(MessageEvent.withSideEffects { m =>
        if (m.channel.id == inChannel) {
          println(s"$identifier: ${m.event.message.content}")
        }
      })

      //We need lazy and an explicit type here to make Scala happy
      lazy val stopper: EventRegistration[NotUsed] =
        client.registerListener(MessageEvent.withSideEffects { m =>
          if (m.channel.id == inChannel && m.event.message.content == "stop listen " + identifier) {
            listener.stop()
            stopper.stop()
          }
        })

      //Initialize stopper
      stopper
    }
  }
```

Note that there is no defined order for which event handler will receive an 
event first. If you register an event handler inside another event handler, it 
might or might not receive the event that caused its registration.

```scala mdoc:invisible
clientSettings.system.terminate()
```
