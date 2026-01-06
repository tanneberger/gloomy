// See README.md for license details.

scalaVersion     := "2.13.16"
version          := "0.1.0"
organization     := "tud"

val chiselVersion = "7.0.0-RC1"
val gloomyVersion = "0.1.0"

lazy val root = (project in file("."))
  .settings(
    name := "test-gloomy",
    libraryDependencies ++= Seq(
      "org.chipsalliance" %% "chisel" % chiselVersion,
      "org.scalatest" %% "scalatest" % "3.2.19" % "test",
      "io.circe" %% "circe-yaml-v12" % "0.16.0",
      "io.circe" %% "circe-yaml" % "0.16.0",
      "io.circe" %% "circe-generic-extras" % "0.14.4",
    ),
    scalacOptions ++= Seq(
      "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-Xcheckinit",
      "-Ymacro-annotations",
    ),
    addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % chiselVersion cross CrossVersion.full),
  )
