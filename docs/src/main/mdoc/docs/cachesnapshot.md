---
layout: docs
title: CacheSnapshot
---

# {{page.title}}
All access to data store in AckCord is represented as a `CacheSnapshot`. Many methods in AckCord takes an implicit cache to let you access stuff.

AckCord also provides the `Streamable` typeclass for converting `F[A]` into a `Source[A, NotUsed]`. AckCord provides instances of this type for `Id` and `Future`, but if you're dealing with something else, you'll have to write the typeclass instance yourself. Hopefully you shouldn't have to deal too much with `Streamable` yourself.

## Core's Cache
If you depend on Core (you most likely do). Then the most common type of snapshot you will encounter is `MemoryCacheSnapshot`. You will also encounter a `CacheState` when listening to events. This type gives you access to two different snapshots. One for before the event happened, and one for after. Generally you want to use the one after.

## Easy access to the cache
Often times when overriding methods, an implicit cache snapshot will already be present. In some cases when dealing with a function instead though, you may want to extract the cache snapshot as a different parameter to mark it implicit. That's where the `withCache` method for the high level API, and `CmdFlow` and `ParsedCmdFlow` comes in for the low level API. Using these we can extract the current cache snapshot into a separate function, used like so for the high level API.

### Example

As before we create out client as usual.
```scala mdoc:silent
import ackcord._
import cats.Id
val token = "<token>"
val settings = ClientSettings(token)
import settings.executionContext

val futureClient = settings.createClient()
futureClient.foreach { client =>
  //client.login()
}
```

Using the `withCache` method. (Note: `withCache`, type inference and IntelliJ don't always play nice together)

```scala mdoc
futureClient.foreach { client =>
  client.onEvent {
    client.withCache[Id, APIMessage] { _ => {
        case APIMessage.Ready(_) => println("Now ready")
        case _                   => 
      }
    }
  }
}
```

Ok course you could also create an instance of `EventHandler`, and register that instead.
```scala mdoc
futureClient.foreach { client =>
  client.registerHandler {
    new EventHandler[Id, APIMessage.Ready] {
      override def handle(message: APIMessage.Ready)(implicit c: CacheSnapshot): Unit = 
        println("Now ready")
    }
  }
}
```

As for the flows, I'll get back to those when talking about commands.

```scala mdoc:invisible
settings.system.terminate()
```