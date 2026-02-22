ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.1"

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
        "org.jfree" % "jfreechart" % "1.5.3",
        "org.scalatest" %% "scalatest" % "3.2.18" % Test,
        "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
      )
    },

    assembly / assemblyJarName := "dnts.jar",

    assembly / assemblyMergeStrategy := {
      case x if x.endsWith("module-info.class") => MergeStrategy.discard

      case "reference.conf" => MergeStrategy.concat

      case PathList("META-INF", xs @ _*) =>
        xs match {
          case "MANIFEST.MF" :: Nil => MergeStrategy.discard
          case "services" :: _      => MergeStrategy.concat
          case _                    => MergeStrategy.discard
        }

      case _ => MergeStrategy.first
    }
  )