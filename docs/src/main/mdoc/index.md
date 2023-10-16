---
layout: home
title: "AckCord"
---

{% assign versions = site.data.versions %}

# AckCord
*You do what you want, exactly how you want it.*

AckCord is a Scala Discord library, powered by sttp. AckCord's focus is on
letting you choose the level of abstraction you want, without sacrificing speed.
Want to work with the raw events from the gateway? Works for that. Maybe you
don't want to bother with any of the underlying implementation and technicalities.
Works for that too. Only interested in the REST requests? Pull in that module
and ignore the rest. AckCord is fast, modular, and clean, focusing on
letting you write good code.

Add AckCord to your project by adding these statements to your `build.sbt` file.
```scala
libraryDependencies += "net.katsstuff" %% "ackcord" % "{{versions.ackcord}}"
```

# More information
For more information, either see the the examples or the ScalaDoc.

Or you can just join the Discord server (we got cookies).

[![](https://discord.com/api/guilds/399373512072232961/embed.png?style=banner1)](https://discord.gg/5UH627u) 
