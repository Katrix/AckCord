# ackcord-core

This module provides gives you an in memory cache for AckCord. In addition to that, it also passes you normalized models
instead of the raw one gotten from the Discord API. This module also exposes the files GuildRouter and GuildStreams
which lets you handle events from different guilds separately.

```scala
libraryDependencies += "net.katsstuff" %% "ackcord-core" % "0.17.1"
```