ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.7.4"

lazy val root = (project in file("."))
  .settings(
    name := "PPS-24-DNTS",
    libraryDependencies +=
      "org.scalatest" %% "scalatest" % "3.2.18" % Test
  )
