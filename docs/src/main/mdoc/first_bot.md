---
layout: docs
title: Your first bot
position: 1
---

{% assign versions = site.data.versions %}

# {{page.title}}
Let's build the simplest bot you can using AckCord. The only thing it will do is 
to log in, and print a line to the console when it has done so.

First add AckCord to your project by adding these statements to your `build.sbt` file.
```scala
resolvers += Resolver.JCenterRepository
libraryDependencies += "net.katsstuff" %% "ackcord" % "{{versions.ackcord}}"
```

Most of these examples assume these imports.
```scala mdoc:silent
import ackcord._
import ackcord.data._
import scala.concurrent.Await
import scala.concurrent.duration.Duration
```

Next we need a bot token. You can get one by going to 
[https://discord.com/developers/applications](https://discord.com/developers/applications), 
creating a new application, and then creating a bot for your application.
```scala mdoc:silent
val token = "<token>" //Your Discord token. Be very careful to never give this to anyone else
```

## Logging in
The first thing you need when logging in is a `ClientSettings` instance. For you 
first bot, the defaults are fine, just pass it your token. This value also 
contains a lot of other useful stuff, so keep it around. This time you'll only 
use the execution context it contains, and create a client. Using that client 
you can then listen for specific messages and events. Once you have set 
everything up, you call login.
```scala mdoc:silent
val clientSettings = ClientSettings(token)
//The client settings contains an excecution context that you can use before you have access to the client
//import clientSettings.executionContext 

//In real code, please dont block on the client construction
val client = Await.result(clientSettings.createClient(), Duration.Inf)

//The client also contains an execution context
//import client.executionContext 

client.onEventSideEffectsIgnore {
  case APIMessage.Ready(_) => println("Now ready")
}

//client.login()
```

And that's it. You've now created your first bot. Now, this is probably not the 
only thing you want to do. Next I would recommend you to check out the pages for 
events, requests, and CacheSnapshot as well. Going over the commands API is 
probably also a smart thing to do.

```scala mdoc:invisible
clientSettings.system.terminate()
```