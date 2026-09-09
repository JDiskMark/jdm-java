package jdiskmark;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.data.category.DefaultCategoryDataset;

/**
 * Top-level tab panel for the batch drive benchmark workflow.
 * Uses a 3-card layout: Setup (landing) → Running → Results.
 */
public class BatchPanel extends JPanel {
    private static final Logger LOG = Logger.getLogger(BatchPanel.class.getName());
    private static final DecimalFormat DF = new DecimalFormat("###.##");
    private static final DecimalFormat DF2 = new DecimalFormat("###.00");

    private static final String CARD_SETUP = "setup";
    private static final String CARD_RUNNING = "running";
    private static final String CARD_RESULTS = "results";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardPanel = new JPanel(cardLayout);

    // Setup card — drives
    private final List<JCheckBox> driveCheckBoxes = new ArrayList<>();
    private final List<File> driveFiles = new ArrayList<>();
    private final List<String> driveLabels = new ArrayList<>();

    // Setup card — profiles
    private final List<JCheckBox> profileCheckBoxes = new ArrayList<>();
    private final List<BenchmarkProfile> profileList = new ArrayList<>();
    private JSpinner cooldownSpinner;

    // Running card
    private JLabel overallLabel;
    private JProgressBar overallProgress;
    private JLabel activeLabel;
    private JProgressBar runProgress;
    private DefaultListModel<String> statusListModel;
    private JButton cancelButton;

    // Results card
    private JPanel chartContainer;
    private JTable summaryTable;
    private JLabel durationLabel;
    private JButton startBatchButton;
    private JButton newBatchButton;

    // State — benchmarks from the currently displayed results (for double-click loading)
    private List<Benchmark> currentResultBenchmarks = new ArrayList<>();
    // Reference to the live chart for theme updates
    private JFreeChart currentChart;

    private BatchWorker worker;

    public BatchPanel() {
        setLayout(new BorderLayout());

        cardPanel.add(buildSetupCard(), CARD_SETUP);
        cardPanel.add(buildRunningCard(), CARD_RUNNING);
        cardPanel.add(buildResultsCard(), CARD_RESULTS);

        add(cardPanel, BorderLayout.CENTER);
        cardLayout.show(cardPanel, CARD_SETUP);

        populateDrives();
    }

    // ── Setup Card ──────────────────────────────────────────────────────────

    private JPanel buildSetupCard() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        JLabel titleLabel = new JLabel("Batch Mode");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        panel.add(titleLabel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));

        // ── Drive + Profile selection (side by side, equal width) ───────────
        JPanel selectionRow = new JPanel(new java.awt.GridLayout(1, 2, 12, 0));
        selectionRow.setAlignmentX(0);

        // ── Left: Drive selection ───────────────────────────────────────────
        JPanel driveColumn = new JPanel();
        driveColumn.setLayout(new BoxLayout(driveColumn, BoxLayout.Y_AXIS));

        JLabel driveListLabel = new JLabel("Select Drives:");
        driveListLabel.setAlignmentX(0);
        driveColumn.add(driveListLabel);
        driveColumn.add(Box.createVerticalStrut(4));

        JPanel driveListPanel = new JPanel();
        driveListPanel.setLayout(new BoxLayout(driveListPanel, BoxLayout.Y_AXIS));
        JScrollPane driveScroll = new JScrollPane(driveListPanel);
        driveScroll.setAlignmentX(0);
        driveScroll.getViewport().setView(driveListPanel);
        driveScroll.putClientProperty("driveListPanel", driveListPanel);
        driveColumn.add(driveScroll);

        JPanel driveSelectPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        driveSelectPanel.setAlignmentX(0);
        JButton driveSelectAll = new JButton("Select All");
        driveSelectAll.addActionListener(e -> driveCheckBoxes.forEach(cb -> cb.setSelected(true)));
        JButton driveDeselectAll = new JButton("Deselect All");
        driveDeselectAll.addActionListener(e -> driveCheckBoxes.forEach(cb -> cb.setSelected(false)));
        driveSelectPanel.add(driveSelectAll);
        driveSelectPanel.add(Box.createHorizontalStrut(8));
        driveSelectPanel.add(driveDeselectAll);
        driveColumn.add(driveSelectPanel);

        // ── Right: Profile selection ────────────────────────────────────────
        JPanel profileColumn = new JPanel();
        profileColumn.setLayout(new BoxLayout(profileColumn, BoxLayout.Y_AXIS));

        JLabel profileLabel = new JLabel("Select Profiles:");
        profileLabel.setAlignmentX(0);
        profileColumn.add(profileLabel);
        profileColumn.add(Box.createVerticalStrut(4));

        BenchmarkProfile[] defaultProfiles = BenchmarkProfile.getDefaults();
        for (BenchmarkProfile bp : defaultProfiles) {
            JCheckBox cb = new JCheckBox(bp.getName());
            cb.setAlignmentX(0);
            if (bp == App.activeProfile) cb.setSelected(true);
            profileCheckBoxes.add(cb);
            profileList.add(bp);
            profileColumn.add(cb);
        }

        JPanel profileSelectPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        profileSelectPanel.setAlignmentX(0);
        JButton profileSelectAll = new JButton("Select All");
        profileSelectAll.addActionListener(e -> profileCheckBoxes.forEach(cb -> cb.setSelected(true)));
        JButton profileDeselectAll = new JButton("Deselect All");
        profileDeselectAll.addActionListener(e -> profileCheckBoxes.forEach(cb -> cb.setSelected(false)));
        profileSelectPanel.add(profileSelectAll);
        profileSelectPanel.add(Box.createHorizontalStrut(8));
        profileSelectPanel.add(profileDeselectAll);
        profileColumn.add(profileSelectPanel);

        profileColumn.add(Box.createVerticalStrut(8));
        startBatchButton = new JButton("Start Batch");
        startBatchButton.putClientProperty("FlatLaf.style", org.metricus.jdm.ui.ButtonStyles.DEFAULT_START);
        startBatchButton.addActionListener(e -> startBatch());
        // MigLayout "h 40!" matches BenchmarkControlPanel's start button exactly, including DPI scaling.
        JPanel startBtnWrapper = new JPanel(new net.miginfocom.swing.MigLayout("insets 0, fillx", "[grow]", "[]"));
        startBtnWrapper.setAlignmentX(0);
        startBtnWrapper.add(startBatchButton, "growx, h 40!");
        startBtnWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, startBtnWrapper.getPreferredSize().height));
        profileColumn.add(startBtnWrapper);

        selectionRow.add(driveColumn);
        selectionRow.add(profileColumn);

        centerPanel.add(selectionRow);
        centerPanel.add(Box.createVerticalStrut(10));

        // ── Cooldown ────────────────────────────────────────────────────────
        JPanel cooldownPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        cooldownPanel.setAlignmentX(0);
        cooldownPanel.add(new JLabel("Cooldown between drives: "));
        cooldownSpinner = new JSpinner(new SpinnerNumberModel(30, 0, 300, 5));
        cooldownPanel.add(cooldownSpinner);
        cooldownPanel.add(new JLabel(" seconds"));
        centerPanel.add(cooldownPanel);

        centerPanel.add(Box.createVerticalStrut(6));

        JLabel infoLabel = new JLabel(
                "<html><i>Each selected profile will run on each selected drive.<br>"
                + "Profiles run round-robin across drives so each drive cools naturally.<br>"
                + "Test data is automatically cleaned up after each run.</i></html>");
        infoLabel.setAlignmentX(0);
        centerPanel.add(infoLabel);

        panel.add(centerPanel, BorderLayout.CENTER);

        return panel;
    }

    // ── Running Card ────────────────────────────────────────────────────────

    private JPanel buildRunningCard() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        JLabel titleLabel = new JLabel("Batch Mode — Running");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        panel.add(titleLabel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));

        overallLabel = new JLabel("Overall: Preparing...");
        overallLabel.setAlignmentX(0);
        centerPanel.add(overallLabel);
        centerPanel.add(Box.createVerticalStrut(4));
        overallProgress = new JProgressBar(0, 100);
        overallProgress.setStringPainted(true);
        overallProgress.setAlignmentX(0);
        overallProgress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        centerPanel.add(overallProgress);

        centerPanel.add(Box.createVerticalStrut(12));

        activeLabel = new JLabel("Active: —");
        activeLabel.setAlignmentX(0);
        centerPanel.add(activeLabel);
        centerPanel.add(Box.createVerticalStrut(4));
        runProgress = new JProgressBar(0, 100);
        runProgress.setStringPainted(true);
        runProgress.setAlignmentX(0);
        runProgress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        centerPanel.add(runProgress);

        centerPanel.add(Box.createVerticalStrut(12));

        statusListModel = new DefaultListModel<>();
        JList<String> statusList = new JList<>(statusListModel);
        JScrollPane statusScroll = new JScrollPane(statusList);
        statusScroll.setAlignmentX(0);
        statusScroll.setPreferredSize(new Dimension(600, 200));
        centerPanel.add(statusScroll);

        panel.add(centerPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        cancelButton = new JButton("Cancel Batch");
        cancelButton.putClientProperty("FlatLaf.style", org.metricus.jdm.ui.ButtonStyles.CANCEL);
        cancelButton.addActionListener(e -> {
            if (worker != null && !worker.isDone()) {
                worker.cancel(false);
                cancelButton.setEnabled(false);
                cancelButton.setText("Cancelling...");
            }
        });
        buttonPanel.add(cancelButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    // ── Results Card ────────────────────────────────────────────────────────

    private JPanel buildResultsCard() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));



        chartContainer = new JPanel(new BorderLayout());
        chartContainer.setPreferredSize(new Dimension(600, 280));

        summaryTable = new JTable(new DefaultTableModel(
                new Object[][]{},
                new String[]{"Drive", "Profile", "Write MB/s", "Read MB/s", "Latency (ms)", "Status"}
        ) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        });
        summaryTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        summaryTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    loadSelectedBenchmark();
                }
            }
        });
        // Give Drive column more room; shrink numeric/status columns
        javax.swing.table.TableColumnModel cm = summaryTable.getColumnModel();
        cm.getColumn(0).setPreferredWidth(220); // Drive
        cm.getColumn(1).setPreferredWidth(100); // Profile
        cm.getColumn(2).setPreferredWidth(80);  // Write MB/s
        cm.getColumn(3).setPreferredWidth(80);  // Read MB/s
        cm.getColumn(4).setPreferredWidth(70);  // Latency (ms)
        cm.getColumn(5).setPreferredWidth(80);  // Status
        cm.getColumn(2).setCellRenderer(new RightTableCellRenderer());
        cm.getColumn(3).setCellRenderer(new RightTableCellRenderer());
        cm.getColumn(4).setCellRenderer(new RightTableCellRenderer());
        cm.getColumn(5).setCellRenderer(new CenterTableCellRenderer());
        JScrollPane tableScroll = new JScrollPane(summaryTable);
        tableScroll.setPreferredSize(new Dimension(600, 120));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chartContainer, tableScroll);
        splitPane.setResizeWeight(0.6);
        splitPane.setContinuousLayout(true);
        splitPane.setBorder(null);

        panel.add(splitPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new BorderLayout());
        durationLabel = new JLabel("");
        buttonPanel.add(durationLabel, BorderLayout.WEST);
        newBatchButton = new JButton("New Batch");
        newBatchButton.putClientProperty("FlatLaf.style", org.metricus.jdm.ui.ButtonStyles.DEFAULT_START);
        newBatchButton.addActionListener(e -> resetToSetup());
        buttonPanel.add(newBatchButton, BorderLayout.EAST);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void loadSelectedBenchmark() {
        int row = summaryTable.getSelectedRow();
        if (row < 0 || row >= currentResultBenchmarks.size()) return;
        Benchmark benchmark = currentResultBenchmarks.get(row);
        if (benchmark == null) return;

        Gui.loadBenchmark(benchmark);
        Gui.selectMainTab(org.metricus.jdm.ui.Tabs.TOP_BENCHMARK);
    }

    private void resetToSetup() {
        overallProgress.setValue(0);
        runProgress.setValue(0);
        overallLabel.setText("Overall: Preparing...");
        activeLabel.setText("Active: —");
        statusListModel.clear();
        cancelButton.setEnabled(true);
        cancelButton.setText("Cancel Batch");

        chartContainer.removeAll();
        chartContainer.revalidate();
        ((DefaultTableModel) summaryTable.getModel()).setRowCount(0);
        durationLabel.setText("");
        currentResultBenchmarks.clear();
        currentChart = null;

        populateDrives();
        cardLayout.show(cardPanel, CARD_SETUP);
    }

    // ── Public: load a historical batch from the bottom reports tab ──────────

    public void showBatchFromHistory(UUID batchId) {
        List<Benchmark> benchmarks = Benchmark.findByBatchId(batchId);
        if (benchmarks.isEmpty()) return;

        currentResultBenchmarks.clear();
        currentResultBenchmarks.addAll(benchmarks);

        List<BenchmarkProfile> profiles = benchmarks.stream()
                .map(b -> b.getConfig().getProfile())
                .distinct()
                .toList();

        List<BatchResult.RunResult> results = benchmarks.stream()
                .map(b -> new BatchResult.RunResult(
                        new File(b.getDriveInfo().getPartitionId() != null ? b.getDriveInfo().getPartitionId() : ""),
                        b.getDriveInfo().getDriveModel(),
                        b.getConfig().getProfile(),
                        b, BatchResult.DriveStatus.COMPLETED, null))
                .toList();

        buildChart(profiles, results);
        buildSummaryTable(results);

        var first = benchmarks.getFirst().startTime;
        var last = benchmarks.getLast().endTime;
        String durationText = "—";
        if (first != null && last != null) {
            Duration dur = Duration.between(first, last);
            durationText = String.format("Duration: %dm %ds", dur.toMinutes(), dur.toSecondsPart());
        }
        durationLabel.setText(durationText);

        cardLayout.show(cardPanel, CARD_RESULTS);
    }

    // ── Public: theme refresh ───────────────────────────────────────────────

    public void refreshChartTheme() {
        if (currentChart == null) return;
        Color bgColor = javax.swing.UIManager.getColor("Panel.background");
        Color fgColor = javax.swing.UIManager.getColor("Panel.foreground");
        if (bgColor == null) bgColor = Color.WHITE;
        if (fgColor == null) fgColor = Color.BLACK;

        currentChart.setBackgroundPaint(bgColor);
        currentChart.getTitle().setPaint(fgColor);
        if (currentChart.getLegend() != null) {
            currentChart.getLegend().setBackgroundPaint(bgColor);
            currentChart.getLegend().setItemPaint(fgColor);
        }
        if (currentChart.getPlot() instanceof CategoryPlot plot) {
            plot.setBackgroundPaint(bgColor);
            plot.setOutlinePaint(bgColor);
            plot.setRangeGridlinePaint(fgColor.brighter());
            plot.getDomainAxis().setTickLabelPaint(fgColor);
            plot.getDomainAxis().setLabelPaint(fgColor);
            plot.getRangeAxis().setTickLabelPaint(fgColor);
            plot.getRangeAxis().setLabelPaint(fgColor);

            // Update bar colors from the current palette
            if (plot.getRenderer() instanceof BarRenderer br) {
                try {
                    java.awt.Paint writePaint = Gui.bwRenderer.getSeriesPaint(0);
                    java.awt.Paint readPaint = Gui.bwRenderer.getSeriesPaint(4);
                    if (writePaint != null) br.setSeriesPaint(0, writePaint);
                    if (readPaint != null) br.setSeriesPaint(1, readPaint);
                } catch (Exception ignored) {}
            }
        }
    }

    public void applyStartButtonStyle(String style) {
        if (startBatchButton != null) startBatchButton.putClientProperty("FlatLaf.style", style);
        if (newBatchButton != null) newBatchButton.putClientProperty("FlatLaf.style", style);
    }

    public void applyCancelButtonStyle(String style) {
        if (cancelButton != null) cancelButton.putClientProperty("FlatLaf.style", style);
    }

    // ── Drive Population ────────────────────────────────────────────────────

    private void populateDrives() {
        new SwingWorker<List<DriveEntry>, Void>() {
            @Override
            protected List<DriveEntry> doInBackground() {
                List<File> roots = listDriveRoots();
                List<DriveEntry> entries = new ArrayList<>();
                for (File root : roots) {
                    String model = Util.getDriveModel(root);
                    if (model == null || model.isBlank()) model = "Unknown";
                    DiskUsageInfo usage;
                    try {
                        usage = Util.getDiskUsage(root.getAbsolutePath());
                    } catch (Exception e) {
                        usage = new DiskUsageInfo();
                    }
                    String label = String.format("%s — %s (%.0f GB)",
                            root.getAbsolutePath(), model, usage.totalGb);
                    entries.add(new DriveEntry(root, label, model));
                }
                return entries;
            }

            @Override
            protected void done() {
                try {
                    List<DriveEntry> entries = get();
                    JPanel driveListPanel = findDriveListPanel();
                    if (driveListPanel == null) return;

                    driveCheckBoxes.clear();
                    driveFiles.clear();
                    driveLabels.clear();
                    driveListPanel.removeAll();

                    for (DriveEntry entry : entries) {
                        JCheckBox cb = new JCheckBox(entry.label());
                        cb.setAlignmentX(0);
                        driveCheckBoxes.add(cb);
                        driveFiles.add(entry.root());
                        driveLabels.add(entry.model());
                        driveListPanel.add(cb);
                    }
                    if (!driveCheckBoxes.isEmpty()) {
                        driveCheckBoxes.getFirst().setSelected(true);
                    }

                    driveListPanel.revalidate();
                    driveListPanel.repaint();
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to populate drives", e);
                }
            }
        }.execute();
    }

    private JPanel findDriveListPanel() {
        JPanel setupCard = (JPanel) cardPanel.getComponent(0);
        return findDriveListPanelRecursive(setupCard);
    }

    private JPanel findDriveListPanelRecursive(java.awt.Container container) {
        for (java.awt.Component c : container.getComponents()) {
            if (c instanceof JScrollPane sp) {
                Object prop = sp.getClientProperty("driveListPanel");
                if (prop instanceof JPanel p) return p;
            }
            if (c instanceof java.awt.Container sub) {
                JPanel result = findDriveListPanelRecursive(sub);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static List<File> listDriveRoots() {
        if (App.isLinux()) {
            return UtilOs.getMountedDrivesLinux();
        } else if (App.isMacOs()) {
            return org.metricus.jdm.os.UtilsMacOs.getMountedDrives();
        }
        return List.of(File.listRoots());
    }

    // ── Batch Execution ─────────────────────────────────────────────────────

    private void startBatch() {
        List<File> selectedDrives = new ArrayList<>();
        for (int i = 0; i < driveCheckBoxes.size(); i++) {
            if (driveCheckBoxes.get(i).isSelected()) {
                selectedDrives.add(driveFiles.get(i));
            }
        }
        List<BenchmarkProfile> selectedProfiles = new ArrayList<>();
        for (int i = 0; i < profileCheckBoxes.size(); i++) {
            if (profileCheckBoxes.get(i).isSelected()) {
                selectedProfiles.add(profileList.get(i));
            }
        }

        if (selectedDrives.isEmpty()) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    "Please select at least one drive.",
                    "Batch Mode", javax.swing.JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (selectedProfiles.isEmpty()) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    "Please select at least one profile.",
                    "Batch Mode", javax.swing.JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (App.state == App.State.DISK_TEST_STATE || App.batchRunning) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    "A benchmark is already running.",
                    "Batch Mode", javax.swing.JOptionPane.WARNING_MESSAGE);
            return;
        }

        int cooldown = (int) cooldownSpinner.getValue();
        BatchConfig batchConfig = BatchConfig.of(selectedDrives, selectedProfiles, cooldown);

        statusListModel.clear();
        // Profile-first order to match BatchWorker execution
        for (BenchmarkProfile profile : selectedProfiles) {
            for (File drive : selectedDrives) {
                String dModel = drive.getAbsolutePath();
                for (int j = 0; j < driveFiles.size(); j++) {
                    if (driveFiles.get(j).equals(drive)) {
                        dModel = driveLabels.get(j) + " (" + driveFiles.get(j).getAbsolutePath() + ")";
                        break;
                    }
                }
                statusListModel.addElement("⏸  " + dModel + " — " + profile.getName() + "  (waiting)");
            }
        }

        cardLayout.show(cardPanel, CARD_RUNNING);

        App.batchRunning = true;
        if (Gui.mainFrame != null) Gui.mainFrame.adjustSensitivity();

        worker = new BatchWorker(batchConfig);
        worker.setEventListener(this::handleEvent);
        worker.execute();
    }

    private void handleEvent(BatchEvent event) {
        switch (event) {
            case BatchEvent.RunStarted e -> {
                overallLabel.setText("Overall: Run " + (e.runIndex() + 1) + " of " + e.totalRuns());
                overallProgress.setValue((int) ((float) e.runIndex() / e.totalRuns() * 100));
                activeLabel.setText("Active: " + e.drivePath() + " — " + e.driveModel() + " — " + e.profile().getName());
                runProgress.setValue(0);
                statusListModel.set(e.runIndex(),
                        "⏳ " + e.driveModel() + " — " + e.profile().getName() + "  (running...)");
            }
            case BatchEvent.RunProgress e -> {
                runProgress.setValue(e.percent());
            }
            case BatchEvent.RunCompleted e -> {
                Benchmark b = e.result();
                StringBuilder sb = new StringBuilder("✅ ");
                sb.append(b.getDriveInfoDisplay()).append(" — ").append(e.profile().getName());
                for (BenchmarkOperation op : b.getOperations()) {
                    switch (op.ioMode) {
                        case WRITE -> sb.append("  W: ").append(DF.format(op.bwAvg)).append(" MB/s");
                        case READ -> sb.append("  R: ").append(DF.format(op.bwAvg)).append(" MB/s");
                    }
                }
                statusListModel.set(e.runIndex(), sb.toString());
                updateOverallProgress();
            }
            case BatchEvent.RunRetrying e -> {
                statusListModel.set(e.runIndex(),
                        "🔄 retrying — " + e.profile().getName() + " — " + e.errorMessage());
            }
            case BatchEvent.RunSkipped e -> {
                statusListModel.set(e.runIndex(),
                        "❌ Skipped — " + e.profile().getName() + " — " + e.errorMessage());
                updateOverallProgress();
            }
            case BatchEvent.CooldownStarted e -> {
                activeLabel.setText("Cooling down... " + e.cooldownSeconds() + "s remaining");
                runProgress.setValue(0);
            }
            case BatchEvent.CooldownTick e -> {
                activeLabel.setText("Cooling down... " + e.secondsRemaining() + "s remaining");
            }
            case BatchEvent.BatchCompleted e -> {
                overallProgress.setValue(100);
                activeLabel.setText("Batch complete");
                showResults(e.finalResult());
            }
            case BatchEvent.BatchCancelled e -> {
                activeLabel.setText("Batch cancelled");
                showResults(e.partialResult());
                App.msg("Batch cancelled — partial results shown");
            }
        }
    }

    private void updateOverallProgress() {
        int completed = 0;
        for (int i = 0; i < statusListModel.size(); i++) {
            String s = statusListModel.get(i);
            if (s.startsWith("✅") || s.startsWith("❌")) completed++;
        }
        overallProgress.setValue((int) ((float) completed / statusListModel.size() * 100));
    }

    // ── Results Display ─────────────────────────────────────────────────────

    private void showResults(BatchResult result) {
        currentResultBenchmarks.clear();
        for (BatchResult.RunResult rr : result.getResults()) {
            currentResultBenchmarks.add(rr.benchmark());
        }

        buildChart(result.getProfiles(), result.getSuccessfulResults());
        buildSummaryTable(result.getResults());

        Duration dur = result.getTotalDuration();
        long mins = dur.toMinutes();
        long secs = dur.toSecondsPart();
        String durationText = String.format("Duration: %dm %ds", mins, secs);
        durationLabel.setText(durationText);

        cardLayout.show(cardPanel, CARD_RESULTS);
        String profileNames = String.join(", ", result.getProfiles().stream().map(BenchmarkProfile::getName).toList());
        App.msg("Batch complete — " + profileNames + " | " + durationText);
    }

    private void buildChart(List<BenchmarkProfile> profiles, List<BatchResult.RunResult> successful) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();

        for (BatchResult.RunResult rr : successful) {
            if (!rr.isSuccess() || rr.benchmark() == null) continue;
            String profileName = rr.profile() != null ? rr.profile().getName() : "—";
            String driveName = rr.driveModel() != null ? rr.driveModel() : "—";
            String categoryKey = driveName + " [" + profileName + "]";
            for (BenchmarkOperation op : rr.benchmark().getOperations()) {
                switch (op.ioMode) {
                    case WRITE -> dataset.addValue(op.bwAvg, "Write", categoryKey);
                    case READ -> dataset.addValue(op.bwAvg, "Read", categoryKey);
                }
            }
        }

        CategoryAxis categoryAxis = new CategoryAxis("");
        categoryAxis.setMaximumCategoryLabelWidthRatio(0.4f);
        NumberAxis valueAxis = new NumberAxis("Bandwidth (MB/s)");
        BarRenderer renderer = new BarRenderer();
        renderer.setBarPainter(new StandardBarPainter());
        renderer.setDrawBarOutline(false);
        renderer.setShadowVisible(false);

        Color writeColor = new Color(0xDC, 0x14, 0x3C);
        Color readColor = new Color(0x3C, 0x3B, 0x6E);
        try {
            java.awt.Paint writePaint = Gui.bwRenderer.getSeriesPaint(0);
            java.awt.Paint readPaint = Gui.bwRenderer.getSeriesPaint(4);
            if (writePaint instanceof Color c) writeColor = c;
            if (readPaint instanceof Color c) readColor = c;
        } catch (Exception ignored) {}

        renderer.setSeriesPaint(0, writeColor);
        renderer.setSeriesPaint(1, readColor);

        CategoryPlot plot = new CategoryPlot(dataset, categoryAxis, valueAxis, renderer);
        plot.setOrientation(PlotOrientation.HORIZONTAL);

        Color bgColor = javax.swing.UIManager.getColor("Panel.background");
        Color fgColor = javax.swing.UIManager.getColor("Panel.foreground");
        if (bgColor == null) bgColor = Color.WHITE;
        if (fgColor == null) fgColor = Color.BLACK;

        plot.setBackgroundPaint(bgColor);
        plot.setOutlinePaint(bgColor);
        plot.setRangeGridlinePaint(fgColor.brighter());
        categoryAxis.setTickLabelPaint(fgColor);
        categoryAxis.setLabelPaint(fgColor);
        valueAxis.setTickLabelPaint(fgColor);
        valueAxis.setLabelPaint(fgColor);

        JFreeChart chart = new JFreeChart("Drive Benchmark Comparison", JFreeChart.DEFAULT_TITLE_FONT, plot, true);
        chart.setBackgroundPaint(bgColor);
        chart.getTitle().setPaint(fgColor);
        if (chart.getLegend() != null) {
            chart.getLegend().setBackgroundPaint(bgColor);
            chart.getLegend().setItemPaint(fgColor);
        }

        currentChart = chart;

        chartContainer.removeAll();
        ChartPanel cp = new ChartPanel(chart);
        cp.setPreferredSize(new Dimension(600, 280));
        chartContainer.add(cp, BorderLayout.CENTER);
        chartContainer.revalidate();
    }

    private void buildSummaryTable(List<BatchResult.RunResult> results) {
        DefaultTableModel tableModel = (DefaultTableModel) summaryTable.getModel();
        tableModel.setRowCount(0);
        for (BatchResult.RunResult rr : results) {
            String driveName = rr.driveModel();
            if (driveName == null || driveName.isBlank()) driveName = rr.driveLocation().getAbsolutePath();

            String profileName = rr.profile() != null ? rr.profile().getName() : "—";
            String writeBw = "—";
            String readBw = "—";
            String latency = "—";
            String status = rr.status().name();

            if (rr.benchmark() != null) {
                for (BenchmarkOperation op : rr.benchmark().getOperations()) {
                    switch (op.ioMode) {
                        case WRITE -> {
                            writeBw = DF2.format(op.bwAvg);
                            if (latency.equals("—")) latency = DF2.format(op.accAvg);
                        }
                        case READ -> {
                            readBw = DF2.format(op.bwAvg);
                            latency = DF2.format(op.accAvg);
                        }
                    }
                }
            }
            tableModel.addRow(new Object[]{driveName, profileName, writeBw, readBw, latency, status});
        }
    }

    record DriveEntry(File root, String label, String model) {}
}
