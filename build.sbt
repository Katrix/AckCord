import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}

lazy val circeVersion   = "0.14.2"
lazy val ackCordVersion = "2.0.0.0-SNAPSHOT"

lazy val generateData = taskKey[Unit]("Generate AckCord data classes")

lazy val commonSettings = Seq(
  scalaVersion       := "2.13.8",
  crossScalaVersions := Seq(scalaVersion.value),
  organization       := "net.katsstuff",
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-unchecked",
    "-Xlint",
    "-Ywarn-dead-code"
  ),
  libraryDependencies += compilerPlugin("org.typelevel" %% "kind-projector" % "0.13.2" cross CrossVersion.full),
  publishTo := sonatypePublishToBundle.value,
  generateData := {
    val sourceDir    = (Compile / sourceDirectory).value.getParentFile.getParentFile.getParentFile / "src" / "main"
    val scalaDir     = sourceDir / "scala"
    val generatedDir = sourceDir / "resources" / "generated"

    val files = generatedDir ** "*.yaml"

    files.get().foreach { yamlFile =>
      val relativeFile  = yamlFile.relativeTo(generatedDir).get
      val scalaPath     = scalaDir / s"${relativeFile.getParent}${Path.sep}${relativeFile.base}.scala"
      val generatedCode = AckCordCodeGen.generateCodeFromFile(generatedDir.toPath, yamlFile.toPath)
      IO.write(scalaPath, generatedCode)
    }
  }
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
      "io.circe" %%% "circe-core"   % circeVersion,
      "io.circe" %%% "circe-parser" % circeVersion
    ),
    description := "AckCord is a Scala library for the Discord API giving as much freedom as possible to the user"
  )

lazy val dataJVM = data.jvm
lazy val dataJS  = data.js

lazy val requests = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .dependsOn(data)
  .settings(
    commonSettings,
    publishSettings,
    name    := "requests",
    version := ackCordVersion,
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client3" %% "core"          % "3.8.15",
      "com.softwaremill.sttp.client3" %% "circe"         % "3.8.15",
      "com.softwaremill.sttp.client3" %% "cats"          % "3.8.15",
      "com.softwaremill.sttp.client3" %% "slf4j-backend" % "3.8.15"
    ),
    libraryDependencies += "org.slf4j"      % "slf4j-api"       % "2.0.1",
    libraryDependencies += "org.typelevel" %% "log4cats-slf4j"  % "2.6.0",
    libraryDependencies += "org.typelevel" %% "cats-effect-std" % "3.4.4",
    description                            := "The request module of AckCord"
  )

lazy val requestsJVM = requests.jvm
lazy val requestsJS  = requests.js

lazy val gateway = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .dependsOn(requests)
  .settings(
    commonSettings,
    publishSettings,
    name                                                   := "gateway",
    version                                                := ackCordVersion,
    libraryDependencies += "com.softwaremill.sttp.client3" %% "fs2"             % "3.8.15",
    libraryDependencies += "ch.qos.logback"                 % "logback-classic" % "1.4.7",
    description                                            := "The gateway module of AckCord"
  )

lazy val gatewayJVM = gateway.jvm
lazy val gatewayJS = gateway.js

lazy val interactions = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .dependsOn(gateway)
  .settings(
    commonSettings,
    publishSettings,
    name        := "interactions",
    version     := ackCordVersion,
    description := "The interactions module of AckCord"
  )

lazy val interactionsJVM = interactions.jvm
lazy val interactionsJS = interactions.js

lazy val ackcord = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .dependsOn(gateway)
  .settings(
    commonSettings,
    publishSettings,
    name := "ackcord",
    moduleName := "ackcord",
    version := ackCordVersion,
    description := "The high level API of AckCord"
  )

lazy val ackcordJVM = ackcord.jvm
lazy val ackcordJS = ackcord.js

lazy val example = project
  .settings(
    commonSettings,
    noPublishSettings,
    name                                   := "example",
    version                                := "1.0",
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.10"
  )
  .dependsOn(ackcordJVM)

lazy val docsMappingsAPIDir = settingKey[String]("Name of subdirectory in site target directory for api docs")

lazy val docs = project
  .enablePlugins(MicrositesPlugin, ScalaUnidocPlugin, GhpagesPlugin)
  .settings(
    commonSettings,
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
      dataJVM, requestsJVM, gatewayJVM, interactionsJVM
    ),
    Compile / doc / scalacOptions ++= Seq("-skip-packages", "com.iwebpp"),
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

lazy val ackCordRoot = project
  .in(file("."))
  .aggregate(
    dataJVM,
    dataJS
  )
  .settings(
    commonSettings,
    publishSettings,
    noPublishSettings,
    version := ackCordVersion
  )
