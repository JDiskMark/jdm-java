package jdiskmark;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import net.miginfocom.swing.MigLayout;

/**
 * Displays all stored {@link SmartSnapshot} records in a sortable table.
 *
 * <p>Shown in the "SMART Reports" tab (Linux only). Rows are loaded from the
 * embedded Derby database. Use the Refresh button or navigate away and back
 * to reload after saving new snapshots.
 *
 * @author jasmine
 */
public class SmartReportsPanel extends JPanel {

    private static final Logger LOGGER = Logger.getLogger(SmartReportsPanel.class.getName());
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final DefaultTableModel model;
    private final JTable table;
    private final JLabel countLabel;
    /** Parallel list to the table rows — used by the selection listener. */
    private java.util.List<SmartSnapshot> lastLoaded = new java.util.ArrayList<>();

    public SmartReportsPanel() {
        super(new BorderLayout());

        // ── Toolbar ──────────────────────────────────────────────────────────
        JPanel toolbar = new JPanel(new MigLayout("insets 8 12 8 12", "[][][][][grow]", "[]"));
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0,
                new Color(80, 80, 80)));

        JButton refreshBtn       = new JButton("Refresh");
        JButton deleteSelectedBtn = new JButton("Delete Selected");
        JButton deleteAllBtn      = new JButton("Delete All");
        countLabel = new JLabel("No snapshots stored.");
        countLabel.setFont(countLabel.getFont().deriveFont(Font.ITALIC));

        refreshBtn.addActionListener(e -> refresh());
        // delete listeners attached below, after table/model are initialized

        toolbar.add(refreshBtn);
        toolbar.add(deleteSelectedBtn);
        toolbar.add(deleteAllBtn);
        toolbar.add(countLabel, "growx");
        add(toolbar, BorderLayout.NORTH);

        // ── Table ─────────────────────────────────────────────────────────────
        model = new DefaultTableModel(
            new String[]{
                "Captured At", "Device", "Model",
                "Health", "Temp (°C)", "Life Left",
                "Power-On Hrs", "Media Errors"
            }, 0
        ) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(22);
        table.getColumnModel().getColumn(0).setPreferredWidth(130);
        table.getColumnModel().getColumn(1).setPreferredWidth(90);
        table.getColumnModel().getColumn(2).setPreferredWidth(220);
        table.getColumnModel().getColumn(3).setPreferredWidth(90);
        table.getColumnModel().getColumn(4).setPreferredWidth(70);
        table.getColumnModel().getColumn(5).setPreferredWidth(70);
        table.getColumnModel().getColumn(6).setPreferredWidth(100);
        table.getColumnModel().getColumn(7).setPreferredWidth(90);

        // Color the Health column (index 3)
        table.getColumnModel().getColumn(3).setCellRenderer(
                new DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(
                    JTable t, Object value, boolean selected, boolean focus,
                    int row, int col) {
                super.getTableCellRendererComponent(t, value, selected, focus, row, col);
                if (!selected) {
                    String v = value != null ? value.toString() : "";
                    if      (v.contains("PASSED")) setForeground(new Color(0x4CAF50));
                    else if (v.contains("FAILED")) setForeground(new Color(0xF44336));
                    else setForeground(null);
                }
                return this;
            }
        });

        // Color the Media Errors column (index 7)
        table.getColumnModel().getColumn(7).setCellRenderer(
                new DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(
                    JTable t, Object value, boolean selected, boolean focus,
                    int row, int col) {
                super.getTableCellRendererComponent(t, value, selected, focus, row, col);
                if (!selected && value != null && !"-".equals(value.toString())) {
                    try {
                        long v = Long.parseLong(value.toString());
                        setForeground(v > 0 ? new Color(0xF44336) : null);
                    } catch (NumberFormatException ignore) {}
                }
                return this;
            }
        });

        JScrollPane scroller = new JScrollPane(table);
        scroller.setBorder(null);
        add(scroller, BorderLayout.CENTER);

        // Row selection — load the clicked snapshot into the SMART tab for full replay
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int viewRow = table.getSelectedRow();
            if (viewRow < 0 || viewRow >= lastLoaded.size()) return;
            int modelRow = table.convertRowIndexToModel(viewRow);
            SmartSnapshot snap = lastLoaded.get(modelRow);
            Gui.loadSnapshot(snap);
        });

        // Delete listeners — attached here so table/model are guaranteed initialized
        deleteSelectedBtn.addActionListener(e -> {
            int viewRow = table.getSelectedRow();
            if (viewRow < 0 || viewRow >= lastLoaded.size()) {
                javax.swing.JOptionPane.showMessageDialog(
                        Gui.mainFrame,
                        "Please select a row first.",
                        "No Selection",
                        javax.swing.JOptionPane.WARNING_MESSAGE);
                return;
            }
            int modelRow = table.convertRowIndexToModel(viewRow);
            SmartSnapshot snap = lastLoaded.get(modelRow);
            String label = snap.getCapturedAt() != null
                    ? snap.getCapturedAt().format(FMT) : "unknown";
            int confirm = javax.swing.JOptionPane.showConfirmDialog(
                    Gui.mainFrame,
                    "Delete snapshot from " + label + "?\nThis cannot be undone.",
                    "Confirm Delete",
                    javax.swing.JOptionPane.YES_NO_OPTION,
                    javax.swing.JOptionPane.WARNING_MESSAGE);
            if (confirm == javax.swing.JOptionPane.YES_OPTION) {
                SmartSnapshot.delete(snap.getId());
                refresh();
            }
        });

        deleteAllBtn.addActionListener(e -> {
            int total = model.getRowCount();
            if (total == 0) {
                javax.swing.JOptionPane.showMessageDialog(
                        Gui.mainFrame,
                        "No snapshots to delete.",
                        "Nothing to Delete",
                        javax.swing.JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            int confirm = javax.swing.JOptionPane.showConfirmDialog(
                    Gui.mainFrame,
                    "Delete all " + total + " snapshot(s)?\nThis cannot be undone.",
                    "Confirm Delete All",
                    javax.swing.JOptionPane.YES_NO_OPTION,
                    javax.swing.JOptionPane.WARNING_MESSAGE);
            if (confirm == javax.swing.JOptionPane.YES_OPTION) {
                SmartSnapshot.deleteAll();
                refresh();
            }
        });
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Reloads all snapshots from the database and refreshes the table.
     * Safe to call from any thread.
     */
    public void refresh() {
        SwingUtilities.invokeLater(() -> {
            model.setRowCount(0);
            lastLoaded = new java.util.ArrayList<>();
            try {
                List<SmartSnapshot> snaps = SmartSnapshot.findAll();
                lastLoaded = snaps;
                for (SmartSnapshot s : snaps) {
                    String capturedAt = s.getCapturedAt() != null
                            ? s.getCapturedAt().format(FMT) : "-";
                    String health = s.getSmartPassed() == null ? "-"
                            : Boolean.TRUE.equals(s.getSmartPassed())
                            ? "\u2714 PASSED" : "\u2718 FAILED";
                    String temp     = s.getTempC()        != null ? s.getTempC() + " \u00b0C" : "-";
                    String lifeLeft = s.getPercentageUsed() != null
                            ? Math.max(0, 100 - s.getPercentageUsed()) + "%" : "-";
                    String poh      = s.getPowerOnHours()  != null
                            ? String.valueOf(s.getPowerOnHours()) : "-";
                    String mediaErr = s.getMediaErrors()   != null
                            ? String.valueOf(s.getMediaErrors()) : "-";
                    model.addRow(new Object[]{
                        capturedAt,
                        orDash(s.getDeviceName()),
                        orDash(s.getModelName()),
                        health, temp, lifeLeft, poh, mediaErr
                    });
                }
                int count = model.getRowCount();
                countLabel.setText(count == 0
                        ? "No snapshots stored."
                        : count + " snapshot" + (count == 1 ? "" : "s") + " stored.");
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Failed to load SmartSnapshots", ex);
                countLabel.setText("Error loading snapshots — see log.");
            }
        });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String orDash(String s) {
        return (s != null && !s.isBlank()) ? s : "-";
    }
}
