import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}

lazy val akkaVersion     = "2.6.0-RC1"
lazy val akkaHttpVersion = "10.1.10"
lazy val circeVersion    = "0.12.1"
lazy val ackCordVersion  = "0.14.0"

lazy val commonSettings = Seq(
  scalaVersion := "2.13.1",
  crossScalaVersions := Seq("2.12.10", scalaVersion.value),
  organization := "net.katsstuff",
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
  libraryDependencies += compilerPlugin("org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full),
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
      "io.circe" %%% "circe-derivation"     % "0.12.0-M3"
    ),
    libraryDependencies ++= Seq(
      "com.beachape" %%% "enumeratum"       % "1.5.13",
      "com.beachape" %%% "enumeratum-circe" % "1.5.22"
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
    Compile / doc / scalacOptions ++= Seq("-skip-packages", "akka.pattern")
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
    Compile / doc / scalacOptions ++= Seq("-skip-packages", "akka.pattern")
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
    libraryDependencies += "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
    Compile / doc / scalacOptions ++= Seq("-skip-packages", "com.iwebpp:akka.pattern")
  )
  .dependsOn(dataJVM)

lazy val commands = project
  .settings(
    commonSettings,
    publishSettings,
    name := "commands",
    version := ackCordVersion,
    libraryDependencies += "org.typelevel" %% "cats-mtl-core" % "0.7.0",
    description := "ackCord-commands provides the basic code used for commands in AckCord"
  )
  .dependsOn(requests)

lazy val commandsNew = project
  .settings(
    commonSettings,
    publishSettings,
    name := "commands-new",
    version := ackCordVersion,
    libraryDependencies += "org.typelevel" %% "cats-mtl-core" % "0.7.0",
    description := "ackCord-commands-new is an experiment to for better commands in AckCord"
  )
  .dependsOn(requests)

lazy val core = project
  .settings(
    commonSettings,
    publishSettings,
    name := "core",
    version := ackCordVersion,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
      "org.scalatest"     %% "scalatest"    % "3.0.8"     % Test
    ),
    description := "AckCord is a Scala library using Akka for the Discord API giving as much freedom as possible to the user"
  )
  .dependsOn(requests, gateway)

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
    libraryDependencies += "com.sedmelluq" % "lavaplayer" % "1.3.22",
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
    description := "A higher level extension to AckCord so you don't have to deal with the lower level stuff as much",
    Compile / doc / scalacOptions ++= Seq("-skip-packages", "akka.pattern")
  )
  .dependsOn(core, commandsCore, lavaplayerCore)

lazy val exampleCore = project
  .settings(
    commonSettings,
    noPublishSettings,
    name := "exampleCore",
    version := "1.0",
    mainClass := Some("ackcord.examplecore.Example"),
    libraryDependencies += "com.typesafe.akka" %% "akka-slf4j"     % akkaVersion,
    libraryDependencies += "ch.qos.logback"    % "logback-classic" % "1.2.3"
  )
  .dependsOn(core, commandsCore, lavaplayerCore, commandsNew)

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

lazy val docs = project
  .enablePlugins(MicrositesPlugin, ScalaUnidocPlugin, GhpagesPlugin)
  .settings(commonSettings: _*)
  .settings(
    micrositeName := "AckCord",
    micrositeAuthor := "Katrix",
    micrositeDescription := "A Scala Discord library",
    micrositeBaseUrl := "",
    micrositeDocumentationUrl := "/api/ackcord",
    micrositeHomepage := "http://ackcord.katsstuff.net",
    micrositeGithubOwner := "Katrix",
    micrositeGithubRepo := "AckCord",
    micrositeGitterChannel := false,
    micrositeShareOnSocial := false,
    excludeFilter in ghpagesCleanSite := "CNAME",
    scalacOptions in Compile ++= Seq("-language:higherKinds"),
    autoAPIMappings := true,
    unidocProjectFilter in (ScalaUnidoc, unidoc) := inProjects(
      dataJVM,
      requests,
      gateway,
      voice,
      commands,
      core,
      commandsCore,
      commandsNew,
      lavaplayerCore,
      ackCord
    ),
    Compile / doc / scalacOptions ++= Seq("-skip-packages", "com.iwebpp:akka.pattern"),
    docsMappingsAPIDir := "api",
    addMappingsToSiteDir(mappings in (ScalaUnidoc, packageDoc), docsMappingsAPIDir),
    micrositeCompilingDocsTool := WithMdoc,
    fork in mdoc := true,
    mdocIn := sourceDirectory.value / "main" / "mdoc",
    fork in (ScalaUnidoc, unidoc) := true,
    scalacOptions in (ScalaUnidoc, unidoc) ++= Seq(
      "-doc-source-url",
      "https://github.com/Katrix/Ackcord/tree/master€{FILE_PATH}.scala",
      "-sourcepath",
      baseDirectory.in(LocalRootProject).value.getAbsolutePath
    )
  )
  .dependsOn(ackCord, commandsNew)

lazy val ackCordRoot = project
  .in(file("."))
  .aggregate(
    dataJVM,
    dataJS,
    requests,
    gateway,
    voice,
    commands,
    core,
    commandsCore,
    commandsNew,
    lavaplayerCore,
    ackCord,
    exampleCore,
    example
  )
  .settings(
    noPublishSettings,
    //Fix some stupid issue where we were cross building to both 2.12.7 and 2.12.8
    crossScalaVersions := (dataJVM / crossScalaVersions).value,
    //Fixes repository not specified error
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
      else Some("releases" at nexus + "service/local/staging/deploy/maven2")
    }
  )
