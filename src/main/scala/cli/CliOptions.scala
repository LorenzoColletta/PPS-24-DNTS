package cli

import actors.root.RootActor.NodeRole

/**
 * Container representing the raw state of parsed command-line arguments.
 *
 * @param role       The operating [[NodeRole]] of the node.
 * @param configFile The optional file path to the simulation configuration.
 * @param targetIp   The target IP address (required for Client nodes).
 * @param targetPort The target port number (required for Client nodes).
 */
case class CliOptions(
  role: Option[NodeRole] = None,
  configFile: Option[String] = None,
  targetIp: Option[String] = None,
  targetPort: Option[Int] = None
):

  /**
   * Performs semantic validation of the accumulated options.
   * It ensures that the specific combination of flags is valid for the selected role.
   *
   * @return `Right` containing the validated tuple (Role, ConfigPath, IP, Port) if successful,
   * or `Left` with an error message if the configuration is invalid.
   */
  def validate: Either[String, (NodeRole, Option[String], Option[String], Option[Int])] =
    role match
      case None =>
        Left("Missing required parameter: --role <seed|client>")

      case Some(NodeRole.Seed) =>
        Right((NodeRole.Seed, configFile, None, targetPort))

      case Some(NodeRole.Client) =>
        (targetIp, targetPort) match
          case (Some(ip), Some(port)) =>
            Right((NodeRole.Client, configFile, Some(ip), Some(port)))
          case _ =>
            Left("Client nodes requires both --ip <address> and --port <number> of the remote cluster.")
