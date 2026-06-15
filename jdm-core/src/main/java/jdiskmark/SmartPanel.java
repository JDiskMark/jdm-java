package jdiskmark;

import java.awt.BorderLayout;
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
 * any thread — it marshals to the EDT internally) after SMART data has
 * been retrieved and parsed from {@code smartctl --json -a}.
 *
 * <p>Layout sections (scrollable):
 * <ul>
 *   <li><b>Drive Info</b>          – model, serial, firmware, capacity, protocol</li>
 *   <li><b>NVMe Device Details</b> – version, controller ID, OUI, capacities (NVMe only)</li>
 *   <li><b>Health</b>              – SMART status, temperature, power-on hours, power cycles</li>
 *   <li><b>NVMe Health Log</b>     – spare, % used, data written/read, errors, temp sensors</li>
 *   <li><b>ATA Attributes</b>      – scrollable attribute table (SATA only)</li>
 * </ul>
 *
 * @author jasmine
 */
public class SmartPanel extends JPanel {

    // ── Column spec used by every section — keeps labels/values aligned ──────
    private static final String COL_SPEC = "[170][grow][170][grow]";

    // -------------------------------------------------------------------------
    // Drive Info labels
    // -------------------------------------------------------------------------
    private final JLabel modelValueLabel        = value("-");
    private final JLabel serialValueLabel       = value("-");
    private final JLabel firmwareValueLabel     = value("-");
    private final JLabel capacityValueLabel     = value("-");
    private final JLabel protocolValueLabel     = value("-");

    // -------------------------------------------------------------------------
    // NVMe Device Details labels
    // -------------------------------------------------------------------------
    private final JLabel nvmeVersionValueLabel      = value("-");
    private final JLabel nvmeControllerIdValueLabel = value("-");
    private final JLabel nvmeOuiValueLabel          = value("-");
    private final JLabel nvmeVendorValueLabel       = value("-");
    private final JLabel nvmeTotalCapValueLabel     = value("-");
    private final JLabel nvmeUnallocCapValueLabel   = value("-");
    private final JLabel nvmeNsCountValueLabel      = value("-");
    private final JLabel localTimeValueLabel        = value("-");
    private       JPanel nvmeDevSection;

    // -------------------------------------------------------------------------
    // Health labels
    // -------------------------------------------------------------------------
    private final JLabel statusValueLabel       = value("-");
    private final JLabel tempValueLabel         = value("-");
    private final JLabel powerOnValueLabel      = value("-");
    private final JLabel powerCyclesValueLabel  = value("-");

    // -------------------------------------------------------------------------
    // NVMe Health Log labels
    // -------------------------------------------------------------------------
    private final JLabel spareValueLabel        = value("-");
    private final JLabel usedPctValueLabel      = value("-");
    private final JLabel writtenValueLabel      = value("-");
    private final JLabel readValueLabel         = value("-");
    private final JLabel mediaErrValueLabel     = value("-");
    private final JLabel errLogValueLabel       = value("-");
    private final JLabel warnTempValueLabel     = value("-");
    private final JLabel critCompValueLabel     = value("-");
    private final JLabel tempSensor1ValueLabel  = value("-");
    private final JLabel tempSensor2ValueLabel  = value("-");
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
        super(new BorderLayout());

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

        // Inner content panel — sections are added here
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new MigLayout("insets 12, fillx", "[grow]", "[]8[]8[]8[]8[]"));

        buildLayout(contentPanel);

        // Wrap in a scroll pane so the tab is always scrollable
        JScrollPane scroller = new JScrollPane(contentPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroller.setBorder(null);
        scroller.getVerticalScrollBar().setUnitIncrement(16);
        add(scroller, BorderLayout.CENTER);
    }

    // -------------------------------------------------------------------------
    // Layout
    // -------------------------------------------------------------------------

    private void buildLayout(JPanel p) {

        // --- Drive Info section ---
        JPanel driveSection = section("Drive Info");
        driveSection.setLayout(new MigLayout("insets 8, wrap 4", COL_SPEC));
        driveSection.add(label("Model:"));
        driveSection.add(modelValueLabel,    "growx");
        driveSection.add(label("Protocol:"));
        driveSection.add(protocolValueLabel, "growx");
        driveSection.add(label("Serial:"));
        driveSection.add(serialValueLabel,   "growx");
        driveSection.add(label("Firmware:"));
        driveSection.add(firmwareValueLabel, "growx");
        driveSection.add(label("Capacity:"));
        driveSection.add(capacityValueLabel, "growx, span 3");
        p.add(driveSection, "growx, wrap");

        // --- NVMe Device Details section ---
        // Shown with dash placeholders on startup; populated once SMART data arrives.
        // Hidden automatically when an ATA drive is detected.
        nvmeDevSection = section("NVMe Device Details");
        nvmeDevSection.setLayout(new MigLayout("insets 8, wrap 4", COL_SPEC));
        nvmeDevSection.add(label("NVMe Version:"));
        nvmeDevSection.add(nvmeVersionValueLabel,      "growx");
        nvmeDevSection.add(label("Controller ID:"));
        nvmeDevSection.add(nvmeControllerIdValueLabel, "growx");
        nvmeDevSection.add(label("PCI Vendor/Subsystem:"));
        nvmeDevSection.add(nvmeVendorValueLabel,       "growx");
        nvmeDevSection.add(label("IEEE OUI:"));
        nvmeDevSection.add(nvmeOuiValueLabel,          "growx");
        nvmeDevSection.add(label("Total NVM Capacity:"));
        nvmeDevSection.add(nvmeTotalCapValueLabel,     "growx");
        nvmeDevSection.add(label("Unallocated NVM:"));
        nvmeDevSection.add(nvmeUnallocCapValueLabel,   "growx");
        nvmeDevSection.add(label("Namespaces:"));
        nvmeDevSection.add(nvmeNsCountValueLabel,      "growx");
        nvmeDevSection.add(label("Local Time:"));
        nvmeDevSection.add(localTimeValueLabel,        "growx, span 3");
        nvmeDevSection.setVisible(true);   // visible with dashes before any SMART query
        p.add(nvmeDevSection, "growx, wrap");

        // --- Health section ---
        JPanel healthSection = section("Health");
        healthSection.setLayout(new MigLayout("insets 8, wrap 4", COL_SPEC));
        healthSection.add(label("SMART Status:"));
        healthSection.add(statusValueLabel,      "growx");
        healthSection.add(label("Temperature:"));
        healthSection.add(tempValueLabel,        "growx");
        healthSection.add(label("Power-On Hours:"));
        healthSection.add(powerOnValueLabel,     "growx");
        healthSection.add(label("Power Cycles:"));
        healthSection.add(powerCyclesValueLabel, "growx");
        p.add(healthSection, "growx, wrap");

        // --- NVMe Health Log section ---
        nvmeSection = section("NVMe Health Log (Log 0x02)");
        nvmeSection.setLayout(new MigLayout("insets 8, wrap 4", COL_SPEC));
        nvmeSection.add(label("Available Spare:"));
        nvmeSection.add(spareValueLabel,       "growx");
        nvmeSection.add(label("% Used (PE cycles):"));
        nvmeSection.add(usedPctValueLabel,     "growx");
        nvmeSection.add(label("Data Written:"));
        nvmeSection.add(writtenValueLabel,     "growx");
        nvmeSection.add(label("Data Read:"));
        nvmeSection.add(readValueLabel,        "growx");
        nvmeSection.add(label("Media Errors:"));
        nvmeSection.add(mediaErrValueLabel,    "growx");
        nvmeSection.add(label("Error Log Entries:"));
        nvmeSection.add(errLogValueLabel,      "growx");
        nvmeSection.add(label("Warn Temp Time:"));
        nvmeSection.add(warnTempValueLabel,    "growx");
        nvmeSection.add(label("Crit Comp Time:"));
        nvmeSection.add(critCompValueLabel,    "growx");
        nvmeSection.add(label("Temp Sensor 1:"));
        nvmeSection.add(tempSensor1ValueLabel, "growx");
        nvmeSection.add(label("Temp Sensor 2:"));
        nvmeSection.add(tempSensor2ValueLabel, "growx");
        nvmeSection.setVisible(true);
        p.add(nvmeSection, "growx, wrap");

        // --- ATA Attributes section (hidden until ATA data is present) ---
        ataSection = section("ATA SMART Attributes");
        ataSection.setLayout(new MigLayout("insets 8, fill", "[grow]", "[grow]"));
        JScrollPane ataScroll = new JScrollPane(ataTable);
        ataScroll.setPreferredSize(new java.awt.Dimension(600, 200));
        ataSection.add(ataScroll, "grow");
        ataSection.setVisible(false);
        p.add(ataSection, "growx, wrap");
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
            fillNvmeDevDetails(data);
            fillHealth(data);
            fillNvme(data.getNvmeHealthLog());
            fillAtaAttributes(data.getAtaSmartAttributes());
            revalidate();
            repaint();
        });
    }

    /** Resets all fields to dash placeholders and restores default visibility. */
    public void clear() {
        SwingUtilities.invokeLater(() -> {
            for (JLabel l : new JLabel[]{
                modelValueLabel, serialValueLabel, firmwareValueLabel,
                capacityValueLabel, protocolValueLabel,
                nvmeVersionValueLabel, nvmeControllerIdValueLabel,
                nvmeOuiValueLabel, nvmeVendorValueLabel,
                nvmeTotalCapValueLabel, nvmeUnallocCapValueLabel,
                nvmeNsCountValueLabel, localTimeValueLabel,
                statusValueLabel, tempValueLabel,
                powerOnValueLabel, powerCyclesValueLabel,
                spareValueLabel, usedPctValueLabel, writtenValueLabel,
                readValueLabel, mediaErrValueLabel, errLogValueLabel,
                warnTempValueLabel, critCompValueLabel,
                tempSensor1ValueLabel, tempSensor2ValueLabel
            }) {
                l.setText("-");
                l.setForeground(null);
            }
            ataModel.setRowCount(0);
            nvmeDevSection.setVisible(true);   // keep visible with dashes
            nvmeSection.setVisible(true);
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

    private void fillNvmeDevDetails(Smart d) {
        boolean hasNvmeInfo = d.getNvmeVersion() != null
                || d.getNvmeControllerId() != null
                || d.getNvmeTotalCapacity() != null
                || d.getNvmeNumberOfNamespaces() != null;

        // Hide for confirmed ATA drives (ATA attributes present, no NVMe info)
        boolean isAta = !hasNvmeInfo
                && d.getAtaSmartAttributes() != null
                && d.getAtaSmartAttributes().getTable() != null
                && !d.getAtaSmartAttributes().getTable().isEmpty();

        if (isAta) {
            nvmeDevSection.setVisible(false);
            return;
        }

        nvmeDevSection.setVisible(true);

        if (d.getNvmeVersion() != null && d.getNvmeVersion().getString() != null) {
            nvmeVersionValueLabel.setText(d.getNvmeVersion().getString());
        }
        if (d.getNvmeControllerId() != null) {
            nvmeControllerIdValueLabel.setText(String.valueOf(d.getNvmeControllerId()));
        }
        if (d.getNvmePciVendor() != null) {
            String s = d.getNvmePciVendor().getDisplayString();
            nvmeVendorValueLabel.setText(s != null ? s : "-");
        }
        if (d.getNvmeIeeeOuiIdentifier() != null) {
            nvmeOuiValueLabel.setText(
                "0x" + Long.toHexString(d.getNvmeIeeeOuiIdentifier()).toUpperCase());
        }
        if (d.getNvmeTotalCapacity() != null) {
            double gb = Math.round(d.getNvmeTotalCapacity() / 1_000_000_000.0 * 100.0) / 100.0;
            nvmeTotalCapValueLabel.setText(gb + " GB");
        }
        if (d.getNvmeUnallocatedCapacity() != null) {
            double gb = Math.round(d.getNvmeUnallocatedCapacity() / 1_000_000_000.0 * 100.0) / 100.0;
            nvmeUnallocCapValueLabel.setText(gb + " GB");
        }
        if (d.getNvmeNumberOfNamespaces() != null) {
            nvmeNsCountValueLabel.setText(String.valueOf(d.getNvmeNumberOfNamespaces()));
        }
        if (d.getLocalTime() != null && d.getLocalTime().getAsctime() != null) {
            localTimeValueLabel.setText(d.getLocalTime().getAsctime());
        }
    }

    private void fillHealth(Smart d) {
        if (d.getSmartStatus() != null) {
            boolean passed = Boolean.TRUE.equals(d.getSmartStatus().isPassed());
            statusValueLabel.setText(passed ? "PASSED ✔" : "FAILED ✘");
            statusValueLabel.setForeground(passed ? new Color(0x4CAF50) : new Color(0xF44336));
        }

        // Temperature — prefer top-level block, fall back to NVMe health log
        if (d.getTemperature() != null && d.getTemperature().getCurrent() != null) {
            int t = d.getTemperature().getCurrent();
            tempValueLabel.setText(t + " °C");
            tempValueLabel.setForeground(tempColor(t));
        } else if (d.getNvmeHealthLog() != null && d.getNvmeHealthLog().getTemperature() != null) {
            int t = d.getNvmeHealthLog().getTemperature();
            tempValueLabel.setText(t + " °C");
            tempValueLabel.setForeground(tempColor(t));
        }

        // Power-on hours — prefer top-level, fall back to NVMe health log
        if (d.getPowerOnTime() != null && d.getPowerOnTime().getHours() != null) {
            powerOnValueLabel.setText(d.getPowerOnTime().getHours() + " h");
        } else if (d.getNvmeHealthLog() != null && d.getNvmeHealthLog().getPowerOnHours() != null) {
            powerOnValueLabel.setText(d.getNvmeHealthLog().getPowerOnHours() + " h");
        }

        // Power cycles — prefer top-level, fall back to NVMe health log
        if (d.getPowerCycleCount() != null) {
            powerCyclesValueLabel.setText(String.valueOf(d.getPowerCycleCount()));
        } else if (d.getNvmeHealthLog() != null && d.getNvmeHealthLog().getPowerCycles() != null) {
            powerCyclesValueLabel.setText(String.valueOf(d.getNvmeHealthLog().getPowerCycles()));
        }
    }

    private void fillNvme(Smart.NvmeHealthLog nvme) {
        if (nvme == null) {
            nvmeSection.setVisible(false);
            return;
        }
        nvmeSection.setVisible(true);

        setNvmeField(spareValueLabel, nvme.getAvailableSpare(), "%",
                nvme.getAvailableSpareThreshold(), true);
        setNvmeField(usedPctValueLabel, nvme.getPercentageUsed(), "%", null, false);

        writtenValueLabel.setText(nvme.getDataWrittenGb()
                + " GB  (" + nvme.getDataUnitsWritten() + " units)");
        readValueLabel.setText(nvme.getDataReadGb()
                + " GB  (" + nvme.getDataUnitsRead() + " units)");

        setCountField(mediaErrValueLabel, nvme.getMediaErrors());
        setCountField(errLogValueLabel,   nvme.getNumErrLogEntries());

        warnTempValueLabel.setText(nvme.getWarningTempTime() != null
                ? nvme.getWarningTempTime() + " min" : "-");
        critCompValueLabel.setText(nvme.getCriticalCompTime() != null
                ? nvme.getCriticalCompTime() + " min" : "-");

        setTempSensorField(tempSensor1ValueLabel, nvme.getTemperatureSensor1());
        setTempSensorField(tempSensor2ValueLabel, nvme.getTemperatureSensor2());

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
                attr.getId(), orDash(attr.getName()),
                attr.getValue(), attr.getWorst(), attr.getThresh(),
                raw, status
            });
        }
    }

    // -------------------------------------------------------------------------
    // Small helpers
    // -------------------------------------------------------------------------

    private static Color tempColor(int tempC) {
        if (tempC >= 60) return new Color(0xF44336);
        if (tempC >= 45) return new Color(0xFF9800);
        return new Color(0x4CAF50);
    }

    private void setTempSensorField(JLabel lbl, Integer tempC) {
        if (tempC == null) { lbl.setText("-"); return; }
        lbl.setText(tempC + " °C");
        lbl.setForeground(tempColor(tempC));
    }

    private void setNvmeField(JLabel lbl, Integer value, String suffix,
                              Integer threshold, boolean higherIsBetter) {
        if (value == null) { lbl.setText("-"); return; }
        lbl.setText(value + suffix);
        if (threshold != null) {
            boolean warn = higherIsBetter ? value <= threshold : value >= threshold;
            lbl.setForeground(warn ? new Color(0xF44336) : new Color(0x4CAF50));
        }
    }

    private void setCountField(JLabel lbl, Long value) {
        if (value == null) { lbl.setText("-"); return; }
        lbl.setText(String.valueOf(value));
        lbl.setForeground(value > 0 ? new Color(0xF44336) : null);
    }

    private static String orDash(String s) {
        return (s != null && !s.isBlank()) ? s : "-";
    }

    private static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        return l;
    }

    private static JLabel value(String text) { return new JLabel(text); }

    private static JPanel section(String title) {
        JPanel p = new JPanel();
        p.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), title));
        return p;
    }
}
