lazy val akkaVersion     = "2.5.7"
lazy val akkaHttpVersion = "10.0.10"
val circeVersion         = "0.8.0"

lazy val commonSettings = Seq(scalaVersion := "2.12.4", organization := "net.katsstuff")

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ =>
    false
  },
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
    else Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  licenses := Seq("MIT" -> url("http://opensource.org/licenses/MIT")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/Katrix-/AckCord"),
      "scm:git:github.com/Katrix-/AckCord",
      Some("scm:git:github.com/Katrix-/AckCord")
    )
  ),
  homepage := Some(url("https://github.com/Katrix-/AckCord")),
  developers := List(Developer("Katrix", "Nikolai Frid", "katrix97@hotmail.com", url("http://katsstuff.net/")))
)

lazy val ackCord = project
  .settings(
    commonSettings,
    publishSettings,
    name := "ackcord",
    version := "0.7",
    resolvers += JCenterRepository,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor"     % akkaVersion,
      "com.typesafe.akka" %% "akka-testkit"   % akkaVersion % Test,
      "com.typesafe.akka" %% "akka-stream"    % akkaVersion,
      "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion
    ),
    libraryDependencies += "de.heikoseeberger" %% "akka-http-circe" % "1.18.1",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic-extras",
      "io.circe" %% "circe-shapes",
      "io.circe" %% "circe-parser"
    ).map(_ % circeVersion),
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.4" % Test,
    description := "AckCord is a Scala library using Akka for the Discord API giving as much freedom as possible to the user"
  )

lazy val example = project
  .settings(
    commonSettings,
    name := "ackcord-example",
    version := "1.0",
    libraryDependencies += "com.sedmelluq"     % "lavaplayer"      % "1.2.42",
    libraryDependencies += "com.typesafe.akka" %% "akka-slf4j"     % "2.5.6",
    libraryDependencies += "ch.qos.logback"    % "logback-classic" % "1.2.3"
  )
  .dependsOn(ackCord, ackCordCommands)

lazy val ackCordCommands = project
  .settings(
    commonSettings,
    publishSettings,
    name := "ackcord-commands",
    version := "0.7",
    description := "AckCord-commands is an extension to AckCord to allow one to easily define commands"
  )
  .dependsOn(ackCord)

lazy val benchmark = project
  .settings(
    commonSettings,
    name := "ackcord-benchmark",
    version := "1.0",
    resolvers += "JitPack" at "https://jitpack.io",
    resolvers += JCenterRepository,
    libraryDependencies += "org.openjdk.jol"      % "jol-core"  % "0.9",
    libraryDependencies += "com.github.austinv11" % "Discord4j" % "dev-SNAPSHOT",
    libraryDependencies += "net.dv8tion" % "JDA" % "3.3.0_260"
  )
  .dependsOn(ackCord)

lazy val ackCordRoot = project
  .in(file("."))
  .aggregate(ackCord, example, ackCordCommands)
  .settings(publish := {}, publishLocal := {}, publishArtifact := false)
