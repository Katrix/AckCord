---
layout: docs
title: CacheSnapshot
---

# {{page.title}}
When reacting to changes in Discord, there are two main places you can get data from. 
The first is from the change event itself. This is for example the message 
created in the `APIMessage.MessageCreate` event. The second is from the cache, 
which up to this point we have been ignoring 
(hence the ignore in `onEventSideEffectsIgnore`). The signature of the 
non-ignore methods is `CacheSnapshot => PartialFunction[APIMessage, Unit]`. 
It might look a little weird, but is perfectly valid. Here is an example 
printing the channel of all new messages. 
All lookups in the cache return `Option`s.

```scala mdoc:invisible
import ackcord._
import ackcord.data._
import scala.concurrent.Await
import scala.concurrent.duration.Duration

val clientSettings = ClientSettings("")
val client = Await.result(clientSettings.createClient(), Duration.Inf)
```
```scala mdoc:silent
client.onEventSideEffects { c => {
    case APIMessage.MessageCreate(_, message, _) => println(c.getTextChannel(message.channelId))
  }
}
```

To increase ergonomics, there are also many ways to get objects from the cache 
without asking it directly. This assumes an `implicit` cache. The first is 
following ids. You can call `resolve` on any id to get the object it 
corresponds to. In some cases there might be multiple resolve methods, either
for performance reasons, or to get different objects that both use the same id.

```scala mdoc:silent
client.onEventSideEffects { implicit c => {
    case APIMessage.MessageCreate(_, message, _) => println(message.channelId.resolve)
  }
}
```

In addition to that, in many cases you can find methods on the object you're 
interacting with taking a cache snapshot, and returning an object directly.

```scala mdoc:silent
client.onEventSideEffects { implicit c => {
    case APIMessage.MessageCreate(_, message: GuildGatewayMessage, _) => 
      println(message.guild)
  }
}
```

## CacheState
Sometimes when dealing with events, you want to get a cache from just before 
the current event happened. In those cases it's time to use the `CacheState`. 
All `APIMessage`s contain one. In the example above it was the value we ignored 
on `APIMessage.MessageCreate`. It stores the current and the 
previous `CacheSnapshot`. For example, to get the message before it was edited 
in `APIMessage.MessageUpdate`, we do something like this.
```scala mdoc:silent
client.onEventSideEffects { c => {
    case APIMessage.MessageUpdate(_, message, CacheState(current, previous)) => 
      println(message.id.resolve(previous))
  }
}
```

`CacheSnapshot`s are always immutable. It's perfectly valid to both store them 
away for later, or have multiple of them.

```scala mdoc:invisible
clientSettings.system.terminate()
```
