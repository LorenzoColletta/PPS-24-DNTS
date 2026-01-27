ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.7.4"

lazy val root = (project in file("."))
  .settings(
    name := "PPS-24-DNTS",

    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-Wunused:all"
    ),

    libraryDependencies ++= {
      val akkaVersion = "2.6.20"
      Seq(
        "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
        "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
        "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
        "ch.qos.logback" % "logback-classic" % "1.4.14",
        "org.scalatest" %% "scalatest" % "3.2.18" % Test,
        "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test
      )
    }
  )