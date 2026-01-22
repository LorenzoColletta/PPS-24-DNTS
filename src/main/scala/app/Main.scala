package app

import akka.actor.typed.ActorSystem
import config.{AppConfig, ProductionConfig}
import actors.root.RootActor
import cli.{CliParser, ParseResult}

/**
 * Application Entry Point.
 *
 * Responsible for bootstrapping the Akka Cluster node. It acts as the orchestration layer
 * between Command Line Interface parsing, configuration loading, and the Akka Actor System startup.
 */
object Main:

  /**
   * The main execution method.
   *
   * It processes the raw command-line arguments to determine the node's role and configuration.
   * Based on the parsing result, it either starts the [[ActorSystem]] or terminates the process.
   *
   * <h3>Exit Codes:</h3>
   * <ul>
   * <li>**0**: Successful execution (or Help message displayed).</li>
   * <li>**1**: Configuration error or Invalid arguments.</li>
   * </ul>
   *
   * @param args The variable argument list passed from the command line.
   */
  @main def run(args: String*): Unit =
    val parseResult = CliParser.parse(args.toList)

    parseResult match
      case ParseResult.Help =>
        println(CliParser.getHelpText)
        sys.exit(0)

      case ParseResult.Failure(msg) =>
        System.err.println(s"Error: $msg")
        println(CliParser.getHelpText)
        sys.exit(1)

      case ParseResult.Success(options) =>
        options.validate match
          case Left(errorMsg) =>
            System.err.println(s"Configuration Error: $errorMsg")
            sys.exit(1)

          case Right((role, configPath, clusterIp, clusterPort)) =>
            println(s">>> Starting Node with Role: $role")

            given appConfig: AppConfig = ProductionConfig

            val rootBehavior = RootActor(
              role = role,
              configPath = configPath,
              //clusterIp = clusterIp,
              //clusterPort = clusterPort,
            )

            ActorSystem(rootBehavior, "ClusterSystem")
