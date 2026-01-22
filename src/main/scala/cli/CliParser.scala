package cli

import actors.root.RootProtocol.NodeRole
import scala.annotation.tailrec

/**
 * Represents the possible outcomes of the command-line argument parsing process.
 */
sealed trait ParseResult

object ParseResult:
  /** Indicates a successful parse, 
   * 
   * @param options the fully populated options. 
   */
  final case class Success(options: CliOptions) extends ParseResult

  /** Indicates the user requested the help message (or provided empty args). */
  case object Help extends ParseResult

  /** 
   * Indicates an error occurred (e.g., unknown flag, invalid value). 
   * 
   * @param message the error message.
   */
  final case class Failure(message: String) extends ParseResult


/**
 * Responsible for parsing command-line arguments.
 */
object CliParser:

  private val HelpText =
    s"""
      |Usage:
      |  Master (Seed): run --role ${NodeRole.Seed.toString} [--config <file>] [--port <local-port>]
      |  Worker (Client): run --role ${NodeRole.Client.toString} --ip <seed-ip> --port <seed-port> [--config <file>]
      |
      |Options:
      |  --role <${NodeRole.validOptions}>   Defines the node role.
      |  --config <path>                     Path to the simulation configuration file, required only for Master Node (optional).
      |  --ip <string>                       IP address of the remote cluster (required for client).
      |  --port <int>                        Port of the remote cluster (required for client).
      |  --help                              Show this message.
      |""".stripMargin

  /** Retrieves the formatted usage string describing available commands. */
  def getHelpText: String = HelpText

  /**
   * Parses a list of raw command-line arguments into a structured result.
   *
   * @param args The list of arguments.
   * @return A [[ParseResult]] indicating Success, Help, or Failure.
   */
  def parse(args: List[String]): ParseResult =
    if args.isEmpty || args.contains("--help") then
      ParseResult.Help
    else
      parseRec(args, CliOptions())

  
  @tailrec
  private def parseRec(args: List[String], current: CliOptions): ParseResult =
    args match
      case Nil =>
        ParseResult.Success(current)

      case "--role" :: value :: tail =>
        NodeRole.fromString(value) match
          case Some(r) => parseRec(tail, current.copy(role = Some(r)))
          case None    => ParseResult.Failure(s"Invalid role: '$value'. Must be one of: ${NodeRole.validOptions}.")

      case "--config" :: path :: tail =>
        parseRec(tail, current.copy(configFile = Some(path)))

      case "--ip" :: value :: tail =>
        parseRec(tail, current.copy(targetIp = Some(value)))

      case "--port" :: value :: tail =>
        value.toIntOption match
          case Some(p) => parseRec(tail, current.copy(targetPort = Some(p)))
          case None    => ParseResult.Failure(s"Invalid port number: '$value'.")

      case unknown :: _ =>
        ParseResult.Failure(s"Unknown option: $unknown")
