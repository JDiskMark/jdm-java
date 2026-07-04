package jdiskmark;

import javax.swing.*;
import java.awt.*;

/**
 * Sharing tab panel (issue #117).
 *
 * Layout:
 *
 *  ┌──────────────────────────┬───────────────────────────────────────────────────────────────────────┐
 *  │ ☑ Share benchmark        │ Endpoint — https://test.jdiskmark.net:5000/api/benchmarks/upload      │
 *  │   results with the       │  ○ Production  ○ Test (test.jdiskmark.net)  ○ Localhost               │
 *  │   JDiskMark community    │  Protocol:  ○ HTTPS  ○ HTTP                                           │
 *  │   portal                 │                                                                       │
 *  │                          │                                                                       │
 *  │   ● Enabled              │                                                                       │
 *  └──────────────────────────┴───────────────────────────────────────────────────────────────────────┘
 *
 * Radios are laid out horizontally to keep the vertical footprint minimal — no
 * scroll bar is triggered at the default window height.
 *
 * The titled border on the right panel doubles as a URL preview; it is updated
 * live whenever the endpoint or protocol changes.
 *
 * Developer controls (endpoint / protocol) will be hidden in a future
 * production build; they remain here for testing.
 */
public class SharingPanel extends JPanel {

    // ── Controls ──────────────────────────────────────────────────────────────
    private final JCheckBox  enableCheckBox;
    private final JLabel     statusLabel;

    private final JRadioButton localRb;
    private final JRadioButton testRb;
    private final JRadioButton prodRb;

    private final JRadioButton httpRb;
    private final JRadioButton httpsRb;

    /** The right dev-controls panel whose titled border shows the live URL. */
    private final JPanel devPanel;


    public SharingPanel() {
        setLayout(new BorderLayout());

        // ── Outer split: left (toggle) | right (dev controls) ─────────────────
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setBorder(BorderFactory.createEmptyBorder(8, 6, 8, 6));

        // ── LEFT ──────────────────────────────────────────────────────────────
        JPanel leftPanel = new JPanel(new GridBagLayout());
        leftPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 8));

        enableCheckBox = new JCheckBox(
                "<html><b>Share benchmark results with<br>the JDiskMark community portal</b></html>");

        statusLabel = new JLabel("○ Disabled");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        statusLabel.setForeground(Color.GRAY);

        GridBagConstraints lc = new GridBagConstraints();
        lc.gridx = 0; lc.fill = GridBagConstraints.HORIZONTAL; lc.weightx = 1;
        lc.anchor = GridBagConstraints.NORTHWEST; lc.insets = new Insets(0, 0, 4, 0);

        lc.gridy = 0; leftPanel.add(enableCheckBox, lc);
        lc.gridy = 1; lc.insets = new Insets(0, 4, 0, 0);
        leftPanel.add(statusLabel, lc);
        lc.gridy = 2; lc.weighty = 1; lc.fill = GridBagConstraints.BOTH;
        leftPanel.add(Box.createVerticalGlue(), lc);

        // ── RIGHT: developer controls ─────────────────────────────────────────
        // Endpoint radios
        ButtonGroup endpointGroup = new ButtonGroup();
        prodRb  = new JRadioButton("Production (www.jdiskmark.net)");
        testRb  = new JRadioButton("Test (test.jdiskmark.net)");
        localRb = new JRadioButton("Localhost");
        endpointGroup.add(prodRb);
        endpointGroup.add(testRb);
        endpointGroup.add(localRb);

        // Protocol radios
        ButtonGroup protocolGroup = new ButtonGroup();
        httpsRb = new JRadioButton("HTTPS");
        httpRb  = new JRadioButton("HTTP");
        protocolGroup.add(httpsRb);
        protocolGroup.add(httpRb);

        // Endpoint row
        JPanel endpointRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        endpointRow.add(prodRb);
        endpointRow.add(testRb);
        endpointRow.add(localRb);

        // Protocol row
        JPanel protocolRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        protocolRow.add(new JLabel("Protocol:"));
        protocolRow.add(httpsRb);
        protocolRow.add(httpRb);

        // Assemble with GridBagLayout so rows stretch horizontally
        devPanel = new JPanel(new GridBagLayout());
        // border title is set in refreshBorderTitle()
        GridBagConstraints dc = new GridBagConstraints();
        dc.gridx = 0; dc.fill = GridBagConstraints.HORIZONTAL; dc.weightx = 1;
        dc.anchor = GridBagConstraints.NORTHWEST; dc.insets = new Insets(0, 0, 2, 0);

        dc.gridy = 0; devPanel.add(endpointRow, dc);
        dc.gridy = 1; devPanel.add(protocolRow, dc);
        dc.gridy = 2; dc.weighty = 1; dc.fill = GridBagConstraints.BOTH;
        devPanel.add(Box.createVerticalGlue(), dc);

        // ── Outer grid assembly ────────────────────────────────────────────────
        GridBagConstraints oc = new GridBagConstraints();
        oc.gridy = 0; oc.fill = GridBagConstraints.BOTH; oc.weighty = 1;
        oc.anchor = GridBagConstraints.NORTHWEST;

        oc.gridx = 0; oc.weightx = 0.35; oc.insets = new Insets(0, 0, 0, 4);
        grid.add(leftPanel, oc);

        oc.gridx = 1; oc.weightx = 0.65; oc.insets = new Insets(0, 0, 0, 0);
        grid.add(devPanel, oc);

        // ── Scroll pane: vertical only ─────────────────────────────────────────
        JPanel northWrapper = new JPanel(new BorderLayout());
        northWrapper.add(grid, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(northWrapper,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);

        // ── Listeners ──────────────────────────────────────────────────────────
        enableCheckBox.addActionListener(e -> onToggleSharing());
        prodRb .addActionListener(e -> onEndpointChanged(Portal.PRODUCTION_UPLOAD_LOCATOR));
        testRb .addActionListener(e -> onEndpointChanged(Portal.TEST_UPLOAD_LOCATOR));
        localRb.addActionListener(e -> onEndpointChanged(Portal.LOCAL_UPLOAD_LOCATOR));
        httpsRb.addActionListener(e -> onProtocolChanged(Portal.HTTPS));
        httpRb .addActionListener(e -> onProtocolChanged(Portal.HTTP));

        refresh();
    }

    // ── Event handlers ─────────────────────────────────────────────────────────

    private void onToggleSharing() {
        App.sharePortal = enableCheckBox.isSelected();
        App.saveConfig();
        App.msg(App.sharePortal ? "Portal upload enabled." : "Portal upload disabled.");
        refresh();
    }

    private void onEndpointChanged(String locator) {
        Portal.uploadResourceLocator = locator;
        App.saveConfig();
        refreshBorderTitle();
    }

    private void onProtocolChanged(String protocol) {
        Portal.uploadProtocol = protocol;
        App.saveConfig();
        refreshBorderTitle();
    }

    // ── Sync ───────────────────────────────────────────────────────────────────

    /** Synchronises all controls to the current {@link App} / {@link Portal} state. */
    public void refresh() {
        enableCheckBox.setSelected(App.sharePortal);

        if (App.sharePortal) {
            statusLabel.setText("● Enabled");
            statusLabel.setForeground(new Color(0, 150, 60));
        } else {
            statusLabel.setText("○ Disabled");
            statusLabel.setForeground(Color.GRAY);
        }

        // Endpoint
        if (Portal.uploadResourceLocator.equalsIgnoreCase(Portal.LOCAL_UPLOAD_LOCATOR)) {
            localRb.setSelected(true);
        } else if (Portal.uploadResourceLocator.equalsIgnoreCase(Portal.TEST_UPLOAD_LOCATOR)) {
            testRb.setSelected(true);
        } else {
            prodRb.setSelected(true);
        }

        // Protocol
        if (Portal.uploadProtocol.equalsIgnoreCase(Portal.HTTPS)) {
            httpsRb.setSelected(true);
        } else {
            httpRb.setSelected(true);
        }

        refreshBorderTitle();
    }

    /**
     * Updates the titled border of the developer panel to reflect the currently
     * constructed upload URL.  Called whenever endpoint or protocol changes.
     */
    private void refreshBorderTitle() {
        String title = "Endpoint \u2014 " + Portal.getUploadUrl();
        devPanel.setBorder(BorderFactory.createTitledBorder(title));
        devPanel.repaint();
    }
}
