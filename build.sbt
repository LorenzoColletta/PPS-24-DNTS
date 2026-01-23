ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.7.4"

lazy val root = (project in file("."))
  .settings(
    name := "PPS-24-DNTS",

    // 1. Rimuove i warning del compilatore (es. import inutilizzati, API deprecate)
    scalacOptions ++= Seq(
      "-deprecation",         // Avvisa se usi API vecchie
      "-feature",             // Avvisa se mancano definizioni di feature
      "-Wunused:all",         // Segnala variabili, import o parametri non usati
      "-no-indent"            // Opzionale: se preferisci uno stile meno rigido sulle indentazioni
    ),

    libraryDependencies ++= {
      val akkaVersion = "2.6.20"
      Seq(
        "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
        "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
        "ch.qos.logback" % "logback-classic" % "1.4.14",
        "org.scalatest" %% "scalatest" % "3.2.18" % Test,
        "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test
      )
    }
  )