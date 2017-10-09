lazy val akkaVersion     = "2.5.6"
lazy val akkaHttpVersion = "10.0.10"
val circeVersion         = "0.8.0"

lazy val commonSettings = Seq(scalaVersion := "2.12.2")

lazy val ackCord = project
  .settings(
    commonSettings,
    name := "AckCord",
    version := "0.1",
    resolvers += JCenterRepository,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor"     % akkaVersion,
      "com.typesafe.akka" %% "akka-stream"    % akkaVersion,
      "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion
    ),
    libraryDependencies += "de.heikoseeberger" %% "akka-http-circe" % "1.16.0",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core",
      //"io.circe" %% "circe-generic",
      "io.circe" %% "circe-generic-extras",
      "io.circe" %% "circe-shapes",
      "io.circe" %% "circe-parser"
    ).map(_ % circeVersion)
  )

lazy val example = project
  .settings(commonSettings, name := "AckCord-example", version := "1.0")
  .dependsOn(ackCord)

lazy val ackCordRoot = project.in(file(".")).aggregate(ackCord, example)
