package jdiskmark;

import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import net.miginfocom.swing.MigLayout;

/**
 * "Advanced Options" dialog — exposes settings that are too technical for the
 * main Options menu but still need a UI surface:
 *
 * <ul>
 *   <li>Render Frequency Mode</li>
 *   <li>IO Engine selection</li>
 *   <li>GC Hint Optimizing</li>
 *   <li>GC Sample Retries</li>
 *   <li>Auto Delete Test Files</li>
 *   <li>Auto Reset</li>
 * </ul>
 *
 * Access via the lazy-init singleton {@link Gui#getAdvancedFrame()} rather than
 * constructing directly. Hand-laid out (not NetBeans form-managed) using MigLayout
 * so that additional controls can be added freely.
 *
 * @author james, vpstackhub
 */
public class AdvancedOptionsFrame extends javax.swing.JFrame {

    // ---- controls --------------------------------------------------------
    private JComboBox<RenderFrequencyMode> renderModeCombo;
    private JRadioButton ioModernRadio;
    private JRadioButton ioLegacyRadio;
    private JCheckBox gcHintsCheckBox;
    private JCheckBox gcRetryCheckBox;
    private JCheckBox autoDeleteCheckBox;
    private JCheckBox autoResetCheckBox;
    private JButton closeButton;

    /**
     * Builds the form and populates all controls from the current App / GcDetector state.
     */
    public AdvancedOptionsFrame() {
        setTitle("Advanced Options");
        setDefaultCloseOperation(javax.swing.WindowConstants.HIDE_ON_CLOSE);
        setResizable(false);
        buildUI();
        syncFromModel();
        pack();
        setLocationRelativeTo(Gui.mainFrame);
    }

    // -----------------------------------------------------------------------
    // UI construction
    // -----------------------------------------------------------------------

    private void buildUI() {
        JPanel content = new JPanel(new MigLayout(
                "insets 12 16 8 16, wrap 2",  // 2-column auto-wrap
                "[right][grow]"               // label right-aligned, control stretches
        ));

        // ---- Render Mode --------------------------------------------------
        renderModeCombo = new JComboBox<>(
                new DefaultComboBoxModel<>(RenderFrequencyMode.values()));
        renderModeCombo.addActionListener(e -> {
            Object sel = renderModeCombo.getSelectedItem();
            if (sel instanceof RenderFrequencyMode mode) {
                App.rmOption = mode;
                App.saveConfig();
                Gui.refreshChartBadges();
            }
        });
        content.add(new JLabel("Render Mode:"));
        content.add(renderModeCombo, "growx");

        // ---- separator ----------------------------------------------------
        content.add(new JSeparator(), "span 2, growx, gapy 4 4");

        // ---- IO Engine (radio group) --------------------------------------
        ButtonGroup ioGroup = new ButtonGroup();
        ioModernRadio = new JRadioButton("Modern (FFM API)");
        ioLegacyRadio = new JRadioButton("Legacy (RandomAccessFile)");
        ioGroup.add(ioModernRadio);
        ioGroup.add(ioLegacyRadio);

        ioModernRadio.addActionListener(e -> applyIoEngine(App.IoEngine.MODERN));
        ioLegacyRadio.addActionListener(e -> applyIoEngine(App.IoEngine.LEGACY));

        content.add(new JLabel("IO Engine:"));
        content.add(ioModernRadio);
        content.add(ioLegacyRadio, "skip 1, gapy 0 4");

        // ---- separator ----------------------------------------------------
        content.add(new JSeparator(), "span 2, growx, gapy 4 4");

        // ---- GC options ---------------------------------------------------
        gcHintsCheckBox = new JCheckBox("GC Hint Optimizing");
        gcHintsCheckBox.addActionListener(e -> {
            GcDetector.gcHintsEnabled = gcHintsCheckBox.isSelected();
        });

        gcRetryCheckBox = new JCheckBox("GC Sample Retries");
        gcRetryCheckBox.addActionListener(e -> {
            GcDetector.gcRetryEnabled = gcRetryCheckBox.isSelected();
        });

        content.add(new JLabel("JVM / Sampling:"));
        content.add(gcHintsCheckBox);
        content.add(gcRetryCheckBox, "skip 1, gapy 0 4");

        // ---- separator ----------------------------------------------------
        content.add(new JSeparator(), "span 2, growx, gapy 4 4");

        // ---- Benchmark lifecycle ------------------------------------------
        autoDeleteCheckBox = new JCheckBox("Auto Delete Test Files");
        autoDeleteCheckBox.addActionListener(e -> {
            App.autoRemoveData = autoDeleteCheckBox.isSelected();
            App.saveConfig();
        });

        autoResetCheckBox = new JCheckBox("Auto Reset");
        autoResetCheckBox.addActionListener(e -> {
            App.autoReset = autoResetCheckBox.isSelected();
            App.saveConfig();
        });

        content.add(new JLabel("Benchmark:"));
        content.add(autoDeleteCheckBox);
        content.add(autoResetCheckBox, "skip 1, gapy 0 4");

        // ---- Close button -------------------------------------------------
        closeButton = new JButton("Close");
        closeButton.addActionListener(e -> setVisible(false));

        JPanel btnPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0));
        btnPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 4, 0));
        btnPanel.add(closeButton);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(content, BorderLayout.CENTER);
        getContentPane().add(btnPanel, BorderLayout.SOUTH);
    }

    // -----------------------------------------------------------------------
    // Model sync
    // -----------------------------------------------------------------------

    /**
     * Populates all controls from the current runtime state.
     * Called at construction and can be called again if settings change externally.
     */
    public void syncFromModel() {
        renderModeCombo.setSelectedItem(App.rmOption);

        switch (App.ioEngine) {
            case MODERN -> ioModernRadio.setSelected(true);
            case LEGACY -> ioLegacyRadio.setSelected(true);
        }

        gcHintsCheckBox.setSelected(GcDetector.gcHintsEnabled);
        gcRetryCheckBox.setSelected(GcDetector.gcRetryEnabled);
        autoDeleteCheckBox.setSelected(App.autoRemoveData);
        autoResetCheckBox.setSelected(App.autoReset);
    }

    // -----------------------------------------------------------------------
    // Business logic
    // -----------------------------------------------------------------------

    /**
     * Applies an IO engine change and updates the main Options menu items
     * that depend on the selected engine (Direct IO and Sector Alignment
     * are only available in Modern mode).
     */
    private void applyIoEngine(App.IoEngine engine) {
        App.ioEngine = engine;
        App.saveConfig();
        // delegate back to MainFrame so it can update its own menu item states
        if (Gui.mainFrame != null) {
            Gui.mainFrame.refreshConfig();
        }
    }

}
