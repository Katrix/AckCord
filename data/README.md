# ackcord-data

This module contains all the models found in the Discord API, but as they are gotten from the API, and normalized forms. Unlike all the other modules, this one is also cross built for Scala.JS, meaning that you can keep the models around on the client side of a webapp too.

```scala
libraryDependencies += "net.katsstuff" %% "ackcord-data" % "0.14.0"

//Or for Scala.Js stuff
libraryDependencies += "net.katsstuff" %%% "ackcord-data" % "0.14.0"
```