import $file.project.AckCordCodeGen
import $ivy.`io.chris-kipp::mill-ci-release::0.1.9`
import mill._
import mill.scalalib._
import mill.scalalib.publish._
import io.kipp.mill.ci.release.CiReleaseModule

//import project.AckCordCodeGen

trait AckCordModule extends SbtModule {
  override def scalaVersion = "2.13.8"
  override def scalacOptions = T {
    super.scalacOptions() ++ Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xlint",
      "-Ywarn-dead-code"
    )
  }

  override def scalacPluginIvyDeps: Target[Agg[Dep]] = T {
    super.scalacPluginIvyDeps() ++ Seq(
      ivy"org.typelevel:::kind-projector:0.13.2"
    )
  }

  def generateDataSources = T.input {
    val sourceDir    = millSourcePath / "src" / "main"
    val scalaDir     = sourceDir / "scala"
    val generatedDir = sourceDir / "resources" / "generated"

    if (os.exists(generatedDir)) os.walk(generatedDir, p => os.isDir(p) || p.ext != "yaml").filter(!os.isDir(_))
    else Nil
  }

  def generateData() = T.command {
    val sourceDir    = millSourcePath / "src" / "main"
    val scalaDir     = sourceDir / "scala"
    val generatedDir = sourceDir / "resources" / "generated"

    generateDataSources().map { yamlFile =>
      val relativeFileSegments = yamlFile.relativeTo(generatedDir).asSubPath.segments

      val scalaPath =
        scalaDir / relativeFileSegments.init / (relativeFileSegments.last.substring(0, ".yaml".length) + ".scala")

      val generatedCode = AckCordCodeGen.AckCordCodeGen.generateCodeFromFile(generatedDir.toNIO, yamlFile.toNIO)
      os.write(scalaPath, generatedCode)
      scalaPath
    }
  }
}

trait AckCordPublishModule extends AckCordModule with CiReleaseModule {
  override def versionScheme: Target[Option[VersionScheme]] = Some(VersionScheme.PVP)

  def publishDescription: T[String]

  override def pomSettings: T[PomSettings] = T {
    PomSettings(
      description = publishDescription(),
      organization = "net.katsstuff",
      url = "https://github.com/Katrix/AckCord",
      licenses = Seq(License.MIT),
      versionControl = VersionControl.github("Katrix", "AckCord"),
      developers = Seq(
        Developer("Katrix", "Kathryn Frid", "http://katsstuff.net")
      )
    )
  }
}

object ackcord extends AckCordPublishModule {
  object data extends AckCordPublishModule {
    override def ivyDeps = super.ivyDeps() ++ Agg(
      ivy"com.chuusai::shapeless:2.3.8",
      ivy"io.circe::circe-core:0.14.2",
      ivy"io.circe::circe-parser:0.14.2"
    )

    override def publishDescription: T[String] = "Data module of AckCord"
  }

  object requests extends AckCordPublishModule {
    override def moduleDeps = Seq(data)

    override def ivyDeps = super.ivyDeps() ++ Agg(
      ivy"com.softwaremill.sttp.client3::core:3.8.15",
      ivy"com.softwaremill.sttp.client3::circe:3.8.15",
      ivy"com.softwaremill.sttp.client3::cats:3.8.15",
      ivy"com.softwaremill.sttp.client3::slf4j-backend:3.8.15",
      ivy"org.slf4j:slf4j-api:2.0.1",
      ivy"org.typelevel::log4cats-slf4j:2.6.0",
      ivy"org.typelevel::cats-effect-std:3.4.4"
    )

    override def publishDescription: T[String] = "The request module of AckCord"
  }

  object gateway extends AckCordPublishModule {
    override def moduleDeps                    = Seq(requests)
    override def publishDescription: T[String] = "The gateway module of AckCord"
  }

  object interactions extends AckCordPublishModule {
    override def moduleDeps                    = Seq(gateway)
    override def publishDescription: T[String] = "The interactions module of AckCord"
  }

  override def moduleDeps = Seq(gateway)

  override def publishDescription: T[String] =
    "AckCord is a Scala library for the Discord API giving as much freedom as possible to the user"
}

object example extends AckCordModule {
  override def moduleDeps = Seq(ackcord, ackcord.interactions)

  override def ivyDeps = super.ivyDeps() ++ Agg(
    ivy"ch.qos.logback:logback-classic:1.4.7"
  )
}

object docs extends AckCordModule with UnidocModule {
  override def scalaVersion = "2.13.8"
  override def moduleDeps   = Seq(ackcord, ackcord.data, ackcord.requests, ackcord.gateway, ackcord.interactions)
}
