logLevel                         := Level.Warn
addSbtPlugin("org.scala-js"       % "sbt-scalajs"              % "1.16.0")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.1.0")
addSbtPlugin("com.47deg"          % "sbt-microsites"           % "1.4.3")

val circeVersion = "0.14.1"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-yaml" % circeVersion
)

addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.11.1")