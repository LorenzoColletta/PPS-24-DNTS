package app

import scala.annotation.tailrec

import akka.actor.typed.ActorSystem

import config.ConfigLoader
import actors.RootActor


object Main:

  private case class CliArgs(
    role: String = "client", 
    configFile: Option[String] = None
  )

  @tailrec
  private def parseArgs(args: List[String], current: CliArgs = CliArgs()): CliArgs = args match
    case "--role" :: "seed" :: tail => parseArgs(tail, current.copy(role = "seed"))
    case "--role" :: "client" :: tail => parseArgs(tail, current.copy(role = "client"))
    case "--config" :: path :: tail   => parseArgs(tail, current.copy(configFile = Some(path)))
    case Nil => current
    case _ =>
      println("Usage: run --role [seed|client] [--config file]")
      sys.exit(1)

  @main def run(args: String*): Unit =
    val cli = parseArgs(args.toList)

    val rootBehavior = RootActor(cli.role, cli.configFile)

    ActorSystem(rootBehavior, "ClusterSystem")
