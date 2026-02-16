package view.components

import javax.swing.*
import javax.swing.border.{EmptyBorder, TitledBorder}
import javax.swing.table.DefaultTableModel
import java.awt.{BorderLayout, Component, Dimension, Font, GridBagConstraints, GridBagLayout, GridLayout, Insets}

import view.ViewStateSnapshot
import domain.network.Model
import actors.trainer.TrainerActor.TrainingConfig
import actors.root.RootActor.NodeRole

/**
 * Contains constants and static definitions for the [[SetupInfoPanel]].
 */
object SetupInfoPanel:
  private object Style:
    final val TitleFont: Font = new Font("SansSerif", Font.BOLD, 20)
    final val SectionFont: Font = new Font("SansSerif", Font.BOLD, 14)


/**
 * A Swing panel designed to display the initial configuration and cluster status
 * before the actual simulation begins.
 * It also hosts the control for the Master node to trigger the start of the simulation.
 */
class SetupInfoPanel extends JPanel:
  import SetupInfoPanel.*

  private val lblRole = new JLabel("Role: -")
  private val lblSeed = new JLabel("Seed: -")
  private val lblPeers = new JLabel("Active Peers: 0/0")

  private val detailsContainer = new JPanel()
  private val scrollPane = new JScrollPane(detailsContainer)

  private val btnStart = new JButton("START DISTRIBUTED TRAINING")


  initLayout()


  /**
   * Updates the UI label displaying the number of connected peers in the cluster.
   *
   * @param active The number of currently active peers.
   * @param total  The total number of peers known to the cluster.
   */
  def updatePeerCount(active: Int, total: Int): Unit =
    SwingUtilities.invokeLater(() =>
      lblPeers.setText(s"Active Peers: $active / $total")
    )

  /**
   * Refreshes the entire panel content based on the provided system snapshot.
   *
   * @param snapshot The current [[ViewStateSnapshot]] containing model and config data.
   * @param isMaster True if the local node is the Cluster Seed, enabling the start button.
   * @param onStart  A callback function to be executed when the "Start" button is clicked.
   */
  def render(snapshot: ViewStateSnapshot, isMaster: Boolean, onStart: () => Unit): Unit =
    SwingUtilities.invokeLater(() =>
      updateHeader(snapshot, isMaster)
      updateStartButton(isMaster, onStart)
      updateDetails(snapshot.model, snapshot.config)
    )


  private def initLayout(): Unit =
    setLayout(new BorderLayout(10, 10))
    setBorder(new EmptyBorder(15, 15, 15, 15))

    val lblTitle = new JLabel("Simulation Configuration", SwingConstants.CENTER)
    lblTitle.setFont(Style.TitleFont)

    val statusPanel = new JPanel(new GridLayout(1, 3, 10, 0))
    statusPanel.setBorder(new TitledBorder(
      null, "Cluster Status", TitledBorder.DEFAULT_JUSTIFICATION, 
      TitledBorder.DEFAULT_POSITION, Style.SectionFont)
    )
    statusPanel.add(lblRole)
    statusPanel.add(lblSeed)
    statusPanel.add(lblPeers)

    detailsContainer.setLayout(new BoxLayout(detailsContainer, BoxLayout.Y_AXIS))
    scrollPane.setBorder(BorderFactory.createEmptyBorder())

    val centerPanel = new JPanel(new BorderLayout())
    centerPanel.add(statusPanel, BorderLayout.NORTH)
    centerPanel.add(scrollPane, BorderLayout.CENTER)

    btnStart.setFont(Style.SectionFont)
    btnStart.setVisible(false)

    add(lblTitle, BorderLayout.NORTH)
    add(centerPanel, BorderLayout.CENTER)
    add(btnStart, BorderLayout.SOUTH)
  
  private def updateHeader(snapshot: ViewStateSnapshot, isMaster: Boolean): Unit =
    lblRole.setText(s"Role: ${
      if isMaster then NodeRole.Seed.toString.toUpperCase else NodeRole.Client.toString.toUpperCase
    }")
    lblSeed.setText(s"Seed: ${snapshot.clusterSeed.getOrElse("Connecting...")}")
    updatePeerCount(snapshot.activePeers, snapshot.totalPeers)

  private def updateStartButton(isMaster: Boolean, onStart: () => Unit): Unit =
    btnStart.getActionListeners.foreach(btnStart.removeActionListener)

    if isMaster then
      btnStart.setVisible(true)
      btnStart.addActionListener(_ => onStart())
    else
      btnStart.setVisible(false)

  private def updateDetails(modelOpt: Option[Model], configOpt: Option[TrainingConfig]): Unit =
    detailsContainer.removeAll()

    (modelOpt, configOpt) match
      case (Some(model), Some(config)) =>
        detailsContainer.add(Box.createVerticalStrut(10))
        detailsContainer.add(buildModelPanel(model))
        detailsContainer.add(Box.createVerticalStrut(10))
        detailsContainer.add(buildTrainingPanel(config))
      case _ =>
        val lblWait = new JLabel("Waiting for configuration...", SwingConstants.CENTER)
        lblWait.setAlignmentX(Component.CENTER_ALIGNMENT)
        detailsContainer.add(Box.createVerticalGlue())
        detailsContainer.add(lblWait)
        detailsContainer.add(Box.createVerticalGlue())

    detailsContainer.revalidate()
    detailsContainer.repaint()
  
  private def buildModelPanel(model: Model): JPanel =
    val p = new JPanel(new BorderLayout())
    p.setBorder(new TitledBorder(
      null, "Neural Network Topology", TitledBorder.DEFAULT_JUSTIFICATION, 
      TitledBorder.DEFAULT_POSITION, Style.SectionFont)
    )

    val lblInput = new JLabel(s"Input Features (${model.features.size}): ${model.features.mkString(", ")}")
    lblInput.setBorder(new EmptyBorder(5, 5, 10, 5))
    p.add(lblInput, BorderLayout.NORTH)

    val colNames = Array[Object]("Layer ID", "Type", "Neurons", "Activation")
    val data = model.network.layers.zipWithIndex.map { case (layer, idx) =>
      val lType = idx match
        case 0 => "Input"
        case i if i == model.network.layers.size - 1 => "Output"
        case _ => "Hidden"
      Array[Object]((idx + 1).toString, lType, layer.weights.rows.toString, layer.activation.toString)
    }.toArray

    val tableModel = new DefaultTableModel(data, colNames) {
      override def isCellEditable(row: Int, col: Int) = false
    }

    val tableScroll = new JScrollPane(new JTable(tableModel))
    tableScroll.setPreferredSize(new Dimension(400, 100))
    p.add(tableScroll, BorderLayout.CENTER)
    p

  private def buildTrainingPanel(conf: TrainingConfig): JPanel =
    val p = new JPanel(new GridBagLayout())
    p.setBorder(new TitledBorder(
      null, "Training Configuration", TitledBorder.DEFAULT_JUSTIFICATION, 
      TitledBorder.DEFAULT_POSITION, Style.SectionFont)
    )

    val c = new GridBagConstraints()
    c.insets = new Insets(5, 5, 5, 5)
    c.fill = GridBagConstraints.HORIZONTAL
    c.weightx = 0.5

    def addRow(label: String, value: String, y: Int): Unit =
      c.gridy = y
      c.gridx = 0; p.add(new JLabel(s"$label:"), c)
      c.gridx = 1; p.add(new JLabel(value), c)

    addRow("Epochs", conf.epochs.toString, 0)
    addRow("Batch Size", conf.batchSize.toString, 1)
    addRow("Learning Rate", conf.hp.learningRate.toString, 2)
    addRow("Regularization", conf.hp.regularization.toString, 3)
    p
    