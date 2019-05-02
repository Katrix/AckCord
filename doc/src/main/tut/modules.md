---
layout: docs
title: The many modules of AckCord
position: 2
---

# {{page.title}}
While the modules listed at the start are the main modules you want to depend on, AckCord has many more if you feel that you don't need the full package.

Here is a map of all the modules, and how they depend on each other
```
      +----> voice -----------------------------> lavaplayer-core -+ 
      |                                                 ^          |   
      |                                                 |          |   
      |                                                 |          v   
 data +----> requests -> util --+-> core ---------------+-----> ackcord
      |                         |    ^                  |          ^   
      |                         |    |                  |          |
      +----> gateway -----------|----+                  |          |   
                                |                       v          |
                                +-> commands ---> commands-core ---+
```

{% assign versions = site.data.versions %}

**Make sure to add the following line to your SBT file :**
```scala
resolvers += Resolver.JCenterRepository
```

---

## ackcord-data
```scala
libraryDependencies += "net.katsstuff" %%% "ackcord-data" % "{{versions.ackcord}}"
```
Provides access to all the data objects, encoders and decoders used by AckCord. This module is also crosscompiled for Scala.Js. Wonderful if you want to build a web panel for your bot in Scala, and don't want to redefine all the data objects.

## ackcord-network
```scala
libraryDependencies += "net.katsstuff" %% "ackcord-network" % "{{versions.ackcord}}"
```
The base network module of AckCord. You probably don't want to depend on this alone unless you're adding new requests to AckCord.

## ackcord-rest
```scala
libraryDependencies += "net.katsstuff" %% "ackcord-rest" % "{{versions.ackcord}}"
```
Contains all the rest requests in AckCord.

## ackcord-image
```scala
libraryDependencies += "net.katsstuff" %% "ackcord-image" % "{{versions.ackcord}}"
```
Contains all the image requests in AckCord. Here for completion sake, although if you only depend on this you should probably find a more light weight solution.

## ackcord-oauth
```scala
libraryDependencies += "net.katsstuff" %% "ackcord-oatuh" % "{{versions.ackcord}}"
```
Contains the building blocks for making an OAuth application using AckCord.

## ackcord-websockets
```scala
libraryDependencies += "net.katsstuff" %% "ackcord-websockets" % "{{versions.ackcord}}"
```
The base websockets module of AckCord. It makes even less sense to depend on this alone than the network module.

## ackcord-gateway
```scala
libraryDependencies += "net.katsstuff" %% "ackcord-gateway" % "{{versions.ackcord}}" //The gateway module of AckCord
```
Contains the code for the AckCord gateway implementation. Depend on this if you want to build custom logic from the gateway and on. You might want to consider `ackcord-util` instead though.

## ackcord-voice
```scala
libraryDependencies += "net.katsstuff" %% "ackcord-voice" % "{{versions.ackcord}}"
```
Contains all the code for the voice stuff in AckCord.

## ackcord-util
```scala
libraryDependencies += "net.katsstuff" %% "ackcord-util" % "{{versions.ackcord}}"
```
Misc utilities for AckCord that does not rely on the memory cache snapshot. If you want to build from the gateway and on, or want a custom cache implementation, this is probably what you want to depend on.

## ackcord-commands
```scala
libraryDependencies += "net.katsstuff" %% "ackcord-commands" % "{{versions.ackcord}}"
```
The base commands API module. Does not work without an implementation.

## ackcord-lavaplayuer
```scala
libraryDependencies += "net.katsstuff" %% "ackcord-lavaplayer" % "{{versions.ackcord}}"
```
AckCord provides a module to use Lavaplayer to send audio to Discord. Does not work without an implementation.

## ackcord-core
```scala
libraryDependencies += "net.katsstuff" %% "ackcord-core" % "{{versions.ackcord}}"
```
The low level API module of AckCord. Provides the in memory cache.

## ackcord-commands-core
```scala
libraryDependencies += "net.katsstuff" %% "ackcord-commands-core" % "{{versions.ackcord}}"
```
Provides an implementation for the commands module using core.

## ackcord-lavaplayer-core
```scala
libraryDependencies += "net.katsstuff" %% "ackcord-lavaplayer-core" % "{{versions.ackcord}}"
```
Provides an implementation for the lavaplayer module using core.

To use, first create an instance of the `LavaplayerHandler` actor. This actor should be shared for an entire guild. Check the objects in the companion object of `LavaplayerHandler` for the valid messages you can send it.

## ackcord
```scala
libraryDependencies += "net.katsstuff" %% "ackcord" % "{{versions.ackcord}}"
```
The high level API, includes all the other modules.