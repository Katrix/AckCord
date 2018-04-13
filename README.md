# AckCord
*You do what you want, exactly how you want it.*

AckCord is a Scala library for Discord, using Akka. AckCord's focus is on letting choose the level of abstraction you want. Want to work with the raw events from the gateway? Or maybe you don't want to bother with any of the underlying implementation and technicalities. Works for that too. Only interested in the REST requests, then pull in that module and ignore the rest.

While AckCord is still in active development, you can try AckCord by adding some of these to your `build.sbt` file.
```scala
libraryDependencies += "net.katsstuff" %% "ackcord"                 % "0.10" //For high level API, includes all the other modules
libraryDependencies += "net.katsstuff" %% "ackcord-core"            % "0.10" //Low level core API
libraryDependencies += "net.katsstuff" %% "ackcord-commands-core"   % "0.10" //Low to mid level Commands API
libraryDependencies += "net.katsstuff" %% "ackcord-lavaplayer-core" % "0.10" //Low level lavaplayer API
```

For more information, either see the [microsite](http://ackcord.katsstuff.net), the examples or the ScalaDoc.

Or you can just join the Discord server (we got cookies).

[![](https://discordapp.com/api/guilds/399373512072232961/embed.png?style=banner1)](https://discord.gg/fdKBnT) 
