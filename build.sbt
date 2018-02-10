import sbtcrossproject.{crossProject, CrossType}

lazy val akkaVersion     = "2.5.9"
lazy val akkaHttpVersion = "10.0.11"
lazy val circeVersion    = "0.9.0"
lazy val ackCordVersion  = "0.9.0"

lazy val commonSettings = Seq(
  scalaVersion := "2.12.4",
  organization := "net.katsstuff",
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-unchecked",
    //"-Xlint", //TODO: Enable again when Position.point on NoPosition is fixed
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

lazy val ackCordCore = project
  .settings(
    commonSettings,
    publishSettings,
    name := "ackcord-core",
    version := ackCordVersion,
    resolvers += JCenterRepository,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor"     % akkaVersion,
      "com.typesafe.akka" %% "akka-testkit"   % akkaVersion % Test,
      "com.typesafe.akka" %% "akka-stream"    % akkaVersion,
      "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion
    ),
    libraryDependencies += "de.heikoseeberger" %% "akka-http-circe" % "1.19.0",
    libraryDependencies += "org.scalatest"     %% "scalatest"       % "3.0.4" % Test,
    description := "AckCord is a Scala library using Akka for the Discord API giving as much freedom as possible to the user"
  )
  .dependsOn(ackCordDataJVM)

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
