package jdiskmark;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * Drives tab — lets the user select the benchmark target drive, shows drive
 * capacity info, and manages the test-directory path.
 *
 * <pre>
 * ┌──────────────────────────────────────────────────────┐
 * │  Drive:  [combo box ──────────────────────────────]  │  ← NORTH
 * ├─────────────────────────┬────────────────────────────┤
 * │                         │                            │
 * │   Drive Info            │   All Drives Table         │  ← CENTER (JSplitPane)
 * │   (selected drive)      │                            │
 * │                         │                            │
 * ├─────────────────────────┴────────────────────────────┤
 * │  Test Dir: [path ────────────────────] [Browse] [Open]│  ← SOUTH
 * └──────────────────────────────────────────────────────┘
 * </pre>
 */
public class DrivesPanel extends JPanel {

    // -----------------------------------------------------------------------
    // Inner type — one item in the drive combo box
    // -----------------------------------------------------------------------

    private static class DriveEntry {
        final File   root;
        final String label;

        DriveEntry(File root) {
            this.root = root;
            double totalGb  = root.getTotalSpace() / (double) App.GIGABYTE;
            String typeDesc = Util.getDriveType(root);
            String type     = (typeDesc != null && !typeDesc.isBlank())
                              ? "  [" + typeDesc + "]" : "";
            label = root.getAbsolutePath() + type + "   —   "
                    + String.format("%.0f GB", totalGb);
        }

        @Override public String toString() { return label; }
    }

    // -----------------------------------------------------------------------
    // UI fields
    // -----------------------------------------------------------------------

    private final JComboBox<DriveEntry> driveCombo;

    // Drive info labels (left pane)
    private JLabel        infoModelLabel;
    private JLabel        infoPartitionLabel;
    private JLabel        infoUsageLabel;
    private JLabel        accessLabel;
    private JProgressBar  usageBar;

    // Test directory row (bottom)
    private final JTextField pathField;
    private final JButton    browseButton;
    private final JButton    openButton;

    // All-drives table (right pane)
    private final DefaultTableModel tableModel;
    private final JTable            table;

    private static final String[] COLUMNS = {
        "Drive / Mount", "Total (GB)", "Used (GB)", "Free (GB)", "Usage %"
    };

    private static final Logger LOG = Logger.getLogger(DrivesPanel.class.getName());

    /** Prevents combo listener from firing during a programmatic refresh(). */
    private boolean suppressComboEvents = false;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public DrivesPanel() {
        setLayout(new BorderLayout(0, 0));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // ── NORTH — Drive selector row ───────────────────────────────────────
        JPanel selectorRow = new JPanel(new BorderLayout(6, 0));
        selectorRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));

        JLabel driveLabel = new JLabel("Drive:");
        driveLabel.setFont(driveLabel.getFont().deriveFont(Font.BOLD));
        driveLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
        selectorRow.add(driveLabel, BorderLayout.WEST);

        driveCombo = new JComboBox<>();
        driveCombo.setMaximumRowCount(12);
        populateCombo();
        selectorRow.add(driveCombo, BorderLayout.CENTER);

        add(selectorRow, BorderLayout.NORTH);

        // ── CENTER — JSplitPane: Drive Info (left) | All Drives Table (right) -
        // Left pane: Drive Info
        JPanel leftPane = buildDriveInfoPane();

        // Right pane: All Drives table
        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int col) {
                return switch (col) {
                    case 1, 2, 3, 4 -> Double.class;
                    default         -> String.class;
                };
            }
        };

        table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setRowHeight(22);
        table.setAutoCreateRowSorter(true);
        table.getTableHeader().setFont(
                table.getTableHeader().getFont().deriveFont(Font.BOLD));

        DefaultTableCellRenderer rightR = new DefaultTableCellRenderer();
        rightR.setHorizontalAlignment(SwingConstants.RIGHT);
        for (int i = 1; i <= 4; i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(rightR);
        }
        table.getColumnModel().getColumn(0).setPreferredWidth(180);
        for (int i = 1; i <= 3; i++) table.getColumnModel().getColumn(i).setPreferredWidth(75);
        table.getColumnModel().getColumn(4).setPreferredWidth(60);

        JPanel rightPane = new JPanel(new BorderLayout());
        rightPane.setBorder(BorderFactory.createTitledBorder("All Drives"));
        rightPane.add(new JScrollPane(table), BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                                              leftPane, rightPane);
        splitPane.setResizeWeight(0.45);      // left pane gets 45 % initially
        splitPane.setDividerSize(5);
        splitPane.setBorder(null);
        add(splitPane, BorderLayout.CENTER);

        // ── SOUTH — Test Directory row ────────────────────────────────────────
        JPanel southPanel = new JPanel(new BorderLayout(4, 0));
        southPanel.setBorder(BorderFactory.createTitledBorder("Test Directory"));

        pathField = new JTextField();
        pathField.setEditable(false);
        southPanel.add(pathField, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new BorderLayout(4, 0));
        browseButton = new JButton("Browse…");
        openButton   = new JButton("Open");
        btnPanel.add(browseButton, BorderLayout.WEST);
        btnPanel.add(openButton,   BorderLayout.EAST);
        southPanel.add(btnPanel, BorderLayout.EAST);

        add(southPanel, BorderLayout.SOUTH);

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
        refreshTable();
        refreshDriveInfo();
    }

    // -----------------------------------------------------------------------
    // Drive Info pane builder
    // -----------------------------------------------------------------------

    /**
     * Builds the left pane containing drive model / partition / usage info.
     * Uses GridBagLayout so all labels are left-aligned with no dead space.
     */
    private JPanel buildDriveInfoPane() {
        infoModelLabel     = new JLabel("Model: —");
        infoPartitionLabel = new JLabel("Partition: —");
        infoUsageLabel     = new JLabel("Usage: —");
        accessLabel        = new JLabel("Access: —");
        usageBar           = new JProgressBar(0, 100);
        usageBar.setStringPainted(true);

        JPanel inner = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx  = 0;
        gbc.fill   = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(3, 4, 3, 4);

        gbc.gridy = 0; inner.add(infoModelLabel,     gbc);
        gbc.gridy = 1; inner.add(infoPartitionLabel, gbc);
        gbc.gridy = 2; inner.add(infoUsageLabel,     gbc);
        gbc.gridy = 3; inner.add(accessLabel,        gbc);
        gbc.gridy = 4; inner.add(usageBar,           gbc);

        // Push content to the top
        gbc.gridy   = 5;
        gbc.weighty = 1.0;
        gbc.fill    = GridBagConstraints.BOTH;
        inner.add(new JPanel(), gbc);   // filler

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(BorderFactory.createTitledBorder("Drive Info"));
        wrapper.add(inner, BorderLayout.CENTER);
        return wrapper;
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
            for (File root : File.listRoots()) {
                if (root.getTotalSpace() == 0) continue;
                driveCombo.addItem(new DriveEntry(root));
            }
        } finally {
            suppressComboEvents = false;
        }
    }

    private void syncComboToLocation() {
        if (App.locationDir == null) return;
        java.nio.file.Path locRoot = App.locationDir.toPath().getRoot();
        if (locRoot == null) return;

        for (int i = 0; i < driveCombo.getItemCount(); i++) {
            DriveEntry entry = driveCombo.getItemAt(i);
            if (entry.root.toPath().equals(locRoot)
                    || entry.root.getAbsolutePath().equalsIgnoreCase(locRoot.toString())) {
                suppressComboEvents = true;
                driveCombo.setSelectedIndex(i);
                suppressComboEvents = false;
                return;
            }
        }
    }

    private void applySelectedDrive() {
        DriveEntry entry = (DriveEntry) driveCombo.getSelectedItem();
        if (entry == null) return;

        File resolved = resolveLocationForRoot(entry.root);
        if (resolved == null) {
            accessLabel.setText("Access: ✗  No writable location found on this drive");
            accessLabel.setForeground(java.awt.Color.RED);
            return;
        }

        if (!DriveAccessChecker.validateTargetDirectory(resolved, true)) {
            accessLabel.setText("Access: ✗  Cannot read/write test directory");
            accessLabel.setForeground(java.awt.Color.RED);
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

    private void refreshTable() {
        tableModel.setRowCount(0);
        for (File root : File.listRoots()) {
            long total = root.getTotalSpace();
            long free  = root.getFreeSpace();
            long used  = total - free;
            if (total == 0) continue;

            double totalGb = total / (double) App.GIGABYTE;
            double usedGb  = used  / (double) App.GIGABYTE;
            double freeGb  = free  / (double) App.GIGABYTE;
            double pct     = 100.0 * used / total;

            tableModel.addRow(new Object[]{
                root.getAbsolutePath(),
                Math.round(totalGb * 10.0) / 10.0,
                Math.round(usedGb  * 10.0) / 10.0,
                Math.round(freeGb  * 10.0) / 10.0,
                Math.round(pct     * 10.0) / 10.0
            });
        }
    }

    private void refreshDriveInfo() {
        if (App.locationDir == null) return;

        // Update path field immediately on EDT
        String testPath = (App.dataDir != null)
                ? App.dataDir.getAbsolutePath()
                : App.locationDir.getAbsolutePath() + File.separator + App.DATADIRNAME;
        pathField.setText(testPath);

        // Reset info labels while loading
        infoModelLabel.setText("Model: loading…");
        infoPartitionLabel.setText("Partition: loading…");
        infoUsageLabel.setText("Usage: loading…");
        accessLabel.setText("Access: loading…");
        usageBar.setValue(0);
        usageBar.setString("…");

        final File dir = App.locationDir;

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
                return new String[]{
                    model, partition,
                    usage.toDisplayString(),
                    String.valueOf(usage.percentUsed),
                    dir.canRead()  ? "✓" : "✗",
                    dir.canWrite() ? "✓" : "✗"
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
            refreshTable();
            refreshDriveInfo();
        };
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else SwingUtilities.invokeLater(r);
    }
}
