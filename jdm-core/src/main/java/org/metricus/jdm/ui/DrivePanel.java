package org.metricus.jdm.ui;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import jdiskmark.App;
import jdiskmark.DiskUsageInfo;
import jdiskmark.DriveChecker;
import jdiskmark.Gui;
import org.metricus.jdm.util.DriveDetailsCache;

import org.metricus.jdm.os.UtilOs;
import org.metricus.jdm.os.UtilsLinux;
import org.metricus.jdm.util.Util;


/**
 * Drives tab — lets the user select the benchmark target drive, shows a
 * summary of the selected drive, and manages the test-directory path.
 *
 * <pre>
 * ┌──────────────────────────────────────────────────────┐
 * │  Drive:  [combo box ─────────────────────────] [⟳]  │  ← NORTH
 * ├──────────────────────────────────────────────────────┤
 * │   Summary                                           │
 * │   Model: …                                          │
 * │   Partition: …                                      │  ← CENTER
 * │   Access: …                                         │
 * │   File System: …   Interface: …   Sector Size: …    │
 * │   Usage: …                                          │
 * │   [═══════════ 20% ═══════════]                     │
 * ├──────────────────────────────────────────────────────┤
 * │  Test Dir: [path ────────────────────] [Browse] [Open]│  ← SOUTH
 * └──────────────────────────────────────────────────────┘
 * </pre>
 *
 * The "All Drives" table is exposed via {@link #buildAllDrivesPanel()} for
 * embedding in the bottom tabbed pane.
 */
public class DrivePanel extends JPanel {

    // -----------------------------------------------------------------------
    // Inner type — one item in the drive combo box
    // -----------------------------------------------------------------------

    private static class DriveEntry {
        final File   root;
        final String capacity;     // "467 GB"
        String       typeDesc;     // OS description, replaced by "USB Drive" once USB is detected
        String       model = "loading\u2026";   // filled in asynchronously

        DriveEntry(File root) {
            this.root = root;
            double totalGb = root.getTotalSpace() / (double) App.GIGABYTE;
            typeDesc = Util.getDriveType(root);
            capacity = String.format("%.0f GB", totalGb);
        }

        @Override public String toString() {
            // path [type]   —   model   —   capacity
            String type = (typeDesc != null && !typeDesc.isBlank())
                          ? "  [" + typeDesc + "]" : "";
            return root.getAbsolutePath() + type
                    + "   \u2014   " + model + "   \u2014   " + capacity;
        }
    }

    // -----------------------------------------------------------------------
    // UI fields
    // -----------------------------------------------------------------------

    private final JComboBox<DriveEntry> driveCombo;

    // Summary labels
    private JLabel        infoModelLabel;
    private JLabel        infoPartitionLabel;
    private JLabel        infoUsageLabel;
    private JLabel        accessLabel;
    private JLabel        infoFilesystemLabel;
    private JLabel        infoInterfaceLabel;
    private JLabel        infoSectorSizeLabel;
    private JProgressBar  usageBar;

    // Test directory row (bottom)
    private final JTextField pathField;
    private final JButton    browseButton;
    private final JButton    openButton;

    // All Drives table — built lazily by buildAllDrivesPanel()
    private DefaultTableModel allDrivesTableModel;
    private JTable            allDrivesTable;

    private static final String[] ALL_DRIVES_COLUMNS = {
        "Drive / Mount", "Model", "Interface", "File System",
        "Total (GB)", "Used (GB)", "Free (GB)", "Usage"
    };

    private static final Logger LOG = Logger.getLogger(DrivePanel.class.getName());

    /** Prevents combo listener from firing during a programmatic refresh(). */
    private boolean suppressComboEvents = false;

    // Latest async request of each kind; older results are discarded (EDT only).
    private int comboGeneration     = 0;
    private int allDrivesGeneration = 0;
    private int infoGeneration      = 0;
    private int selectionGeneration = 0;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public DrivePanel() {
        setLayout(new BorderLayout(0, 0));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // ── NORTH — Drive selector + Test Directory ──────────────────────────
        JPanel northPanel = new JPanel(new BorderLayout(0, 2));

        JPanel selectorRow = new JPanel(new BorderLayout(6, 0));
        JLabel driveLabel = new JLabel("Drive:");
        driveLabel.setFont(driveLabel.getFont().deriveFont(Font.BOLD));
        driveLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
        selectorRow.add(driveLabel, BorderLayout.WEST);

        driveCombo = new JComboBox<>();
        driveCombo.setMaximumRowCount(12);
        populateCombo();
        selectorRow.add(driveCombo, BorderLayout.CENTER);

        JButton refreshButton = new JButton("\u27F3");
        refreshButton.setToolTipText("Refresh drive list");
        refreshButton.setMargin(new Insets(2, 6, 2, 6));
        refreshButton.addActionListener(e -> {
            DriveDetailsCache.clear();
            refresh();
        });
        selectorRow.add(refreshButton, BorderLayout.EAST);
        northPanel.add(selectorRow, BorderLayout.NORTH);

        // Test Directory row — directly below the drive selector
        JPanel testDirRow = new JPanel(new BorderLayout(4, 0));
        testDirRow.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));

        JLabel testDirLabel = new JLabel("Test Path:");
        testDirLabel.setFont(testDirLabel.getFont().deriveFont(Font.BOLD));
        testDirLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
        testDirRow.add(testDirLabel, BorderLayout.WEST);

        pathField = new JTextField();
        pathField.setEditable(false);
        testDirRow.add(pathField, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new BorderLayout(4, 0));
        browseButton = new JButton("Browse…");
        openButton   = new JButton("Open");
        btnPanel.add(browseButton, BorderLayout.WEST);
        btnPanel.add(openButton,   BorderLayout.EAST);
        testDirRow.add(btnPanel, BorderLayout.EAST);
        northPanel.add(testDirRow, BorderLayout.SOUTH);

        add(northPanel, BorderLayout.NORTH);

        // ── CENTER — Summary pane (full width) ───────────────────────────────
        JPanel summaryPane = buildSummaryPane();
        add(summaryPane, BorderLayout.CENTER);

        // ── Wire listeners ────────────────────────────────────────────────────
        driveCombo.addActionListener(e -> {
            if (!suppressComboEvents) applySelectedDrive();
        });

        browseButton.addActionListener(e -> Gui.browseLocation());

        openButton.addActionListener(e -> {
            if (App.locationDir != null && App.locationDir.exists()
                    && Desktop.isDesktopSupported()) {
                try { Desktop.getDesktop().open(App.locationDir); }
                catch (IOException ex) { LOG.log(Level.WARNING, "open failed", ex); }
            }
        });

        // Initial population
        refreshDriveInfo();
    }

    // -----------------------------------------------------------------------
    // Summary pane builder
    // -----------------------------------------------------------------------

    /**
     * Builds the summary pane showing selected-drive info.
     * Usage label and capacity bar are at the bottom.
     */
    private JPanel buildSummaryPane() {
        infoModelLabel      = new JLabel("Model: —");
        infoPartitionLabel  = new JLabel("Partition: —");
        accessLabel         = new JLabel("Access: —");
        infoFilesystemLabel = new JLabel("File System: —");
        infoInterfaceLabel  = new JLabel("Interface: —");
        infoSectorSizeLabel = new JLabel("Sector Size: —");
        infoUsageLabel      = new JLabel("Usage: —");
        usageBar            = new JProgressBar(0, 100);
        usageBar.setStringPainted(true);

        JPanel inner = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx  = 0;
        gbc.fill   = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(3, 4, 3, 4);

        gbc.gridy = 0; inner.add(infoModelLabel,      gbc);
        gbc.gridy = 1; inner.add(infoPartitionLabel,  gbc);
        gbc.gridy = 2; inner.add(accessLabel,         gbc);
        gbc.gridy = 3; inner.add(infoFilesystemLabel, gbc);
        gbc.gridy = 4; inner.add(infoInterfaceLabel,  gbc);
        gbc.gridy = 5; inner.add(infoSectorSizeLabel, gbc);

        // Push content to the top, usage to the bottom
        gbc.gridy   = 6;
        gbc.weighty = 1.0;
        gbc.fill    = GridBagConstraints.BOTH;
        inner.add(new JPanel(), gbc);   // filler

        // Usage label + bar pinned to the bottom
        gbc.gridy   = 7;
        gbc.weighty = 0;
        gbc.fill    = GridBagConstraints.HORIZONTAL;
        inner.add(infoUsageLabel, gbc);

        gbc.gridy = 8;
        inner.add(usageBar, gbc);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(BorderFactory.createTitledBorder("Summary"));
        wrapper.add(inner, BorderLayout.CENTER);
        return wrapper;
    }

    // -----------------------------------------------------------------------
    // All Drives panel — for embedding in the bottom tabbed pane
    // -----------------------------------------------------------------------

    /**
     * Builds and returns a panel containing the "All Drives" table with a
     * Model column. This is intended to be added as a tab in the bottom
     * tabbed pane. The table is populated immediately and refreshed on
     * subsequent {@link #refresh()} calls.
     *
     * @return the All Drives panel
     */
    public JPanel buildAllDrivesPanel() {
        allDrivesTableModel = new DefaultTableModel(ALL_DRIVES_COLUMNS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int col) {
                return switch (col) {
                    case 4, 5, 6 -> Double.class;
                    case 7       -> Integer.class;  // Usage % for progress bar
                    default      -> String.class;
                };
            }
        };

        allDrivesTable = new JTable(allDrivesTableModel);
        allDrivesTable.setFillsViewportHeight(true);
        allDrivesTable.setRowHeight(22);
        allDrivesTable.setAutoCreateRowSorter(true);
        allDrivesTable.getTableHeader().setFont(
                allDrivesTable.getTableHeader().getFont().deriveFont(Font.BOLD));
        // Request enough height for 5 visible rows
        allDrivesTable.setPreferredScrollableViewportSize(
                new java.awt.Dimension(allDrivesTable.getPreferredSize().width, 5 * 22));

        DefaultTableCellRenderer centerR = new DefaultTableCellRenderer();
        centerR.setHorizontalAlignment(SwingConstants.CENTER);
        for (int i = 2; i <= 6; i++) {
            allDrivesTable.getColumnModel().getColumn(i).setCellRenderer(centerR);
        }
        // Usage column — render as a progress bar with percentage text
        allDrivesTable.getColumnModel().getColumn(7).setCellRenderer(new ProgressBarRenderer());

        allDrivesTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        allDrivesTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        allDrivesTable.getColumnModel().getColumn(2).setPreferredWidth(75);
        allDrivesTable.getColumnModel().getColumn(3).setPreferredWidth(80);
        for (int i = 4; i <= 6; i++) allDrivesTable.getColumnModel().getColumn(i).setPreferredWidth(75);
        allDrivesTable.getColumnModel().getColumn(7).setPreferredWidth(100);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(allDrivesTable), BorderLayout.CENTER);

        // Initial population
        refreshAllDrivesTable();
        return panel;
    }

    /**
     * Table cell renderer that paints a {@link JProgressBar} filling the
     * entire cell for integer percentage values (0–100).
     */
    private static class ProgressBarRenderer extends JPanel
            implements javax.swing.table.TableCellRenderer {
        private final JProgressBar bar = new JProgressBar(0, 100);

        ProgressBarRenderer() {
            setLayout(new BorderLayout());
            bar.setStringPainted(true);
            bar.setBorderPainted(true);
            add(bar, BorderLayout.CENTER);
            // Remove cell padding so the bar fills edge-to-edge
            setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));
        }

        @Override
        public java.awt.Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus,
                int row, int column) {
            int pct = (value instanceof Number n) ? n.intValue() : 0;
            bar.setValue(pct);
            bar.setString(pct + "%");
            if (isSelected) {
                setBackground(table.getSelectionBackground());
            } else {
                setBackground(table.getBackground());
            }
            return this;
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Enumerates the drives off the EDT, then fills the combo and re-selects
     * the entry for the current {@link App#locationDir}.
     */
    private void populateCombo() {
        int generation = ++comboGeneration;
        new SwingWorker<List<DriveEntry>, Void>() {
            @Override
            protected List<DriveEntry> doInBackground() {
                List<DriveEntry> entries = new ArrayList<>();
                for (File root : listDriveRoots()) {
                    if (root.getTotalSpace() == 0) continue;
                    entries.add(new DriveEntry(root));
                }
                return entries;
            }
            @Override
            protected void done() {
                if (generation != comboGeneration) return;
                List<DriveEntry> entries;
                try {
                    entries = get();
                } catch (InterruptedException | ExecutionException ex) {
                    LOG.log(Level.WARNING, "drive enumeration failed", ex);
                    return;
                }
                // Suppress events: removeAllItems() and addItem() both fire ActionEvent,
                // which would invoke applySelectedDrive() and overwrite App.locationDir.
                suppressComboEvents = true;
                try {
                    driveCombo.removeAllItems();
                    for (DriveEntry entry : entries) driveCombo.addItem(entry);
                } finally {
                    suppressComboEvents = false;
                }
                syncComboToLocation();
                for (DriveEntry entry : entries) fetchDriveModel(entry);
            }
        }.execute();
    }

    /**
     * Fetches the drive model and bus type in the background, then repaints
     * the combo. USB drives that the OS reports as fixed disks (e.g. USB SSDs
     * shown as "Local Disk" on Windows) are relabelled "USB Drive".
     */
    private void fetchDriveModel(DriveEntry entry) {
        new SwingWorker<DriveDetailsCache.Details, Void>() {
            @Override
            protected DriveDetailsCache.Details doInBackground() {
                return DriveDetailsCache.get(entry.root);
            }
            @Override
            protected void done() {
                try {
                    DriveDetailsCache.Details d = get();
                    entry.model = (d.model() != null && !d.model().isBlank()) ? d.model() : "\u2014";
                    if ("USB".equalsIgnoreCase(d.busType())) entry.typeDesc = "USB Drive";
                } catch (Exception ex) {
                    entry.model = "\u2014";
                }
                driveCombo.repaint();
            }
        }.execute();
    }

    private void syncComboToLocation() {
        if (App.locationDir == null) return;
        String locPath = App.locationDir.getAbsolutePath();

        // Find the combo entry whose mount point is the longest prefix of
        // the current location. On Windows this still matches by drive root
        // (e.g. "C:\"); on Linux it correctly picks /run/media/user/Drive
        // over / when both are present.
        int bestIndex  = -1;
        int bestLength = -1;
        for (int i = 0; i < driveCombo.getItemCount(); i++) {
            DriveEntry entry = driveCombo.getItemAt(i);
            String mountPath = entry.root.getAbsolutePath();
            String mountPrefix = mountPath.endsWith(File.separator)
                    ? mountPath
                    : mountPath + File.separator;
            if ((locPath.equals(mountPath) || locPath.startsWith(mountPrefix))
                    && mountPath.length() > bestLength) {
                bestIndex  = i;
                bestLength = mountPath.length();
            }
        }
        if (bestIndex >= 0) {
            suppressComboEvents = true;
            driveCombo.setSelectedIndex(bestIndex);
            suppressComboEvents = false;
        }
    }

    private void applySelectedDrive() {
        DriveEntry entry = (DriveEntry) driveCombo.getSelectedItem();
        if (entry == null) return;

        // Always refresh the summary panel to show info for the selected
        // drive, even when it is read-only or otherwise not usable.
        refreshDriveInfo(entry.root);

        // Drive probes can stall on a waking USB drive, so they run off the EDT.
        int generation = ++selectionGeneration;
        new SwingWorker<File, Void>() {
            private boolean valid;

            @Override
            protected File doInBackground() {
                File resolved = resolveLocationForRoot(entry.root);
                valid = resolved != null
                        && DriveChecker.validateTargetDirectory(resolved, false);
                return resolved;
            }

            @Override
            protected void done() {
                if (generation != selectionGeneration) return;
                File resolved;
                try {
                    resolved = get();
                } catch (InterruptedException | ExecutionException ex) {
                    LOG.log(Level.WARNING, "drive selection failed for " + entry.root, ex);
                    return;
                }
                if (resolved == null) return;
                // re-validate on the EDT so the failure dialog is shown
                if (!valid && !DriveChecker.validateTargetDirectory(resolved, true)) return;

                App.setLocationDir(resolved);
                App.saveConfig();
                pathField.setText(App.dataDir.getAbsolutePath());
                Gui.updateDriveTitle();
            }
        }.execute();
    }

    private static File resolveLocationForRoot(File root) {
        return DriveChecker.resolveLocationForRoot(root);
    }

    /** One All Drives table row, with space figures computed off the EDT. */
    private record DriveRow(File root, Object[] cells) {}

    /**
     * Populates the All Drives table. Drive enumeration and space queries run
     * off the EDT; model, interface and filesystem are then fetched per row.
     */
    private void refreshAllDrivesTable() {
        if (allDrivesTableModel == null) return;
        int generation = ++allDrivesGeneration;
        new SwingWorker<List<DriveRow>, Void>() {
            @Override
            protected List<DriveRow> doInBackground() {
                List<DriveRow> rows = new ArrayList<>();
                for (File root : listDriveRoots()) {
                    long total = root.getTotalSpace();
                    if (total == 0) continue;
                    long free  = root.getFreeSpace();
                    long used  = total - free;

                    double totalGb = total / (double) App.GIGABYTE;
                    double usedGb  = used  / (double) App.GIGABYTE;
                    double freeGb  = free  / (double) App.GIGABYTE;
                    double pct     = 100.0 * used / total;

                    rows.add(new DriveRow(root, new Object[]{
                        root.getAbsolutePath(),
                        "loading…",
                        "loading…",
                        "loading…",
                        Math.round(totalGb * 10.0) / 10.0,
                        Math.round(usedGb  * 10.0) / 10.0,
                        Math.round(freeGb  * 10.0) / 10.0,
                        (int) Math.round(pct)
                    }));
                }
                return rows;
            }
            @Override
            protected void done() {
                if (generation != allDrivesGeneration) return;
                List<DriveRow> rows;
                try {
                    rows = get();
                } catch (InterruptedException | ExecutionException ex) {
                    LOG.log(Level.WARNING, "drive enumeration failed", ex);
                    return;
                }
                allDrivesTableModel.setRowCount(0);
                for (DriveRow r : rows) {
                    int rowIndex = allDrivesTableModel.getRowCount();
                    allDrivesTableModel.addRow(r.cells());
                    fetchDriveAttributes(r.root(), rowIndex, generation);
                }
            }
        }.execute();
    }

    /** Fetches model, interface, and filesystem for one All Drives row in the background. */
    private void fetchDriveAttributes(File driveRoot, int row, int generation) {
        new SwingWorker<String[], Void>() {
            @Override
            protected String[] doInBackground() {
                DriveDetailsCache.Details details = DriveDetailsCache.get(driveRoot);
                return new String[]{ details.model(), details.busDisplay(), details.filesystem() };
            }
            @Override
            protected void done() {
                if (generation != allDrivesGeneration) return;
                try {
                    String[] r = get();
                    if (row < allDrivesTableModel.getRowCount()) {
                        allDrivesTableModel.setValueAt(
                                (r[0] != null && !r[0].isBlank()) ? r[0] : "—", row, 1);
                        allDrivesTableModel.setValueAt(
                                (r[1] != null && !r[1].isBlank()) ? r[1] : "—", row, 2);
                        allDrivesTableModel.setValueAt(
                                (r[2] != null && !r[2].isBlank()) ? r[2] : "—", row, 3);
                    }
                } catch (Exception ex) {
                    LOG.log(Level.WARNING, "drive attribute lookup failed for " + driveRoot, ex);
                    if (row < allDrivesTableModel.getRowCount()) {
                        allDrivesTableModel.setValueAt("—", row, 1);
                        allDrivesTableModel.setValueAt("—", row, 2);
                        allDrivesTableModel.setValueAt("—", row, 3);
                    }
                }
            }
        }.execute();
    }

    /**
     * Returns drive roots appropriate for the current OS. On Linux, reads
     * {@code /proc/mounts} via {@link UtilOs#getMountedDrivesLinux()} to
     * discover all mounted drives; on other platforms delegates to
     * {@link File#listRoots()}.
     */
    private static List<File> listDriveRoots() {
        if (App.isLinux()) {
            return UtilsLinux.getMountedDrives();
        } else if (App.isMacOs()) {
            return org.metricus.jdm.os.UtilsMac.getMountedDrives();
        }
        return List.of(File.listRoots());
    }

    private void refreshDriveInfo() {
        refreshDriveInfo(App.locationDir);
    }

    private void refreshDriveInfo(File dir) {
        if (dir == null) return;

        // Update path field immediately on EDT
        String testPath = (App.dataDir != null)
                ? App.dataDir.getAbsolutePath()
                : dir.getAbsolutePath() + File.separator + App.DATADIRNAME;
        pathField.setText(testPath);

        // Reset info labels while loading
        infoModelLabel.setText("Model: loading…");
        infoPartitionLabel.setText("Partition: loading…");
        accessLabel.setText("Access: loading…");
        infoFilesystemLabel.setText("File System: loading…");
        infoInterfaceLabel.setText("Interface: loading…");
        infoSectorSizeLabel.setText("Sector Size: loading…");
        infoUsageLabel.setText("Usage: loading…");
        usageBar.setValue(0);
        usageBar.setString("…");

        int generation = ++infoGeneration;
        new SwingWorker<String[], Void>() {
            @Override
            protected String[] doInBackground() {
                DriveDetailsCache.Details details = DriveDetailsCache.get(dir);
                String model     = details.model();
                String partition = details.partitionId();
                DiskUsageInfo usage;
                try {
                    usage = Util.getDiskUsage(dir.getAbsolutePath());
                } catch (IOException | InterruptedException ex) {
                    LOG.log(Level.WARNING, "getDiskUsage failed", ex);
                    usage = new DiskUsageInfo();
                }
                // Drive attributes — null on unsupported OS
                String filesystem  = details.filesystem();
                String busDisplay  = details.busDisplay();
                String sectorSize  = details.sectorSize();
                File testDir = new File(testPath);
                File accessDir = testDir.isDirectory() ? testDir : dir;
                return new String[]{
                    model, partition,
                    usage.toDisplayString(),
                    String.valueOf(usage.percentUsed),
                    accessDir.canRead()  ? "✓" : "✗",
                    accessDir.canWrite() ? "✓" : "✗",
                    filesystem, busDisplay, sectorSize
                };
            }

            @Override
            protected void done() {
                if (generation != infoGeneration) return;
                try {
                    String[] r = get();
                    infoModelLabel.setText("Model: " + ((r[0] != null && !r[0].isBlank()) ? r[0] : "—"));
                    infoPartitionLabel.setText("Partition: " + ((r[1] != null && !r[1].isBlank()) ? r[1] : "—"));
                    infoUsageLabel.setText("Usage: " + ((r[2] != null && !r[2].isBlank()) ? r[2] : "—"));

                    int pct = 0;
                    try { pct = Integer.parseInt(r[3]); } catch (NumberFormatException ignore) {}
                    usageBar.setValue(pct);
                    usageBar.setString(pct + "%");

                    boolean ok = "✓".equals(r[4]) && "✓".equals(r[5]);
                    accessLabel.setText("Access:  Read " + r[4] + "   Write " + r[5]);
                    accessLabel.setForeground(ok
                            ? new java.awt.Color(0, 180, 0)
                            : java.awt.Color.RED);

                    // Drive attributes — show dash when unavailable
                    infoFilesystemLabel.setText("File System: " + ((r[6] != null && !r[6].isBlank()) ? r[6] : "—"));
                    infoInterfaceLabel.setText("Interface: " + ((r[7] != null && !r[7].isBlank()) ? r[7] : "—"));
                    infoSectorSizeLabel.setText("Sector Size: " + ((r[8] != null && !r[8].isBlank()) ? r[8] : "—"));
                } catch (Exception ex) {
                    LOG.log(Level.WARNING, "refreshDriveInfo worker failed", ex);
                    App.err("Drive info lookup failed: " + ex.getMessage());
                }
            }
        }.execute();
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Synchronises the panel with the current {@link App#locationDir}.
     * Safe to call from any thread.
     */
    public void refresh() {
        Runnable r = () -> {
            // Update the path field immediately — must be the very first thing
            // so it reflects the new App.locationDir before any async work begins.
            if (App.dataDir != null) {
                pathField.setText(App.dataDir.getAbsolutePath());
            } else if (App.locationDir != null) {
                pathField.setText(App.locationDir.getAbsolutePath()
                        + File.separator + App.DATADIRNAME);
            }
            populateCombo();
            refreshAllDrivesTable();
            refreshDriveInfo();
        };
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else SwingUtilities.invokeLater(r);
    }
}
