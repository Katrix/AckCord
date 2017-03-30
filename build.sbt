lazy val akkaVersion = "2.4.17"
lazy val akkaHttpVersion = "10.0.4"

lazy val commonSettings = Seq(
  scalaVersion := "2.12.1"
)

lazy val akkaCord = project
  .settings(
    commonSettings,
    name := "AkkaCord",
    version := "0.1",
    resolvers += JCenterRepository,
      libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion
    )
  )

lazy val example = project
  .settings(
    commonSettings,
    name := "AkkaCord-example",
    version := "1.0"
  ).dependsOn(akkaCord)

lazy val akkaCordRoot = project.in(file(".")).aggregate(akkaCord, example)