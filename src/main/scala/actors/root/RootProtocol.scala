package actors.root


/**
 * Defines the public API for the Root component.
 */
object RootProtocol:

  /**
   * Defines the operational role of a node within the cluster architecture.
   *
   * @param id The string identifier associated with the role.
   */
  enum NodeRole(val id: String):
    
    /**
     * Represents the Seed Node within the cluster.
     * Acts as an initial contact point for new members joining the cluster.
     */
    case Seed extends NodeRole("seed")

    /**
     * Represents a Client Node within the cluster.
     */
    case Client extends NodeRole("client")

    
    /** @return The string identifier of this role. */
    override def toString: String = id

  /**
   * Factory and utility methods for [[NodeRole]].
   */
  object NodeRole:

    private val lookup: Map[String, NodeRole] =
      values.map(role => role.id -> role).toMap

    /**
     * Safely parses a string into a NodeRole.
     *
     * @param s The string representation of the role.
     * @return [[Some]]([[NodeRole]]) if the string matches a valid role, [[None]] otherwise.
     */
    def fromString(s: String): Option[NodeRole] =
      lookup.get(s.toLowerCase)

    /**
     * Returns a pipe-separated string of all valid role identifiers.
     *
     * @return A string like "seed|client".
     */
    def validOptions: String = values.map(_.id).mkString("|")


  /**
   * Commands handled by the RootActor.
   */
  sealed trait RootCommand

  /** Protocol for the RootActor. */
  object RootCommand:

    /**
     * Triggered by the Seed Node to start the simulation.
     */
    case object SeedStartSimulation extends RootCommand

    /**
     * Triggered in case the cluster connection has been confirmed
     */
    case object ClusterReady extends RootCommand

    /**
     * Triggered in case the cluster is not reachable.
     * */
    case object ClusterFailed extends RootCommand

    case object InvalidCommandInBootstrap extends RootCommand

    case object InvalidCommandInJoining extends  RootCommand

    case object SeedLost extends RootCommand
