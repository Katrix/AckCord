import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}

lazy val akkaVersion     = "2.6.18"
lazy val akkaHttpVersion = "10.2.8"
lazy val circeVersion    = "0.13.0"
lazy val ackCordVersion  = "0.19.0-SNAPSHOT"

lazy val commonSettings = Seq(
  scalaVersion       := "2.13.8",
  crossScalaVersions := Seq("2.12.12", scalaVersion.value),
  organization       := "net.katsstuff",
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-unchecked",
    "-Xlint",
    "-Ywarn-dead-code"
  ),
  scalacOptions ++= (
    if (scalaVersion.value.startsWith("2.12"))
      Seq("-Yno-adapted-args", "-Ywarn-unused-import", "-Ypartial-unification", "-language:higherKinds")
    else Nil
  ),
  libraryDependencies += compilerPlugin("org.typelevel" %% "kind-projector" % "0.13.2" cross CrossVersion.full),
  publishTo := sonatypePublishToBundle.value
)

lazy val publishSettings = Seq(
  publishMavenStyle      := true,
  Test / publishArtifact := false,
  licenses               := Seq("MIT" -> url("http://opensource.org/licenses/MIT")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/Katrix/AckCord"),
      "scm:git:github.com/Katrix/AckCord",
      Some("scm:git:github.com/Katrix/AckCord")
    )
  ),
  moduleName := {
    val old = moduleName.value
    s"ackcord-$old"
  },
  homepage        := Some(url("https://github.com/Katrix/AckCord")),
  developers      := List(Developer("Katrix", "Kathryn Frid", "katrix97@hotmail.com", url("http://katsstuff.net/"))),
  autoAPIMappings := true
)

lazy val noPublishSettings = Seq(publish := {}, publishLocal := {}, publishArtifact := false)

lazy val data = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(
    commonSettings,
    publishSettings,
    name                                  := "data",
    version                               := ackCordVersion,
    libraryDependencies += "com.chuusai" %%% "shapeless" % "2.3.8",
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core"           % circeVersion,
      "io.circe" %%% "circe-parser"         % circeVersion,
      "io.circe" %%% "circe-generic-extras" % circeVersion,
      "io.circe" %%% "circe-derivation"     % "0.13.0-M5"
    ),
    libraryDependencies ++= Seq(
      "com.beachape" %%% "enumeratum"       % "1.7.0",
      "com.beachape" %%% "enumeratum-circe" % "1.7.0"
    ),
    description := "AckCord is a Scala library using Akka for the Discord API giving as much freedom as possible to the user"
  )

lazy val dataJVM = data.jvm
lazy val dataJS  = data.js

lazy val gatewayData = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(
    commonSettings,
    publishSettings,
    name    := "gateway-data",
    version := ackCordVersion
  )
  .dependsOn(data)

lazy val gatewayDataJVM = gatewayData.jvm
lazy val gatewayDataJS  = gatewayData.js

lazy val requests = project
  .settings(
    commonSettings,
    publishSettings,
    name        := "requests",
    version     := ackCordVersion,
    description := "The request module of AckCord",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed"  % akkaVersion,
      "com.typesafe.akka" %% "akka-stream-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-http-core"    % akkaHttpVersion
    ),
    Compile / doc / scalacOptions ++= Seq("-skip-packages", "akka.pattern")
  )
  .dependsOn(dataJVM)

lazy val gateway = project
  .settings(
    commonSettings,
    publishSettings,
    name        := "gateway",
    version     := ackCordVersion,
    description := "The gateway module of AckCord",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed"  % akkaVersion,
      "com.typesafe.akka" %% "akka-stream-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-http-core"    % akkaHttpVersion
    ),
    Compile / doc / scalacOptions ++= Seq("-skip-packages", "akka.pattern")
  )
  .dependsOn(gatewayDataJVM)

lazy val voice = project
  .settings(
    commonSettings,
    publishSettings,
    name        := "voice",
    version     := ackCordVersion,
    description := "The voice websocket module of AckCord",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed"  % akkaVersion,
      "com.typesafe.akka" %% "akka-stream-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-http-core"    % akkaHttpVersion
    ),
    libraryDependencies += "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
    Compile / doc / scalacOptions ++= Seq("-skip-packages", "com.iwebpp:akka.pattern")
  )
  .dependsOn(dataJVM)

lazy val commands = project
  .settings(
    commonSettings,
    publishSettings,
    name                                   := "commands",
    version                                := ackCordVersion,
    libraryDependencies += "org.typelevel" %% "cats-mtl-core" % "0.7.1",
    description                            := "ackCord-commands provides a Play like commands framework for AckCord"
  )
  .dependsOn(requests)

val interactions = project
  .settings(
    commonSettings,
    publishSettings,
    name        := "interactions",
    version     := ackCordVersion,
    description := "ackCord-interactions provides a high level API to interact with Discord's interactions"
  )
  .dependsOn(requests)

lazy val core = project
  .settings(
    commonSettings,
    publishSettings,
    name    := "core",
    version := ackCordVersion,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
      "org.scalatest"     %% "scalatest"    % "3.2.11"    % Test
    ),
    description := "AckCord is a Scala library using Akka for the Discord API giving as much freedom as possible to the user"
  )
  .dependsOn(requests, gateway)

lazy val lavaplayerCore = project
  .settings(
    commonSettings,
    publishSettings,
    name    := "lavaplayer-core",
    version := ackCordVersion,
    resolvers += Resolver.JCenterRepository,
    resolvers += "dv8tion" at "https://m2.dv8tion.net/releases",
    libraryDependencies += "com.sedmelluq" % "lavaplayer" % "1.3.78",
    description := "ackCord-lavaplayer-core provides the glue code between ackcord-core and ackcord-lavaplayer"
  )
  .dependsOn(core, voice)

lazy val ackCord = project
  .settings(
    commonSettings,
    publishSettings,
    name                                       := "ackcord",
    version                                    := ackCordVersion,
    libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    moduleName                                 := "ackcord",
    description := "A higher level extension to AckCord so you don't have to deal with the lower level stuff as much",
    Compile / doc / scalacOptions ++= Seq("-skip-packages", "akka.pattern")
  )
  .dependsOn(core, lavaplayerCore, commands, interactions)

lazy val exampleCore = project
  .settings(
    commonSettings,
    noPublishSettings,
    name                                       := "exampleCore",
    version                                    := "1.0",
    Compile / mainClass                        := Some("ackcord.examplecore.Example"),
    libraryDependencies += "com.typesafe.akka" %% "akka-slf4j"      % akkaVersion,
    libraryDependencies += "ch.qos.logback"     % "logback-classic" % "1.2.10"
  )
  .dependsOn(core, lavaplayerCore, commands, interactions)

lazy val example = project
  .settings(
    commonSettings,
    noPublishSettings,
    name                                   := "example",
    version                                := "1.0",
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.10"
  )
  .dependsOn(ackCord)

lazy val docsMappingsAPIDir = settingKey[String]("Name of subdirectory in site target directory for api docs")

lazy val docs = project
  .enablePlugins(MicrositesPlugin, ScalaUnidocPlugin, GhpagesPlugin)
  .settings(commonSettings: _*)
  .settings(
    micrositeName                          := "AckCord",
    micrositeAuthor                        := "Katrix",
    micrositeDescription                   := "A Discord library built for flexibility and speed",
    micrositeDocumentationUrl              := "/api/ackcord",
    micrositeDocumentationLabelDescription := "ScalaDoc",
    micrositeHomepage                      := "https://ackcord.katsstuff.net",
    micrositeGithubOwner                   := "Katrix",
    micrositeGithubRepo                    := "AckCord",
    micrositeGitterChannel                 := false,
    micrositeShareOnSocial                 := false,
    micrositeTheme                         := "pattern",
    ghpagesCleanSite / excludeFilter       := "CNAME",
    micrositePushSiteWith                  := GitHub4s,
    micrositeGithubToken                   := sys.env.get("GITHUB_TOKEN"),
    Compile / scalacOptions ++= Seq("-language:higherKinds"),
    autoAPIMappings := true,
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(
      dataJVM,
      gatewayDataJVM,
      requests,
      gateway,
      voice,
      interactions,
      core,
      commands,
      lavaplayerCore,
      ackCord
    ),
    Compile / doc / scalacOptions ++= Seq("-skip-packages", "com.iwebpp:akka.pattern"),
    docsMappingsAPIDir := "api",
    addMappingsToSiteDir(ScalaUnidoc / packageDoc / mappings, docsMappingsAPIDir),
    //mdoc / fork := true,
    mdocIn := sourceDirectory.value / "main" / "mdoc",
    //ScalaUnidoc / unidoc / fork := true,
    ScalaUnidoc / unidoc / scalacOptions ++= Seq(
      "-doc-source-url",
      "https://github.com/Katrix/Ackcord/tree/master€{FILE_PATH}.scala",
      "-sourcepath",
      (LocalRootProject / baseDirectory).value.getAbsolutePath
    )
  )
  .dependsOn(ackCord)

lazy val ackCordRoot = project
  .in(file("."))
  .aggregate(
    dataJVM,
    dataJS,
    gatewayDataJVM,
    gatewayDataJS,
    requests,
    gateway,
    voice,
    interactions,
    core,
    commands,
    lavaplayerCore,
    ackCord,
    exampleCore,
    example
  )
  .settings(
    commonSettings,
    publishSettings,
    noPublishSettings,
    version := ackCordVersion
  )
