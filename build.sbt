import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}

lazy val akkaVersion          = "2.5.22"
lazy val akkaHttpVersion      = "10.1.8"
lazy val circeVersion         = "0.11.1"
lazy val akkaHttpCirceVersion = "1.25.2"
lazy val ackCordVersion       = "0.12.0"

lazy val commonSettings = Seq(
  scalaVersion := "2.12.8",
  crossScalaVersions := Seq(scalaVersion.value, "2.11.12"),
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
  scalacOptions ++= (if (scalaVersion.value.startsWith("2.11")) Seq("-Xexperimental") else Nil),
  libraryDependencies += compilerPlugin("org.spire-math" %% "kind-projector" % "0.9.10"),
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
  moduleName := {
    val old = moduleName.value
    if (old.toLowerCase.startsWith("ackcord")) old else s"ackcord-$old"
  },
  homepage := Some(url("https://github.com/Katrix/AckCord")),
  developers := List(Developer("Katrix", "Nikolai Frid", "katrix97@hotmail.com", url("http://katsstuff.net/"))),
  autoAPIMappings := true
)

lazy val noPublishSettings = Seq(publish := {}, publishLocal := {}, publishArtifact := false)

lazy val data = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(
    commonSettings,
    publishSettings,
    name := "data",
    version := ackCordVersion,
    libraryDependencies += "com.chuusai" %%% "shapeless" % "2.3.3",
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core"           % circeVersion,
      "io.circe" %%% "circe-parser"         % circeVersion,
      "io.circe" %%% "circe-generic-extras" % circeVersion,
      "io.circe" %%% "circe-derivation"     % "0.12.0-M1"
    ),
    description := "AckCord is a Scala library using Akka for the Discord API giving as much freedom as possible to the user"
  )

lazy val dataJVM = data.jvm
lazy val dataJS  = data.js

lazy val requests = project
  .settings(
    commonSettings,
    publishSettings,
    name := "requests",
    version := ackCordVersion,
    description := "The request module of AckCord",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor"     % akkaVersion,
      "com.typesafe.akka" %% "akka-stream"    % akkaVersion,
      "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion
    ),
    libraryDependencies += "de.heikoseeberger" %% "akka-http-circe" % akkaHttpCirceVersion,
    libraryDependencies += "com.typesafe.akka" %% "akka-http"       % akkaHttpVersion //Need to add this because of akka-http-circe
  )
  .dependsOn(dataJVM)

lazy val gateway = project
  .settings(
    commonSettings,
    publishSettings,
    name := "gateway",
    version := ackCordVersion,
    description := "The gateway module of AckCord",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor"     % akkaVersion,
      "com.typesafe.akka" %% "akka-stream"    % akkaVersion,
      "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion
    ),
    libraryDependencies += "de.heikoseeberger" %% "akka-http-circe" % akkaHttpCirceVersion,
  )
  .dependsOn(dataJVM)

lazy val voice = project
  .settings(
    commonSettings,
    publishSettings,
    name := "voice",
    version := ackCordVersion,
    description := "The voice websocket module of AckCord",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor"     % akkaVersion,
      "com.typesafe.akka" %% "akka-stream"    % akkaVersion,
      "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion
    ),
    libraryDependencies += "de.heikoseeberger" %% "akka-http-circe"     % akkaHttpCirceVersion,
    libraryDependencies += "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test
  )
  .dependsOn(dataJVM)

lazy val util = project
  .settings(
    commonSettings,
    publishSettings,
    name := "util",
    version := ackCordVersion,
    description := "The module that contains all utilities for AckCord that can be represented without a concrete cache"
  )
  .dependsOn(requests)

lazy val commands = project
  .settings(
    commonSettings,
    publishSettings,
    name := "commands",
    version := ackCordVersion,
    description := "ackCord-commands provides the basic code used for commands in AckCord"
  )
  .dependsOn(util)

lazy val core = project
  .settings(
    commonSettings,
    publishSettings,
    name := "core",
    version := ackCordVersion,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
      "org.scalatest"     %% "scalatest"    % "3.0.7"     % Test
    ),
    description := "AckCord is a Scala library using Akka for the Discord API giving as much freedom as possible to the user"
  )
  .dependsOn(util, gateway)

lazy val commandsCore = project
  .settings(
    commonSettings,
    publishSettings,
    name := "commands-core",
    version := ackCordVersion,
    description := "ackCord-commands-core provides the glue code between ackcord-core and ackcord-commands"
  )
  .dependsOn(core, commands)

lazy val lavaplayerCore = project
  .settings(
    commonSettings,
    publishSettings,
    name := "lavaplayer-core",
    version := ackCordVersion,
    //Workaround for https://github.com/sbt/sbt/issues/4479
    resolvers += MavenRepository(Resolver.JCenterRepositoryName, Resolver.JCenterRepositoryRoot + "net/.."),
    libraryDependencies += "com.sedmelluq" % "lavaplayer" % "1.3.17",
    description := "ackCord-lavaplayer-core provides the glue code between ackcord-core and ackcord-lavaplayer"
  )
  .dependsOn(core, voice)

lazy val ackCord = project
  .settings(
    commonSettings,
    publishSettings,
    name := "ackcord",
    version := ackCordVersion,
    libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    description := "A higher level extension to AckCord so you don't have to deal with the lower level stuff as much"
  )
  .dependsOn(core, commandsCore, lavaplayerCore)

lazy val exampleCore = project
  .settings(
    commonSettings,
    noPublishSettings,
    name := "exampleCore",
    version := "1.0",
    libraryDependencies += "com.typesafe.akka" %% "akka-slf4j"     % akkaVersion,
    libraryDependencies += "ch.qos.logback"    % "logback-classic" % "1.2.3"
  )
  .dependsOn(core, commandsCore, lavaplayerCore)

lazy val example = project
  .settings(
    commonSettings,
    noPublishSettings,
    name := "example",
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
      dataJVM,
      requests,
      gateway,
      voice,
      util,
      commands,
      core,
      commandsCore,
      lavaplayerCore,
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
    dataJVM,
    dataJS,
    requests,
    gateway,
    voice,
    util,
    commands,
    core,
    commandsCore,
    lavaplayerCore,
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
