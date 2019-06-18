# ackcord-gateway

This module contains all the code needed to connect to the Discord gateway. If you want, you can connect directly to the gateway itself, passing it a Source and Sink to send and receive events. When doing so you receive the objects as raw as they can be. No cache, and no normalization.

```scala
libraryDependencies += "net.katsstuff" %% "ackcord-gateway" % "0.14.0"
```