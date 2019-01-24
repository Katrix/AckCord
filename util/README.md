# ackcord-util

This module contains all misc code for AckCord which does not require a specific cache implementation. More specifically, this module currently contains:
* Helpers for common things often done on models
* Dot syntax for creating request objects
* MessageParser to get objects from messages, including mentions and similar
* RequestRunner, an abstraction which lets you make requests while keeping you logic in a for block

```scala
libraryDependencies += "net.katsstuff" %% "ackcord-util" % "0.12.0"
```