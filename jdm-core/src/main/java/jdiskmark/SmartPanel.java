package jdiskmark;

import java.awt.Color;
import java.awt.Font;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import net.miginfocom.swing.MigLayout;

/**
 * Displays parsed S.M.A.R.T. data in the main "SMART" tab.
 *
 * Call {@link #populate(Smart)} on the Event Dispatch Thread (or from
 * any thread — it marshals to the EDT internally) after the benchmark runner
 * has retrieved and parsed the {@code smartctl} JSON output.
 *
 * <p>Layout sections:
 * <ul>
 *   <li><b>Drive Info</b>  – model, serial, firmware, capacity, protocol</li>
 *   <li><b>Health</b>      – SMART status, temperature, power-on hours, power cycles</li>
 *   <li><b>NVMe Health Log</b> – available spare, % used, data written/read, errors (NVMe only)</li>
 *   <li><b>ATA Attributes</b> – scrollable table of all ATA SMART attributes (SATA only)</li>
 * </ul>
 *
 * @author jasmine
 */
public class SmartPanel extends javax.swing.JPanel {

    // -------------------------------------------------------------------------
    // Drive Info labels
    // -------------------------------------------------------------------------
    private final JLabel modelValueLabel        = value("-");
    private final JLabel serialValueLabel       = value("-");
    private final JLabel firmwareValueLabel     = value("-");
    private final JLabel capacityValueLabel     = value("-");
    private final JLabel protocolValueLabel     = value("-");

    // -------------------------------------------------------------------------
    // Health labels
    // -------------------------------------------------------------------------
    private final JLabel statusValueLabel       = value("-");
    private final JLabel tempValueLabel         = value("-");
    private final JLabel powerOnValueLabel      = value("-");
    private final JLabel powerCyclesValueLabel  = value("-");

    // -------------------------------------------------------------------------
    // NVMe-specific labels
    // -------------------------------------------------------------------------
    private final JLabel spareValueLabel        = value("-");
    private final JLabel usedPctValueLabel      = value("-");
    private final JLabel writtenValueLabel      = value("-");
    private final JLabel readValueLabel         = value("-");
    private final JLabel mediaErrValueLabel     = value("-");
    private final JLabel errLogValueLabel       = value("-");
    private final JLabel warnTempValueLabel     = value("-");
    private final JLabel critCompValueLabel     = value("-");
    private       JPanel nvmeSection;

    // -------------------------------------------------------------------------
    // ATA Attributes table
    // -------------------------------------------------------------------------
    private final DefaultTableModel ataModel;
    private final JTable            ataTable;
    private       JPanel            ataSection;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public SmartPanel() {
        // ATA table model
        ataModel = new DefaultTableModel(
            new String[]{"ID", "Attribute Name", "Value", "Worst", "Threshold", "Raw", "Status"},
            0
        ) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        ataTable = new JTable(ataModel);
        ataTable.setFillsViewportHeight(true);
        ataTable.getColumnModel().getColumn(0).setPreferredWidth(30);
        ataTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        ataTable.getColumnModel().getColumn(2).setPreferredWidth(45);
        ataTable.getColumnModel().getColumn(3).setPreferredWidth(45);
        ataTable.getColumnModel().getColumn(4).setPreferredWidth(60);
        ataTable.getColumnModel().getColumn(5).setPreferredWidth(80);
        ataTable.getColumnModel().getColumn(6).setPreferredWidth(60);

        buildLayout();
    }

    // -------------------------------------------------------------------------
    // Layout
    // -------------------------------------------------------------------------

    private void buildLayout() {
        setLayout(new MigLayout("insets 12, fillx", "[grow]", "[]8[]8[]8[]"));

        // --- Drive Info section ---
        JPanel driveSection = section("Drive Info");
        driveSection.setLayout(new MigLayout("insets 8, wrap 4", "[120][grow][120][grow]"));
        driveSection.add(label("Model:"));
        driveSection.add(modelValueLabel,    "growx");
        driveSection.add(label("Protocol:"));
        driveSection.add(protocolValueLabel, "growx, wrap");
        driveSection.add(label("Serial:"));
        driveSection.add(serialValueLabel,   "growx");
        driveSection.add(label("Firmware:"));
        driveSection.add(firmwareValueLabel, "growx, wrap");
        driveSection.add(label("Capacity:"));
        driveSection.add(capacityValueLabel, "growx, span 3");
        add(driveSection, "growx, wrap");

        // --- Health section ---
        JPanel healthSection = section("Health");
        healthSection.setLayout(new MigLayout("insets 8, wrap 4", "[120][grow][120][grow]"));
        healthSection.add(label("SMART Status:"));
        healthSection.add(statusValueLabel,      "growx");
        healthSection.add(label("Temperature:"));
        healthSection.add(tempValueLabel,        "growx, wrap");
        healthSection.add(label("Power-On Hours:"));
        healthSection.add(powerOnValueLabel,     "growx");
        healthSection.add(label("Power Cycles:"));
        healthSection.add(powerCyclesValueLabel, "growx");
        add(healthSection, "growx, wrap");

        // --- NVMe Health Log section (hidden until populated) ---
        nvmeSection = section("NVMe Health Log");
        nvmeSection.setLayout(new MigLayout("insets 8, wrap 4", "[150][grow][150][grow]"));
        nvmeSection.add(label("Available Spare:"));
        nvmeSection.add(spareValueLabel,     "growx");
        nvmeSection.add(label("% Used (PE cycles):"));
        nvmeSection.add(usedPctValueLabel,   "growx, wrap");
        nvmeSection.add(label("Data Written:"));
        nvmeSection.add(writtenValueLabel,   "growx");
        nvmeSection.add(label("Data Read:"));
        nvmeSection.add(readValueLabel,      "growx, wrap");
        nvmeSection.add(label("Media Errors:"));
        nvmeSection.add(mediaErrValueLabel,  "growx");
        nvmeSection.add(label("Error Log Entries:"));
        nvmeSection.add(errLogValueLabel,    "growx, wrap");
        nvmeSection.add(label("Warning Temp Time:"));
        nvmeSection.add(warnTempValueLabel,  "growx");
        nvmeSection.add(label("Critical Comp Time:"));
        nvmeSection.add(critCompValueLabel,  "growx");
        nvmeSection.setVisible(true);  // shown by default with dash placeholders; populated after auth
        add(nvmeSection, "growx, wrap");

        // --- ATA Attributes section (hidden until populated) ---
        ataSection = section("ATA SMART Attributes");
        ataSection.setLayout(new MigLayout("insets 8, fill", "[grow]", "[grow]"));
        JScrollPane scrollPane = new JScrollPane(ataTable);
        scrollPane.setPreferredSize(new java.awt.Dimension(600, 200));
        ataSection.add(scrollPane, "grow");
        ataSection.setVisible(false);
        add(ataSection, "growx, wrap");
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Populates all fields from the given {@link Smart}.
     * Safe to call from any thread — marshals to the EDT automatically.
     *
     * @param data the parsed SMART data; if {@code null} the panel is cleared
     */
    public void populate(Smart data) {
        SwingUtilities.invokeLater(() -> {
            if (data == null) {
                clear();
                return;
            }
            fillDriveInfo(data);
            fillHealth(data);
            fillNvme(data.getNvmeHealthLog());
            fillAtaAttributes(data.getAtaSmartAttributes());
            revalidate();
            repaint();
        });
    }

    /** Resets all fields to their default placeholder values. */
    public void clear() {
        SwingUtilities.invokeLater(() -> {
            for (JLabel l : new JLabel[]{
                modelValueLabel, serialValueLabel, firmwareValueLabel,
                capacityValueLabel, protocolValueLabel,
                statusValueLabel, tempValueLabel,
                powerOnValueLabel, powerCyclesValueLabel,
                spareValueLabel, usedPctValueLabel, writtenValueLabel,
                readValueLabel, mediaErrValueLabel, errLogValueLabel,
                warnTempValueLabel, critCompValueLabel
            }) {
                l.setText("-");
                l.setForeground(null);
            }
            ataModel.setRowCount(0);
            nvmeSection.setVisible(true);  // keep visible so placeholders remain shown
            ataSection.setVisible(false);
            revalidate();
            repaint();
        });
    }

    // -------------------------------------------------------------------------
    // Private fill helpers
    // -------------------------------------------------------------------------

    private void fillDriveInfo(Smart d) {
        modelValueLabel.setText(orDash(d.getModelName()));
        serialValueLabel.setText(orDash(d.getSerialNumber()));
        firmwareValueLabel.setText(orDash(d.getFirmwareVersion()));

        if (d.getUserCapacity() != null) {
            capacityValueLabel.setText(d.getUserCapacity().getCapacityGb() + " GB");
        }
        if (d.getDevice() != null) {
            protocolValueLabel.setText(orDash(d.getDevice().getProtocol()));
        }
    }

    private void fillHealth(Smart d) {
        // SMART status
        if (d.getSmartStatus() != null) {
            boolean passed = Boolean.TRUE.equals(d.getSmartStatus().isPassed());
            statusValueLabel.setText(passed ? "PASSED ✔" : "FAILED ✘");
            statusValueLabel.setForeground(passed ? new Color(0x4CAF50) : new Color(0xF44336));
        }

        // Temperature
        if (d.getTemperature() != null && d.getTemperature().getCurrent() != null) {
            int temp = d.getTemperature().getCurrent();
            tempValueLabel.setText(temp + " °C");
            // colour-code: green < 45, amber < 60, red ≥ 60
            if (temp >= 60) {
                tempValueLabel.setForeground(new Color(0xF44336));
            } else if (temp >= 45) {
                tempValueLabel.setForeground(new Color(0xFF9800));
            } else {
                tempValueLabel.setForeground(new Color(0x4CAF50));
            }
        }

        // Power-on hours
        if (d.getPowerOnTime() != null && d.getPowerOnTime().getHours() != null) {
            powerOnValueLabel.setText(d.getPowerOnTime().getHours() + " h");
        }

        // Power cycles
        if (d.getPowerCycleCount() != null) {
            powerCyclesValueLabel.setText(String.valueOf(d.getPowerCycleCount()));
        }
    }

    private void fillNvme(Smart.NvmeHealthLog nvme) {
        if (nvme == null) {
            nvmeSection.setVisible(false);
            return;
        }
        nvmeSection.setVisible(true);

        setNvmeField(spareValueLabel,    nvme.getAvailableSpare(),        "%",
                nvme.getAvailableSpareThreshold(), true);
        setNvmeField(usedPctValueLabel,  nvme.getPercentageUsed(),        "%",  null, false);
        writtenValueLabel.setText(nvme.getDataWrittenGb() + " GB  (" + nvme.getDataUnitsWritten() + " units)");
        readValueLabel.setText(nvme.getDataReadGb() + " GB  (" + nvme.getDataUnitsRead() + " units)");

        // Error counts — colour red on non-zero
        setCountField(mediaErrValueLabel,  nvme.getMediaErrors());
        setCountField(errLogValueLabel,    nvme.getNumErrLogEntries());

        warnTempValueLabel.setText(nvme.getWarningTempTime() != null
                ? nvme.getWarningTempTime() + " min" : "-");
        critCompValueLabel.setText(nvme.getCriticalCompTime() != null
                ? nvme.getCriticalCompTime() + " min" : "-");

        // Flag critical warning
        if (nvme.hasCriticalWarning()) {
            statusValueLabel.setText("CRITICAL WARNING (" + nvme.getCriticalWarning() + ") ✘");
            statusValueLabel.setForeground(new Color(0xF44336));
        }
    }

    private void fillAtaAttributes(Smart.AtaSmartAttributes ata) {
        ataModel.setRowCount(0);
        if (ata == null || ata.getTable() == null || ata.getTable().isEmpty()) {
            ataSection.setVisible(false);
            return;
        }
        ataSection.setVisible(true);
        List<Smart.AtaAttribute> table = ata.getTable();
        for (Smart.AtaAttribute attr : table) {
            String raw    = attr.getRaw() != null ? attr.getRaw().getString() : "-";
            String status = attr.isFailing() ? "FAILING ✘" : "OK";
            ataModel.addRow(new Object[]{
                attr.getId(),
                orDash(attr.getName()),
                attr.getValue(),
                attr.getWorst(),
                attr.getThresh(),
                raw,
                status
            });
        }
    }

    // -------------------------------------------------------------------------
    // Small helpers
    // -------------------------------------------------------------------------

    /** Labels a field value with optional threshold colouring. */
    private void setNvmeField(JLabel lbl, Integer value, String suffix,
                              Integer threshold, boolean higherIsBetter) {
        if (value == null) { lbl.setText("-"); return; }
        lbl.setText(value + suffix);
        if (threshold != null) {
            boolean warn = higherIsBetter ? value <= threshold : value >= threshold;
            lbl.setForeground(warn ? new Color(0xF44336) : new Color(0x4CAF50));
        }
    }

    /** Sets a count field to red on non-zero. */
    private void setCountField(JLabel lbl, Long value) {
        if (value == null) { lbl.setText("-"); return; }
        lbl.setText(String.valueOf(value));
        lbl.setForeground(value > 0 ? new Color(0xF44336) : null);
    }

    /** Returns {@code s} if non-null/non-empty, else {@code "-"}. */
    private static String orDash(String s) {
        return (s != null && !s.isBlank()) ? s : "-";
    }

    /** Creates a right-aligned bold key label. */
    private static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        return l;
    }

    /** Creates a left-aligned plain value label. */
    private static JLabel value(String text) {
        return new JLabel(text);
    }

    /** Creates a titled, etched-border section panel. */
    private static JPanel section(String title) {
        JPanel p = new JPanel();
        p.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), title));
        return p;
    }
}
