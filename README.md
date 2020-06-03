# AckCord
*Do what you want, exactly how you want it.*

[![Latest version](https://index.scala-lang.org/katrix/ackcord/ackcord/latest.svg)](https://index.scala-lang.org/katrix/ackcord/ackcord) [![Build Status](https://travis-ci.com/Katrix/AckCord.svg?branch=master)](https://travis-ci.com/Katrix/AckCord)

AckCord is a Scala Discord library, powered by Akka. AckCord's focus is on letting you choose the level of abstraction you want, without sacrificing speed. Want to work with the raw events from the gateway? Works for that. Maybe you don't want to bother with any of the underlying implementation and technicalities. Works for that too. Only interested in the REST requests? Pull in that module and ignore the rest. AckCord is fast, reactive, modular, and clean, focusing on letting you write good code.

For more info see the see the [microsite](https://ackcord.katsstuff.net/), the examples or the ScalaDoc(which you can find on the microsite).

Add AckCord to your project by adding these statements to your `build.sbt` file.
```scala
resolvers += Resolver.JCenterRepository
libraryDependencies += "net.katsstuff" %% "ackcord"                 % "0.16.1" //For high level API, includes all the other modules
libraryDependencies += "net.katsstuff" %% "ackcord-core"            % "0.16.1" //Low level core API
libraryDependencies += "net.katsstuff" %% "ackcord-commands-core"   % "0.16.1" //Low to mid level Commands API
libraryDependencies += "net.katsstuff" %% "ackcord-lavaplayer-core" % "0.16.1" //Low level lavaplayer API
```

The AckCord module map
```
      +----> voice ------> lavaplayer-core -+ 
      |                           ^         |   
      |                           |         v   
 data +----> gateway --> core ----+----> ackcord
      |                   ^                 ^   
      |                   |                 |
      +----> requests ----+-> commands -----+
```

Lastly, join our Discord server (we got cookies).

[![](https://discord.com/api/guilds/399373512072232961/embed.png?style=banner1)](https://discord.gg/5UH627u) 
