import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

lazy val akkaVersion     = "2.5.18"
lazy val akkaHttpVersion = "10.1.5"
lazy val circeVersion    = "0.10.1"
lazy val ackCordVersion  = "0.11.0-SNAPSHOT"

lazy val commonSettings = Seq(
  scalaVersion := "2.12.8",
  organization := "net.katsstuff",
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-unchecked",
    "-Xlint",
    "-Yno-adapted-args",
    "-Ywarn-dead-code",
    "-Ywarn-unused-import",
    "-Ypartial-unification"
  ),
  libraryDependencies += compilerPlugin("org.spire-math" %% "kind-projector" % "0.9.7"),
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
  licenses := Seq("MIT" -> url("http://opensource.org/licenses/MIT")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/Katrix/AckCord"),
      "scm:git:github.com/Katrix/AckCord",
      Some("scm:git:github.com/Katrix/AckCord")
    )
  ),
  homepage := Some(url("https://github.com/Katrix/AckCord")),
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
      "io.circe" %%% "circe-core"           % circeVersion,
      "io.circe" %%% "circe-parser"         % circeVersion,
      "io.circe" %%% "circe-generic-extras" % circeVersion,
      "io.circe" %%% "circe-derivation"     % "0.10.0-M1"
    ),
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
      "com.typesafe.akka" %% "akka-stream"    % akkaVersion,
      "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion
    ),
    libraryDependencies += "de.heikoseeberger" %% "akka-http-circe" % "1.22.0",
    description := "The base network module of AckCord"
  )
  .dependsOn(ackCordDataJVM)

lazy val ackCordRest = project
  .settings(
    commonSettings,
    publishSettings,
    name := "ackcord-rest",
    version := ackCordVersion,
    description := "The REST module of AckCord"
  )
  .dependsOn(ackCordNetwork)

lazy val ackCordImages = project
  .settings(
    commonSettings,
    publishSettings,
    name := "ackcord-images",
    version := ackCordVersion,
    description := "The image requests module of AckCord"
  )
  .dependsOn(ackCordNetwork)

lazy val ackCordOAuth = project
  .settings(
    commonSettings,
    publishSettings,
    name := "ackcord-oauth",
    version := ackCordVersion,
    description := "The OAuth requests module of AckCord"
  )
  .dependsOn(ackCordNetwork)

lazy val ackCordWebsocket = project
  .settings(
    commonSettings,
    publishSettings,
    name := "ackcord-websockets",
    version := ackCordVersion,
    description := "The base websockets module of AckCord"
  )
  .dependsOn(ackCordNetwork)

lazy val ackCordGateway = project
  .settings(
    commonSettings,
    publishSettings,
    name := "ackcord-gateway",
    version := ackCordVersion,
    description := "The gateway module of AckCord"
  )
  .dependsOn(ackCordWebsocket)

lazy val ackCordVoice = project
  .settings(
    commonSettings,
    publishSettings,
    name := "ackcord-voice",
    version := ackCordVersion,
    description := "The voice websocket module of AckCord"
  )
  .dependsOn(ackCordWebsocket)

lazy val ackCordUtil = project
  .settings(
    commonSettings,
    publishSettings,
    name := "ackcord-util",
    version := ackCordVersion,
    description := "The module that contains all utilities for AckCord that can be represented without a concrete cache"
  )
  .dependsOn(ackCordRest, ackCordImages, ackCordOAuth, ackCordGateway, ackCordVoice)

lazy val ackCordCommands = project
  .settings(
    commonSettings,
    publishSettings,
    name := "ackcord-commands",
    version := ackCordVersion,
    description := "ackCord-commands provides the basic code used for commands in AckCord"
  )
  .dependsOn(ackCordUtil)

lazy val ackCordLavaplayer = project
  .settings(
    commonSettings,
    publishSettings,
    name := "ackcord-lavaplayer",
    version := ackCordVersion,
    resolvers += JCenterRepository,
    libraryDependencies += "com.sedmelluq" % "lavaplayer" % "1.3.10",
    description := "ackCord-lavaplayer provides the basic code needed to use lavaplayer together with AckCord"
  )
  .dependsOn(ackCordVoice)

lazy val ackCordCore = project
  .settings(
    commonSettings,
    publishSettings,
    name := "ackcord-core",
    version := ackCordVersion,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
      "org.scalatest"     %% "scalatest"    % "3.0.4"     % Test
    ),
    description := "AckCord is a Scala library using Akka for the Discord API giving as much freedom as possible to the user"
  )
  .dependsOn(ackCordUtil)

lazy val ackCordCommandsCore = project
  .settings(
    commonSettings,
    publishSettings,
    name := "ackcord-commands-core",
    version := ackCordVersion,
    description := "ackCord-commands-core provides the glue code between ackcord-core and ackcord-commands"
  )
  .dependsOn(ackCordCore, ackCordCommands)

lazy val ackCordLavaplayerCore = project
  .settings(
    commonSettings,
    publishSettings,
    name := "ackcord-lavaplayer-core",
    version := ackCordVersion,
    description := "ackCord-lavaplayer-core provides the glue code between ackcord-core and ackcord-lavaplayer"
  )
  .dependsOn(ackCordCore, ackCordLavaplayer)

lazy val ackCord = project
  .settings(
    commonSettings,
    publishSettings,
    name := "ackcord",
    version := ackCordVersion,
    libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    description := "A higher level extension to AckCord so you don't have to deal with the lower level stuff as much"
  )
  .dependsOn(ackCordCore, ackCordCommandsCore, ackCordLavaplayerCore)

lazy val exampleCore = project
  .settings(
    commonSettings,
    noPublishSettings,
    name := "ackcord-exampleCore",
    version := "1.0",
    libraryDependencies += "com.typesafe.akka" %% "akka-slf4j"     % akkaVersion,
    libraryDependencies += "ch.qos.logback"    % "logback-classic" % "1.2.3"
  )
  .dependsOn(ackCordCore, ackCordCommandsCore, ackCordLavaplayerCore)

lazy val example = project
  .settings(
    commonSettings,
    noPublishSettings,
    name := "ackcord-example",
    version := "1.0",
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"
  )
  .dependsOn(ackCord)

lazy val docsMappingsAPIDir = settingKey[String]("Name of subdirectory in site target directory for api docs")

lazy val doc = project
  .enablePlugins(MicrositesPlugin, ScalaUnidocPlugin, GhpagesPlugin)
  .settings(commonSettings: _*)
  .settings(
    micrositeName := "AckCord",
    micrositeAuthor := "Katrix",
    micrositeDescription := "A Scala Discord library",
    micrositeBaseUrl := "",
    micrositeDocumentationUrl := "/api/net/katsstuff/ackcord",
    micrositeHomepage := "http://ackcord.katsstuff.net",
    micrositeGithubOwner := "Katrix",
    micrositeGithubRepo := "AckCord",
    micrositeGitterChannel := false,
    micrositeShareOnSocial := false,
    scalacOptions in Tut --= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xlint",
      "-Ywarn-dead-code",
      "-Ywarn-unused-import"
    ),
    scalacOptions in Tut ++= Seq("-language:higherKinds"),
    autoAPIMappings := true,
    unidocProjectFilter in (ScalaUnidoc, unidoc) := inProjects(
      ackCordDataJVM,
      ackCordNetwork,
      ackCordRest,
      ackCordImages,
      ackCordOAuth,
      ackCordWebsocket,
      ackCordGateway,
      ackCordVoice,
      ackCordUtil,
      ackCordCommands,
      ackCordLavaplayer,
      ackCordCore,
      ackCordCommandsCore,
      ackCordLavaplayerCore,
      ackCord
    ),
    docsMappingsAPIDir := "api",
    addMappingsToSiteDir(mappings in (ScalaUnidoc, packageDoc), docsMappingsAPIDir),
    fork in tut := true,
    fork in (ScalaUnidoc, unidoc) := true,
    scalacOptions in (ScalaUnidoc, unidoc) ++= Seq(
      "-doc-source-url",
      "https://github.com/Katrix/Ackcord/tree/master€{FILE_PATH}.scala",
      "-sourcepath",
      baseDirectory.in(LocalRootProject).value.getAbsolutePath,
      "-diagrams"
    )
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
    ackCordUtil,
    ackCordCommands,
    ackCordLavaplayer,
    ackCordCore,
    ackCordCommandsCore,
    ackCordLavaplayerCore,
    ackCord,
    exampleCore,
    example
  )
  .settings(
    noPublishSettings,
    //Fixes repository not specified error
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
      else Some("releases" at nexus + "service/local/staging/deploy/maven2")
    }
  )
