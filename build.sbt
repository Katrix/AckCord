lazy val akkaVersion     = "2.4.17"
lazy val akkaHttpVersion = "10.0.4"
val circeVersion         = "0.7.0"

lazy val commonSettings = Seq(scalaVersion := "2.12.1")

lazy val akkaCord = project
  .settings(
    commonSettings,
    name := "AkkaCord",
    version := "0.1",
    resolvers += JCenterRepository,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor"     % akkaVersion,
      "com.typesafe.akka" %% "akka-stream"    % akkaVersion,
      "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion
    ),
    libraryDependencies += "de.heikoseeberger" %% "akka-http-circe" % "1.14.0",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core",
      //"io.circe" %% "circe-generic",
      "io.circe" %% "circe-generic-extras",
      "io.circe" %% "circe-shapes",
      "io.circe" %% "circe-parser"
    ).map(_ % circeVersion)
  )

lazy val example = project
  .settings(commonSettings, name := "AkkaCord-example", version := "1.0")
  .dependsOn(akkaCord)

lazy val akkaCordRoot = project.in(file(".")).aggregate(akkaCord, example)
