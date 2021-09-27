---
layout: docs title: The many modules of AckCord position: 2
---

# {{page.title}}

While the modules listed at the start are the main modules you want to depend on, AckCord has many more if you feel that
you don't need the full package.

Here is a map of all the modules, and how they depend on each other

```
      +----> voice ------> lavaplayer-core -+ 
      |                           ^         |   
      |                           |         v   
 data +----> gateway --> core ----+----> ackcord
      |                   ^                 ^   
      |                   |                 |
      +----> requests ----+-> commands -----+
```

{% assign versions = site.data.versions %}

**Make sure to add the following line to your SBT file if you're using Lavaplayer:**

```scala
resolvers += Resolver.JCenterRepository
```

---

## ackcord-data

```scala
libraryDependencies += "net.katsstuff" %%% "ackcord-data" % "{{versions.ackcord}}"
```

Provides access to all the data objects, encoders and decoders used by AckCord. This module is also crosscompiled for
Scala.Js. Wonderful if you want to build a web panel for your bot in Scala, and don't want to redefine all the data
objects.

## ackcord-requests

```scala
libraryDependencies += "net.katsstuff" %% "ackcord-requests" % "{{versions.ackcord}}"
```

Contains all the requests in AckCord.

## ackcord-gateway

```scala
libraryDependencies += "net.katsstuff" %% "ackcord-gateway" % "{{versions.ackcord}}"
```

Contains the code for the AckCord gateway implementation. Depend on this if you want to build custom logic from the
gateway and on.

## ackcord-voice

```scala
libraryDependencies += "net.katsstuff" %% "ackcord-voice" % "{{versions.ackcord}}"
```

Contains all the code for the voice stuff in AckCord.

## ackcord-commands

```scala
libraryDependencies += "net.katsstuff" %% "ackcord-commands" % "{{versions.ackcord}}"
```

The commands API module.

## ackcord-core

```scala
libraryDependencies += "net.katsstuff" %% "ackcord-core" % "{{versions.ackcord}}"
```

The low level API module of AckCord. Provides the in memory cache.

## ackcord-lavaplayer-core

```scala
libraryDependencies += "net.katsstuff" %% "ackcord-lavaplayer-core" % "{{versions.ackcord}}"
```

Provides an implementation to the the voice module using lavaplayer and core.

To use, first create an instance of the `LavaplayerHandler` actor. This actor should be shared for an entire guild.
Check the objects in the companion object of `LavaplayerHandler` for the valid messages you can send it.

## ackcord

```scala
libraryDependencies += "net.katsstuff" %% "ackcord" % "{{versions.ackcord}}"
```

The high level API, includes all the other modules.