import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}

lazy val circeVersion = "0.14.2"

lazy val generateData = taskKey[Unit]("Generate AckCord data classes")

lazy val commonSettings = Seq(
  scalaVersion       := "2.13.14",
  crossScalaVersions := Seq(scalaVersion.value, "3.3.2"),
  scalacOptions ++= {
    if (scalaVersion.value.startsWith("2"))
      Seq(
        "-deprecation",
        "-feature",
        "-unchecked",
        "-Xlint",
        "-Wdead-code"
      )
    else
      Seq(
        "-deprecation",
        "-feature",
        "-unchecked",
        "-Wunused:all",
        //"-Xlint",
        "-Ykind-projector"
      )
  },
  libraryDependencies ++= {
    if (scalaVersion.value.startsWith("2"))
      Seq(compilerPlugin("org.typelevel" %% "kind-projector" % "0.13.3" cross CrossVersion.full))
    else Nil
  },
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

inThisBuild(
  Seq(
    homepage      := Some(url("https://github.com/Katrix/AckCord")),
    organization  := "net.katsstuff",
    licenses      := Seq("MIT" -> url("http://opensource.org/licenses/MIT")),
    developers    := List(Developer("Katrix", "Kathryn Frid", "katrix97@hotmail.com", url("http://katsstuff.net/"))),
    versionScheme := Some("pvp")
  )
)

lazy val publishSettings = Seq(
  Test / publishArtifact := false,
  moduleName := {
    val old = moduleName.value
    s"ackcord-$old"
  },
  autoAPIMappings := true
)

lazy val noPublishSettings = Seq(publish := {}, publishLocal := {}, publishArtifact := false, publish / skip := true)

lazy val data = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(
    commonSettings,
    publishSettings,
    name := "data",
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
    name := "requests",
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
    description                                            := "The gateway module of AckCord",
    libraryDependencies += "com.softwaremill.sttp.client3" %% "fs2" % "3.8.15"
  )

lazy val gatewayJVM = gateway.jvm
lazy val gatewayJS  = gateway.js

lazy val interactions = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .dependsOn(gateway)
  .settings(
    commonSettings,
    publishSettings,
    name        := "interactions",
    description := "The interactions module of AckCord"
  )

lazy val interactionsJVM = interactions.jvm
lazy val interactionsJS  = interactions.js

lazy val ackcord = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .dependsOn(gateway)
  .settings(
    commonSettings,
    publishSettings,
    name        := "ackcord",
    moduleName  := "ackcord",
    description := "The high level API of AckCord"
  )

lazy val ackcordJVM = ackcord.jvm
lazy val ackcordJS  = ackcord.js

lazy val example = project
  .settings(
    commonSettings,
    noPublishSettings,
    name                                   := "example",
    version                                := "1.0",
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.4.7"
  )
  .dependsOn(ackcordJVM, interactionsJVM)

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
      dataJVM,
      requestsJVM,
      gatewayJVM,
      interactionsJVM,
      ackcordJVM
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
    dataJS,
    requestsJVM,
    requestsJS,
    gatewayJVM,
    gatewayJS,
    interactionsJVM,
    interactionsJS,
    ackcordJVM,
    ackcordJS
  )
  .settings(
    commonSettings,
    publishSettings,
    noPublishSettings
  )
