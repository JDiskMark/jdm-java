package jdiskmark;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.Scrollable;
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
    private final JLabel statusValueLabel         = value("-");
    private final JLabel tempValueLabel           = value("-");
    private final JLabel powerOnValueLabel        = value("-");
    private final JLabel powerCyclesValueLabel    = value("-");
    private final JLabel remainingLifeValueLabel  = value("-");

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
    // Drive Endurance labels
    // -------------------------------------------------------------------------
    private final JLabel wearLevelingValueLabel  = value("-");
    private final JLabel badBlockValueLabel      = value("-");
    private final JLabel programFailValueLabel   = value("-");
    private final JLabel eraseFailValueLabel     = value("-");
    private final JLabel eccErrorValueLabel      = value("-");
    private final JLabel uncorrErrorValueLabel   = value("-");
    private       JPanel enduranceSection;

    // -------------------------------------------------------------------------
    // Toolbar controls
    // -------------------------------------------------------------------------
    private JButton runButton;
    private JButton saveButton;
    private JLabel  statusLabel;

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

        // Inner content panel — implements Scrollable so the scroll pane uses
        // the panel's natural preferred height instead of stretching to fill the viewport.
        ContentPanel contentPanel = new ContentPanel();
        contentPanel.setLayout(new MigLayout("insets 12, fillx", "[grow]", "[]8[]8[]8[]8[]"));

        buildLayout(contentPanel);

        // Wrap in a scroll pane so the tab is always scrollable
        JScrollPane scroller = new JScrollPane(contentPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroller.setBorder(null);
        scroller.getVerticalScrollBar().setUnitIncrement(16);
        add(scroller, BorderLayout.CENTER);

        // Toolbar with Run and Save buttons (NORTH — above scroll pane)
        add(buildToolbar(), BorderLayout.NORTH);
    }

    // -------------------------------------------------------------------------
    // Toolbar
    // -------------------------------------------------------------------------

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new MigLayout("insets 8 12 8 12", "[][][grow]", "[]"));
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(80, 80, 80)));

        runButton  = new JButton("Run SMART");
        saveButton = new JButton("Save Snapshot");
        saveButton.setEnabled(false);

        statusLabel = new JLabel("Click \u2018Run SMART\u2019 to fetch live data.");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC));

        runButton.addActionListener(e -> Gui.runSmart());
        saveButton.addActionListener(e -> Gui.saveCurrentSmartData());

        bar.add(runButton);
        bar.add(saveButton);
        bar.add(statusLabel, "growx");
        return bar;
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
        healthSection.add(statusValueLabel,        "growx");
        healthSection.add(label("Temperature:"));
        healthSection.add(tempValueLabel,          "growx");
        healthSection.add(label("Power-On Hours:"));
        healthSection.add(powerOnValueLabel,       "growx");
        healthSection.add(label("Power Cycles:"));
        healthSection.add(powerCyclesValueLabel,   "growx");
        healthSection.add(label("Remaining Life:"));
        healthSection.add(remainingLifeValueLabel, "growx, span 3");
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

        // --- Drive Endurance section ---
        enduranceSection = section("Drive Endurance");
        enduranceSection.setLayout(new MigLayout("insets 8, wrap 4", COL_SPEC));
        enduranceSection.add(label("Wear Leveling Count:"));
        enduranceSection.add(wearLevelingValueLabel, "growx");
        enduranceSection.add(label("Bad Block Count:"));
        enduranceSection.add(badBlockValueLabel,     "growx");
        enduranceSection.add(label("Program Fail Count:"));
        enduranceSection.add(programFailValueLabel,  "growx");
        enduranceSection.add(label("Erase Fail Count:"));
        enduranceSection.add(eraseFailValueLabel,    "growx");
        enduranceSection.add(label("ECC Error Rate:"));
        enduranceSection.add(eccErrorValueLabel,     "growx");
        enduranceSection.add(label("Uncorrectable Errors:"));
        enduranceSection.add(uncorrErrorValueLabel,  "growx");
        enduranceSection.setVisible(false);
        p.add(enduranceSection, "growx, wrap");

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
            fillEndurance(data);
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
                remainingLifeValueLabel,
                spareValueLabel, usedPctValueLabel, writtenValueLabel,
                readValueLabel, mediaErrValueLabel, errLogValueLabel,
                warnTempValueLabel, critCompValueLabel,
                tempSensor1ValueLabel, tempSensor2ValueLabel,
                wearLevelingValueLabel, badBlockValueLabel,
                programFailValueLabel, eraseFailValueLabel,
                eccErrorValueLabel, uncorrErrorValueLabel
            }) {
                l.setText("-");
                l.setForeground(null);
            }
            ataModel.setRowCount(0);
            nvmeDevSection.setVisible(true);   // keep visible with dashes
            nvmeSection.setVisible(true);
            enduranceSection.setVisible(false);
            ataSection.setVisible(false);
            // Reset toolbar state
            saveButton.setEnabled(false);
            statusLabel.setText("Click \u2018Run SMART\u2019 to fetch live data.");
            revalidate();
            repaint();
        });
    }

    /**
     * Called after SMART data has been successfully loaded.
     * Enables the Save Snapshot button and updates the status label.
     *
     * @param deviceName the device name that was queried (e.g. {@code nvme0n1})
     */
    public void onDataLoaded(String deviceName) {
        SwingUtilities.invokeLater(() -> {
            saveButton.setEnabled(true);
            statusLabel.setText("Data loaded for /dev/" + deviceName
                    + ". Click \u2018Save Snapshot\u2019 to store.");
        });
    }

    /**
     * Called after a snapshot has been successfully saved to the database.
     * Disables the Save button so the same data cannot be saved twice —
     * the button is only re-enabled by {@link #onDataLoaded(String)} when a
     * fresh {@code Run SMART} query completes.
     */
    public void onDataSaved() {
        SwingUtilities.invokeLater(() -> {
            saveButton.setEnabled(false);
            statusLabel.setText("Snapshot saved \u2714 \u2014 click \u2018Run SMART\u2019 to fetch new data.");
        });
    }

    /**
     * Called after the SMART tab has been populated from a stored
     * {@link SmartSnapshot} (via the SMART Reports table).
     *
     * <p>Disables the Save button (this is a read-only historical view) and
     * updates the status label with the snapshot's capture timestamp.
     * Safe to call from any thread.
     *
     * @param snap the snapshot that is currently being displayed
     */
    public void onSnapshotLoaded(SmartSnapshot snap) {
        SwingUtilities.invokeLater(() -> {
            saveButton.setEnabled(false);
            String ts = snap.getCapturedAt() != null
                    ? snap.getCapturedAt().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    : "unknown";
            statusLabel.setText("Viewing stored snapshot from " + ts
                    + " \u2014 click \u2018Run SMART\u2019 to fetch live data.");
        });
    }

    /**
     * Populates the panel from a stored {@link SmartSnapshot}.
     * Only the fields that are persisted in the DB are shown; sections that
     * require a live {@code smartctl} query (NVMe device details, ATA
     * attributes, endurance) are hidden.
     *
     * <p>Safe to call from any thread.
     *
     * @param snap the snapshot to display (must not be null)
     */
    public void populateFromSnapshot(SmartSnapshot snap) {
        SwingUtilities.invokeLater(() -> {
            // Reset all value labels to dash
            for (JLabel l : new JLabel[]{
                modelValueLabel, serialValueLabel, firmwareValueLabel,
                capacityValueLabel, protocolValueLabel,
                nvmeVersionValueLabel, nvmeControllerIdValueLabel,
                nvmeOuiValueLabel, nvmeVendorValueLabel,
                nvmeTotalCapValueLabel, nvmeUnallocCapValueLabel,
                nvmeNsCountValueLabel, localTimeValueLabel,
                statusValueLabel, tempValueLabel,
                powerOnValueLabel, powerCyclesValueLabel,
                remainingLifeValueLabel,
                spareValueLabel, usedPctValueLabel, writtenValueLabel,
                readValueLabel, mediaErrValueLabel, errLogValueLabel,
                warnTempValueLabel, critCompValueLabel,
                tempSensor1ValueLabel, tempSensor2ValueLabel,
                wearLevelingValueLabel, badBlockValueLabel,
                programFailValueLabel, eraseFailValueLabel,
                eccErrorValueLabel, uncorrErrorValueLabel
            }) { l.setText("-"); l.setForeground(null); }
            ataModel.setRowCount(0);

            // Drive Info
            modelValueLabel.setText(orDash(snap.getModelName()));
            serialValueLabel.setText(orDash(snap.getSerialNumber()));
            firmwareValueLabel.setText(orDash(snap.getFirmwareVersion()));
            protocolValueLabel.setText(orDash(snap.getProtocol()));
            if (snap.getCapacityGb() != null) {
                capacityValueLabel.setText(snap.getCapacityGb() + " GB");
            }

            // Health
            if (snap.getSmartPassed() != null) {
                boolean passed = Boolean.TRUE.equals(snap.getSmartPassed());
                statusValueLabel.setText(passed ? "PASSED \u2714" : "FAILED \u2718");
                statusValueLabel.setForeground(passed ? new Color(0x4CAF50) : new Color(0xF44336));
            }
            if (snap.getTempC() != null) {
                int t = snap.getTempC();
                tempValueLabel.setText(t + " \u00b0C");
                tempValueLabel.setForeground(tempColor(t));
            }
            if (snap.getPowerOnHours() != null) {
                powerOnValueLabel.setText(snap.getPowerOnHours() + " h");
            }
            if (snap.getPowerCycles() != null) {
                powerCyclesValueLabel.setText(String.valueOf(snap.getPowerCycles()));
            }
            if (snap.getPercentageUsed() != null) {
                int remaining = Math.max(0, 100 - snap.getPercentageUsed());
                remainingLifeValueLabel.setText(remaining + "%");
                if (remaining > 50)      remainingLifeValueLabel.setForeground(new Color(0x4CAF50));
                else if (remaining > 20) remainingLifeValueLabel.setForeground(new Color(0xFF9800));
                else                     remainingLifeValueLabel.setForeground(new Color(0xF44336));
            }

            // NVMe health log — show only if we have any stored NVMe field
            boolean hasNvme = snap.getAvailableSpare() != null
                    || snap.getPercentageUsed() != null
                    || snap.getMediaErrors() != null
                    || snap.getDataWrittenGb() != null;
            nvmeSection.setVisible(hasNvme);
            if (hasNvme) {
                if (snap.getAvailableSpare() != null)
                    spareValueLabel.setText(snap.getAvailableSpare() + "%");
                if (snap.getPercentageUsed() != null)
                    usedPctValueLabel.setText(snap.getPercentageUsed() + "%");
                if (snap.getDataWrittenGb() != null)
                    writtenValueLabel.setText(snap.getDataWrittenGb() + " GB");
                if (snap.getDataReadGb() != null)
                    readValueLabel.setText(snap.getDataReadGb() + " GB");
                if (snap.getMediaErrors() != null) {
                    long me = snap.getMediaErrors();
                    mediaErrValueLabel.setText(String.valueOf(me));
                    mediaErrValueLabel.setForeground(me > 0 ? new Color(0xF44336) : null);
                }
            }

            // Hide sections not stored in snapshots
            nvmeDevSection.setVisible(false);
            enduranceSection.setVisible(false);
            ataSection.setVisible(false);

            // Toolbar: disable Save (this is a read-only view), show snapshot timestamp
            saveButton.setEnabled(false);
            String ts = snap.getCapturedAt() != null
                    ? snap.getCapturedAt().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    : "unknown";
            statusLabel.setText("Viewing stored snapshot from " + ts
                    + " \u2014 click \u2018Run SMART\u2019 to fetch live data.");

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

        // Remaining Life — derived from NVMe percentage_used (100 - used)
        if (d.getNvmeHealthLog() != null && d.getNvmeHealthLog().getPercentageUsed() != null) {
            int used      = d.getNvmeHealthLog().getPercentageUsed();
            int remaining = Math.max(0, 100 - used);
            remainingLifeValueLabel.setText(remaining + "%");
            if (remaining > 50)       remainingLifeValueLabel.setForeground(new Color(0x4CAF50)); // green
            else if (remaining > 20)  remainingLifeValueLabel.setForeground(new Color(0xFF9800)); // orange
            else                      remainingLifeValueLabel.setForeground(new Color(0xF44336)); // red
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

         if (nvme.getDataUnitsWritten() != null) {
             writtenValueLabel.setText(nvme.getDataWrittenGb()
                     + " GB  (" + nvme.getDataUnitsWritten() + " units)");
         } else {
             writtenValueLabel.setText("-");
         }
         if (nvme.getDataUnitsRead() != null) {
             readValueLabel.setText(nvme.getDataReadGb()
                     + " GB  (" + nvme.getDataUnitsRead() + " units)");
         } else {
             readValueLabel.setText("-");
         }

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

    private void fillEndurance(Smart d) {
        Smart.AtaSmartAttributes ata = d.getAtaSmartAttributes();
        Smart.NvmeHealthLog nvme     = d.getNvmeHealthLog();

        boolean hasAta  = ata != null && ata.getTable() != null && !ata.getTable().isEmpty();
        boolean hasNvme = nvme != null;

        if (!hasAta && !hasNvme) { enduranceSection.setVisible(false); return; }
        enduranceSection.setVisible(true);

        if (hasAta) {
            // Wear Leveling Count   — attr 177 (0xB1) or 231 (0xE7)
            setAtaAttrField(wearLevelingValueLabel, ata.findByIdAny(177, 231));
            // Bad Block / Reallocated Sectors — attr 5 (0x05), 181 (0xB5),
            //   197 (0xC5 Current Pending), 198 (0xC6 Uncorrectable)
            setAtaAttrField(badBlockValueLabel,     ata.findByIdAny(5, 181, 197, 198));
            // Program Fail Count    — attr 181 (0xB5)
            setAtaAttrField(programFailValueLabel,  ata.findById(181));
            // Erase Fail Count      — attr 182 (0xB6)
            setAtaAttrField(eraseFailValueLabel,    ata.findById(182));
            // Raw Read Error Rate   — attr 1  (0x01)
            setAtaAttrField(eccErrorValueLabel,     ata.findById(1));
            // Uncorrectable Errors  — attr 187 (0xBB)
            setAtaAttrField(uncorrErrorValueLabel,  ata.findById(187));
        } else {
            // NVMe: map to nearest equivalent health-log fields
            // Wear Leveling → percentage_used (endurance consumed)
            if (nvme.getPercentageUsed() != null) {
                wearLevelingValueLabel.setText(nvme.getPercentageUsed() + "% used");
                wearLevelingValueLabel.setForeground(
                    nvme.getPercentageUsed() < 80 ? new Color(0x4CAF50) : new Color(0xF44336));
            }
            // Bad Block Count → media_errors
            if (nvme.getMediaErrors() != null) {
                long me = nvme.getMediaErrors();
                badBlockValueLabel.setText(String.valueOf(me));
                badBlockValueLabel.setForeground(me > 0 ? new Color(0xF44336) : null);
            }
            // Remaining fields not present in standard NVMe health log
            programFailValueLabel.setText("N/A");
            eraseFailValueLabel.setText("N/A");
            eccErrorValueLabel.setText("N/A");
            uncorrErrorValueLabel.setText("N/A");
        }
    }

    /** Populates {@code lbl} from an ATA attribute's raw string, or "N/A" if absent. */
    private void setAtaAttrField(JLabel lbl, Smart.AtaAttribute attr) {
        if (attr == null) { lbl.setText("N/A"); return; }
        String raw = (attr.getRaw() != null && attr.getRaw().getString() != null)
                ? attr.getRaw().getString()
                : (attr.getValue() != null ? String.valueOf(attr.getValue()) : "-");
        lbl.setText(raw);
        if (attr.isFailing()) lbl.setForeground(new Color(0xF44336));
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
        if (value == null) {
            lbl.setText("-");
            lbl.setForeground(null);
            return;
        }
        lbl.setText(value + suffix);
        if (threshold != null) {
            boolean warn = higherIsBetter ? value <= threshold : value >= threshold;
            lbl.setForeground(warn ? new Color(0xF44336) : new Color(0x4CAF50));
        } else {
            lbl.setForeground(null);
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

    // -------------------------------------------------------------------------
    // Scrollable content panel
    // -------------------------------------------------------------------------

    /**
     * A JPanel that implements {@link Scrollable} to prevent the enclosing
     * {@link JScrollPane} from stretching it to fill the viewport height.
     * The panel will only be as tall as its content, eliminating blank space
     * below the last section.
     */
    private static class ContentPanel extends JPanel implements Scrollable {
        ContentPanel() { super(); }

        @Override
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 16; }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return visibleRect.height;
        }

        /** Fill the viewport width so content stretches horizontally. */
        @Override
        public boolean getScrollableTracksViewportWidth() { return true; }

        /** Do NOT fill viewport height — use natural content height to avoid blank space. */
        @Override
        public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
