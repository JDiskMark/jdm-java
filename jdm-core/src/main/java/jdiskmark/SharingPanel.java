package jdiskmark;

import javax.swing.*;
import java.awt.*;
import java.net.URI;

/**
 * Sharing tab panel (issue #117).
 *
 * Layout:
 *
 *  ┌──────────────────────────────────────────────────┬──────────────────────────────────────────────┐
 *  │ Community portal sharing  http://test.jdisk…     │ Endpoint — http://test.jdiskmark.net:5000/…  │
 *  │ ☑ Share benchmark results          ● Enabled     │  ○ Production  ● Test  ○ Localhost            │
 *  │ ☐ Share SMART snapshots            ○ Disabled    │  Protocol:  ○ HTTPS  ● HTTP                  │
 *  └──────────────────────────────────────────────────┴──────────────────────────────────────────────┘
 *
 * The titled border on the right dev panel doubles as a URL preview updated
 * live whenever the endpoint or protocol changes.
 * Developer controls are hidden by default — unlocked via Help > Dev Mode.
 */
public class SharingPanel extends JPanel {

    // ── Controls ──────────────────────────────────────────────────────────────
    private final JCheckBox enableCheckBox;
    private final JCheckBox enableSmartCheckBox;
    private final JLabel    statusLabel;
    private final JLabel    smartStatusLabel;

    private final JRadioButton localRb;
    private final JRadioButton testRb;
    private final JRadioButton prodRb;

    private final JRadioButton httpRb;
    private final JRadioButton httpsRb;

    /** The right dev-controls panel whose titled border shows the live URL. */
    private final JPanel devPanel;

    /** Button in the header row that opens the active portal URL in the browser. */
    private final JButton portalButton;


    public SharingPanel() {
        setLayout(new BorderLayout());

        // ── Outer split: left (toggles) | right (dev controls) ────────────────
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setBorder(BorderFactory.createEmptyBorder(8, 6, 8, 6));

        // ── LEFT: three compact rows ───────────────────────────────────────────
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 8));

        // Row 0 — header: Title Case label + "View Portal ↗" button
        portalButton = makePortalButton();
        JPanel headerRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerRow.add(new JLabel("<html><b>Community Portal Sharing</b></html>"));
        headerRow.add(portalButton);

        // Row 1 — benchmark checkbox + status inline
        enableCheckBox = new JCheckBox("Share benchmark results");
        statusLabel = new JLabel("\u25cb Disabled");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        statusLabel.setForeground(Color.GRAY);

        JPanel benchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        benchRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        benchRow.add(enableCheckBox);
        benchRow.add(statusLabel);

        // Row 2 — SMART checkbox + status inline
        enableSmartCheckBox = new JCheckBox("Share SMART snapshots");
        smartStatusLabel = new JLabel("\u25cb Disabled");
        smartStatusLabel.setFont(smartStatusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        smartStatusLabel.setForeground(Color.GRAY);

        JPanel smartRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        smartRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        smartRow.add(enableSmartCheckBox);
        smartRow.add(smartStatusLabel);

        leftPanel.add(headerRow);
        leftPanel.add(benchRow);
        leftPanel.add(smartRow);
        leftPanel.add(Box.createVerticalGlue());

        // Hide SMART sharing controls when smartctl is not available (e.g. Flatpak)
        if (!App.isSmartSupported()) {
            smartRow.setVisible(false);
            App.shareSmartPortal = false;
        }

        // ── RIGHT: developer controls ─────────────────────────────────────────
        ButtonGroup endpointGroup = new ButtonGroup();
        prodRb  = new JRadioButton("Production (www.jdiskmark.net)");
        testRb  = new JRadioButton("Test (test.jdiskmark.net)");
        localRb = new JRadioButton("Localhost");
        endpointGroup.add(prodRb);
        endpointGroup.add(testRb);
        endpointGroup.add(localRb);

        ButtonGroup protocolGroup = new ButtonGroup();
        httpsRb = new JRadioButton("HTTPS");
        httpRb  = new JRadioButton("HTTP");
        protocolGroup.add(httpsRb);
        protocolGroup.add(httpRb);

        JPanel endpointRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        endpointRow.add(prodRb);
        endpointRow.add(testRb);
        endpointRow.add(localRb);

        JPanel protocolRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        protocolRow.add(new JLabel("Protocol:"));
        protocolRow.add(httpsRb);
        protocolRow.add(httpRb);

        devPanel = new JPanel(new GridBagLayout());
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

        oc.gridx = 0; oc.weightx = 0.45; oc.insets = new Insets(0, 0, 0, 4);
        grid.add(leftPanel, oc);

        oc.gridx = 1; oc.weightx = 0.55; oc.insets = new Insets(0, 0, 0, 0);
        grid.add(devPanel, oc);

        // ── Scroll pane ────────────────────────────────────────────────────────
        JPanel northWrapper = new JPanel(new BorderLayout());
        northWrapper.add(grid, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(northWrapper,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);

        // Dev panel hidden by default — unlocked via Help > Dev Mode
        devPanel.setVisible(false);

        // ── Listeners ──────────────────────────────────────────────────────────
        enableCheckBox.addActionListener(e -> onToggleSharing());
        enableSmartCheckBox.addActionListener(e -> onToggleSmartSharing());
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

    private void onToggleSmartSharing() {
        App.shareSmartPortal = enableSmartCheckBox.isSelected();
        App.saveConfig();
        App.msg(App.shareSmartPortal ? "SMART portal upload enabled." : "SMART portal upload disabled.");
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
        enableSmartCheckBox.setSelected(App.shareSmartPortal);

        if (App.sharePortal) {
            statusLabel.setText("\u25cf Enabled");
            statusLabel.setForeground(new Color(0, 150, 60));
        } else {
            statusLabel.setText("\u25cb Disabled");
            statusLabel.setForeground(Color.GRAY);
        }

        if (App.isSmartSupported()) {
            if (App.shareSmartPortal) {
                smartStatusLabel.setText("\u25cf Enabled");
                smartStatusLabel.setForeground(new Color(0, 150, 60));
            } else {
                smartStatusLabel.setText("\u25cb Disabled");
                smartStatusLabel.setForeground(Color.GRAY);
            }
        }

        // Endpoint radio
        if (Portal.uploadResourceLocator.equalsIgnoreCase(Portal.LOCAL_UPLOAD_LOCATOR)) {
            localRb.setSelected(true);
        } else if (Portal.uploadResourceLocator.equalsIgnoreCase(Portal.TEST_UPLOAD_LOCATOR)) {
            testRb.setSelected(true);
        } else {
            prodRb.setSelected(true);
        }

        // Protocol radio
        if (Portal.uploadProtocol.equalsIgnoreCase(Portal.HTTPS)) {
            httpsRb.setSelected(true);
        } else {
            httpRb.setSelected(true);
        }

        refreshBorderTitle();
    }

    /**
     * Updates the titled border of the dev panel and the header portal link
     * to reflect the currently active endpoint.
     */
    private void refreshBorderTitle() {
        boolean benchmark = enableCheckBox.isSelected();
        boolean smart     = enableSmartCheckBox.isSelected();
        String title;
        if (benchmark && smart) {
            title = "Endpoint \u2014 " + Portal.getUploadUrl() + "  |  " + Portal.getSmartUploadUrl();
        } else if (benchmark) {
            title = "Endpoint \u2014 " + Portal.getUploadUrl();
        } else if (smart) {
            title = "Endpoint \u2014 " + Portal.getSmartUploadUrl();
        } else {
            title = "Endpoint \u2014 No endpoint";
        }
        devPanel.setBorder(BorderFactory.createTitledBorder(title));
        devPanel.repaint();

        // Keep the portal button tooltip in sync with the active endpoint
        portalButton.setToolTipText(Portal.getPortalBrowseUrl());
    }

    /**
     * Creates a small button that opens {@link Portal#getPortalBrowseUrl()}
     * in the system browser when clicked.
     */
    private JButton makePortalButton() {
        JButton button = new JButton("View Portal \u2197");
        button.setFont(button.getFont().deriveFont(Font.PLAIN, 11f));
        button.setToolTipText(Portal.getPortalBrowseUrl());
        button.setFocusPainted(false);
        button.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(URI.create(Portal.getPortalBrowseUrl()));
            } catch (Exception ex) {
                App.err("Could not open portal URL: " + ex.getMessage());
            }
        });
        return button;
    }

    /**
     * Shows or hides the developer endpoint/protocol controls.
     * Called by the Help > Dev Mode menu item after password verification.
     */
    public void setDevModeVisible(boolean visible) {
        devPanel.setVisible(visible);
        revalidate();
        repaint();
    }

    /** Returns true if the dev panel is currently visible. */
    public boolean isDevModeVisible() {
        return devPanel.isVisible();
    }
}
