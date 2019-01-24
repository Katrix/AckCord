# ackcord-lavaplayer
This module provides code to use Lavaplayer together with AckCord. There are two audio senders. The normal AudioSender, and the BurstingAudioSender. The normal one is probably technically more accurate, but it's also much slower. Prefer the bursting one for the most part. If you're using the in memory cache, then you want to depend on `ackcord-lavaplayer-core`, not this.

```scala
libraryDependencies += "net.katsstuff" %% "ackcord-lavaplayer" % "0.12.0"
```