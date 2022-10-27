logLevel                         := Level.Warn
addSbtPlugin("org.scala-js"       % "sbt-scalajs"              % "1.9.0")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.1.0")
addSbtPlugin("com.47deg"          % "sbt-microsites"           % "1.3.4")
addSbtPlugin("org.xerial.sbt"     % "sbt-sonatype"             % "3.9.7")

val circeVersion = "0.14.1"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-yaml" % circeVersion
)
