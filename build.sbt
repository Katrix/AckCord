import sbtcrossproject.{crossProject, CrossType}

lazy val akkaVersion     = "2.5.11"
lazy val akkaHttpVersion = "10.1.0"
lazy val circeVersion    = "0.9.3"
lazy val ackCordVersion  = "0.9.0"

lazy val commonSettings = Seq(
  scalaVersion := "2.12.5",
  organization := "net.katsstuff",
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-unchecked",
    "-Xlint",
    "-Yno-adapted-args",
    "-Ywarn-dead-code",
    "-Ywarn-unused-import"
  ),
  //Fixes repository not specified error
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
    else Some("releases" at nexus + "service/local/staging/deploy/maven2")
  }
)

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ =>
    false
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
  developers := List(Developer("Katrix", "Nikolai Frid", "katrix97@hotmail.com", url("http://katsstuff.net/"))),
  autoAPIMappings := true
)

lazy val noPublishSettings = Seq(publish := {}, publishLocal := {}, publishArtifact := false)

lazy val ackCordData = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(
    commonSettings,
    publishSettings,
    name := "ackcord-data",
    version := ackCordVersion,
    libraryDependencies += "com.chuusai" %%% "shapeless" % "2.3.3",
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core",
      "io.circe" %%% "circe-generic-extras",
      "io.circe" %%% "circe-shapes",
      "io.circe" %%% "circe-parser"
    ).map(_ % circeVersion),
    description := "AckCord is a Scala library using Akka for the Discord API giving as much freedom as possible to the user"
  )

lazy val ackCordDataJVM = ackCordData.jvm
lazy val ackCordDataJS  = ackCordData.js

lazy val ackCordNetwork = project
  .settings(
    commonSettings,
    publishSettings,
    name := "ackcord-network",
    version := ackCordVersion,
    resolvers += JCenterRepository,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor"     % akkaVersion,
      "com.typesafe.akka" %% "akka-testkit"   % akkaVersion % Test,
      "com.typesafe.akka" %% "akka-stream"    % akkaVersion,
      "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion
    ),
    libraryDependencies += "de.heikoseeberger" %% "akka-http-circe" % "1.20.0",
    libraryDependencies += "org.scalatest"     %% "scalatest"       % "3.0.4" % Test,
    description := "The base network module of AckCord"
  ).dependsOn(ackCordDataJVM)

lazy val ackCordRest = project
  .settings(
    commonSettings,
    publishSettings,
    name := "ackcord-rest",
    version := ackCordVersion,
    description := "The REST module of AckCord"
  ).dependsOn(ackCordNetwork)

lazy val ackCordImages = project
  .settings(
    commonSettings,
    publishSettings,
    name := "ackcord-rest",
    version := ackCordVersion,
    description := "The image requests module of AckCord"
  ).dependsOn(ackCordNetwork)

lazy val ackCordOAuth = project
  .settings(
    commonSettings,
    publishSettings,
    name := "ackcord-rest",
    version := ackCordVersion,
    description := "The OAuth requests module of AckCord"
  ).dependsOn(ackCordNetwork)

lazy val ackCordWebsocket = project
  .settings(
    commonSettings,
    publishSettings,
    name := "ackcord-websockets",
    version := ackCordVersion,
    description := "The base websockets module of AckCord"
  ).dependsOn(ackCordNetwork)

lazy val ackCordGateway = project
  .settings(
    commonSettings,
    publishSettings,
    name := "ackcord-gateway",
    version := ackCordVersion,
    description := "The gateway module of AckCord"
  ).dependsOn(ackCordWebsocket)

lazy val ackCordVoice = project
  .settings(
    commonSettings,
    publishSettings,
    name := "ackcord-Voice",
    version := ackCordVersion,
    description := "The voice websocket module of AckCord"
  ).dependsOn(ackCordWebsocket)

lazy val ackCordUtil = project
  .settings(
    commonSettings,
    publishSettings,
    name := "ackcord-util",
    version := ackCordVersion,
    description := "The module that contains all utilities for AckCord that can be represented without a concrete cache"
  ).dependsOn(ackCordRest, ackCordImages, ackCordOAuth, ackCordGateway, ackCordVoice)

lazy val ackCordCore = project
  .settings(
    commonSettings,
    publishSettings,
    name := "ackcord-core",
    version := ackCordVersion,
    description := "AckCord is a Scala library using Akka for the Discord API giving as much freedom as possible to the user"
  )
  .dependsOn(ackCordUtil)

lazy val ackCordCommands = project
  .settings(
    commonSettings,
    publishSettings,
    name := "ackcord-commands",
    version := ackCordVersion,
    description := "AckCord-commands is an extension to AckCord to allow one to easily define commands"
  )
  .dependsOn(ackCordCore)

lazy val ackCordLavaplayer = project
  .settings(
    commonSettings,
    publishSettings,
    name := "ackcord-lavaplayer",
    version := ackCordVersion,
    libraryDependencies += "com.sedmelluq" % "lavaplayer" % "1.2.45",
    description := "AckCord-lavaplayer an extension to AckCord to help you integrate with lavaplayer"
  )
  .dependsOn(ackCordCore)

lazy val ackCord = project
  .settings(
    commonSettings,
    publishSettings,
    name := "ackcord",
    version := ackCordVersion,
    libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    description := "AckCord-highlvl is a higher level extension to AckCord so you don't have to deal with the lower level stuff as much"
  )
  .dependsOn(ackCordCore, ackCordCommands, ackCordLavaplayer)

lazy val exampleCore = project
  .settings(
    commonSettings,
    noPublishSettings,
    name := "ackcord-exampleCore",
    version := "1.0",
    libraryDependencies += "com.typesafe.akka" %% "akka-slf4j"     % akkaVersion,
    libraryDependencies += "ch.qos.logback"    % "logback-classic" % "1.2.3"
  )
  .dependsOn(ackCordCore, ackCordCommands, ackCordLavaplayer)

lazy val example = project
  .settings(
    commonSettings,
    noPublishSettings,
    name := "ackcord-example",
    version := "1.0",
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"
  )
  .dependsOn(ackCord)

lazy val ackCordRoot = project
  .in(file("."))
  .aggregate(
    ackCordDataJVM,
    ackCordDataJS,
    ackCordNetwork,
    ackCordRest,
    ackCordImages,
    ackCordOAuth,
    ackCordWebsocket,
    ackCordGateway,
    ackCordVoice,
    ackCordCore,
    ackCordCommands,
    ackCordLavaplayer,
    ackCord,
    exampleCore,
    example
  )
  .settings(
    noPublishSettings,
    unidocProjectFilter in (ScalaUnidoc, unidoc) := inAnyProject -- inProjects(example, exampleCore),
    //Fixes repository not specified error
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
      else Some("releases" at nexus + "service/local/staging/deploy/maven2")
    }
  )
  .enablePlugins(ScalaUnidocPlugin)
