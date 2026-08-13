package jdiskmark;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
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
        final String pathLabel;    // "/ [SSD]"
        final String capacity;     // "467 GB"
        String       model = "loading\u2026";   // filled in asynchronously

        DriveEntry(File root) {
            this.root = root;
            double totalGb  = root.getTotalSpace() / (double) App.GIGABYTE;
            String typeDesc = Util.getDriveType(root);
            String type     = (typeDesc != null && !typeDesc.isBlank())
                              ? "  [" + typeDesc + "]" : "";
            pathLabel = root.getAbsolutePath() + type;
            capacity  = String.format("%.0f GB", totalGb);
        }

        @Override public String toString() {
            // path   —   model   —   capacity
            return pathLabel + "   \u2014   " + model + "   \u2014   " + capacity;
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
        refreshButton.addActionListener(e -> refresh());
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

    private void populateCombo() {
        // Suppress events: removeAllItems() and addItem() both fire ActionEvent,
        // which would invoke applySelectedDrive() and overwrite App.locationDir.
        suppressComboEvents = true;
        try {
            driveCombo.removeAllItems();
            for (File root : listDriveRoots()) {
                if (root.getTotalSpace() == 0) continue;
                DriveEntry entry = new DriveEntry(root);
                driveCombo.addItem(entry);
                // Fetch the drive model in the background, then refresh the combo
                new SwingWorker<String, Void>() {
                    @Override
                    protected String doInBackground() {
                        return Util.getDriveModel(root);
                    }
                    @Override
                    protected void done() {
                        try {
                            String m = get();
                            entry.model = (m != null && !m.isBlank()) ? m : "\u2014";
                        } catch (Exception ex) {
                            entry.model = "\u2014";
                        }
                        driveCombo.repaint();
                    }
                }.execute();
            }
        } finally {
            suppressComboEvents = false;
        }
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

        File resolved = resolveLocationForRoot(entry.root);
        if (resolved == null) {
            return;
        }

        if (!DriveChecker.validateTargetDirectory(resolved, true)) {
            return;
        }

        App.setLocationDir(resolved);
        App.saveConfig();
        Gui.updateDiskInfo();
    }

    private static File resolveLocationForRoot(File root) {
        File home = new File(System.getProperty("user.home", ""));
        if (home.exists()) {
            java.nio.file.Path homeRoot = home.toPath().getRoot();
            if (homeRoot != null && homeRoot.equals(root.toPath())) {
                File candidate = new File(home, App.DATADIRNAME);
                if (candidate.exists() ? candidate.canWrite() : home.canWrite()) {
                    return home;
                }
            }
        }
        if (root.canRead() && root.canWrite()) return root;
        return null;
    }

    /**
     * Populates the All Drives table. Each row includes the drive model,
     * which is fetched asynchronously via a SwingWorker to avoid blocking
     * the EDT.
     */
    private void refreshAllDrivesTable() {
        if (allDrivesTableModel == null) return;
        allDrivesTableModel.setRowCount(0);

        for (File root : listDriveRoots()) {
            long total = root.getTotalSpace();
            long free  = root.getFreeSpace();
            long used  = total - free;
            if (total == 0) continue;

            double totalGb = total / (double) App.GIGABYTE;
            double usedGb  = used  / (double) App.GIGABYTE;
            double freeGb  = free  / (double) App.GIGABYTE;
            double pct     = 100.0 * used / total;

            // Add row with placeholders — filled in asynchronously
            int rowIndex = allDrivesTableModel.getRowCount();
            allDrivesTableModel.addRow(new Object[]{
                root.getAbsolutePath(),
                "loading…",
                "loading…",
                "loading…",
                Math.round(totalGb * 10.0) / 10.0,
                Math.round(usedGb  * 10.0) / 10.0,
                Math.round(freeGb  * 10.0) / 10.0,
                (int) Math.round(pct)
            });

            // Fetch model, interface, and filesystem in background
            final int row = rowIndex;
            final File driveRoot = root;
            new SwingWorker<String[], Void>() {
                @Override
                protected String[] doInBackground() {
                    String model = Util.getDriveModel(driveRoot);
                    String busType = Util.getBusType(driveRoot.toPath());
                    String busDisplay = busType;
                    if ("USB".equalsIgnoreCase(busType)) {
                        String usbVer = Util.getUsbVersion(driveRoot.toPath());
                        if (usbVer != null) busDisplay = busType + " " + usbVer;
                    }
                    String filesystem = Util.getFilesystem(driveRoot.toPath());
                    return new String[]{ model, busDisplay, filesystem };
                }
                @Override
                protected void done() {
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
    }

    /**
     * Returns drive roots appropriate for the current OS. On Linux, reads
     * {@code /proc/mounts} via {@link UtilOs#getMountedDrivesLinux()} to
     * discover all mounted drives; on other platforms delegates to
     * {@link File#listRoots()}.
     */
    private static List<File> listDriveRoots() {
        if (App.isLinux()) {
            return UtilOs.getMountedDrivesLinux();
        } else if (App.isMacOs()) {
            return org.metricus.jdm.os.UtilsMacOs.getMountedDrives();
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

        new SwingWorker<String[], Void>() {
            @Override
            protected String[] doInBackground() {
                String model     = Util.getDriveModel(dir);
                String partition = Util.getPartitionId(dir.toPath());
                DiskUsageInfo usage;
                try {
                    usage = Util.getDiskUsage(dir.getAbsolutePath());
                } catch (IOException | InterruptedException ex) {
                    LOG.log(Level.WARNING, "getDiskUsage failed", ex);
                    usage = new DiskUsageInfo();
                }
                // Drive attributes — null on unsupported OS
                String filesystem  = Util.getFilesystem(dir.toPath());
                String busType     = Util.getBusType(dir.toPath());
                String busDisplay  = busType;
                if ("USB".equalsIgnoreCase(busType)) {
                    String usbVer = Util.getUsbVersion(dir.toPath());
                    if (usbVer != null) busDisplay = busType + " " + usbVer;
                }
                String sectorSize  = Util.getSectorSize(dir.toPath());
                return new String[]{
                    model, partition,
                    usage.toDisplayString(),
                    String.valueOf(usage.percentUsed),
                    dir.canRead()  ? "✓" : "✗",
                    dir.canWrite() ? "✓" : "✗",
                    filesystem, busDisplay, sectorSize
                };
            }

            @Override
            protected void done() {
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
            syncComboToLocation();
            refreshAllDrivesTable();
            refreshDriveInfo();
        };
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else SwingUtilities.invokeLater(r);
    }
}
