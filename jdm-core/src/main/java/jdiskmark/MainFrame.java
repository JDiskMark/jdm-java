package jdiskmark;

import static jdiskmark.App.SLASH_DATADIRNAME;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.text.DefaultCaret;
import jdiskmark.Exporter.ExportFormat;
import org.metricus.jdm.ui.AppIcon;
import org.metricus.jdm.ui.GraphPaletteMenu;
import org.metricus.jdm.ui.GraphThemeMenu;
import org.metricus.jdm.ui.Theme;
import net.miginfocom.swing.MigLayout;

/**
 * The parent frame of the app
 */
public final class MainFrame extends javax.swing.JFrame {

    public static final DecimalFormat DF = new DecimalFormat("###.##");

    /**
     * Sharing tab panel — built programmatically, added to tabbedPane in the
     * constructor.
     */
    public SharingPanel sharingPanel;

    /** Toggle item for archive view — text changes between "View Archive" and "Exit Archive View". */
    private final javax.swing.JMenuItem archiveViewItem = new javax.swing.JMenuItem("View Archive");
    /** Unarchive menu item — only meaningful while in archive view. */
    private final javax.swing.JMenuItem unarchiveSelectedItem = new javax.swing.JMenuItem("Unarchive Selected");

    /**
     * Graph Palette submenu — built programmatically from the {@link Gui.Palette}
     * enum so that adding a new palette never touches the NetBeans form.
     */
    private final GraphPaletteMenu graphPaletteMenu = new GraphPaletteMenu();
    GraphPaletteMenu getGraphPaletteMenu() { return graphPaletteMenu; }

    /**
     * Window Theme submenu — built programmatically from the {@link Gui.Theme}
     * enum so that adding a new theme never touches the NetBeans form.
     */
    private final GraphThemeMenu graphThemeMenu = new GraphThemeMenu();
    
    /**
     * Creates new form MainFrame
     */
    @SuppressWarnings("unchecked")
    public MainFrame() {
        initComponents();
        
        // The Drive Location tab is superseded by the Drives tab in the main
        // navigation pane — remove it from the bottom tabbed pane at runtime.
        // The NetBeans-generated field (locationPanel) is kept intact in the form.
        tabbedPane.remove(locationPanel);

        // Replace the NetBeans-generated colorPaletteMenu with our data-driven
        // GraphPaletteMenu — inserted at the same menu position.
        int paletteIndex = -1;
        for (int i = 0; i < optionMenu.getMenuComponentCount(); i++) {
            if (optionMenu.getMenuComponent(i) == colorPaletteMenu) {
                paletteIndex = i;
                break;
            }
        }
        if (paletteIndex >= 0) {
            optionMenu.remove(colorPaletteMenu);
            optionMenu.add(graphPaletteMenu, paletteIndex);
        }

        // Replace the NetBeans-generated themeMenu with our data-driven
        // GraphThemeMenu — inserted at the same menu position.
        int themeIndex = -1;
        for (int i = 0; i < optionMenu.getMenuComponentCount(); i++) {
            if (optionMenu.getMenuComponent(i) == themeMenu) {
                themeIndex = i;
                break;
            }
        }
        if (themeIndex >= 0) {
            optionMenu.remove(themeMenu);
            optionMenu.add(graphThemeMenu, themeIndex);
        }
        
        //for diagnostics
        //controlsPanel.setBackground(Color.blue);
        
        javax.swing.JPanel chartWrapper = Gui.createChartPanel();
        cResultMountPanel.setLayout(new BorderLayout());
        Gui.chartPanel.setSize(cResultMountPanel.getSize());
        Gui.chartPanel.setSize(cResultMountPanel.getWidth(), 200);
        Gui.smartPanel = new SmartPanel();
        cResultMountPanel.add(chartWrapper);
        BenchmarkControlPanel bcPanel = Gui.createControlPanel();
        bControlMountPanel.setLayout(new MigLayout());
        bControlMountPanel.add(bcPanel);
        getRootPane().setDefaultButton(bcPanel.startButton);
        totalTxProgBar.setStringPainted(true);
        totalTxProgBar.setValue(0);
        totalTxProgBar.setString("");
        
        StringBuilder titleSb = new StringBuilder();
        titleSb.append(getTitle()).append(" ").append(App.VERSION);
        
        syncFromModel();
        
        // architecture
        if (App.arch != null && !App.arch.isEmpty()) {
            titleSb.append(" - ").append(App.arch);
        }
        
        // processor name
        if (App.processorName != null && !App.processorName.isEmpty()) {
            titleSb.append(" - ").append(App.processorName);
        }
        
        // permission indicator
        if (App.isAdmin) titleSb.append(" [Admin]");
        if (App.isRoot) titleSb.append(" [root]");
        
        setTitle(titleSb.toString());
        
        // auto scroll the text area.
        DefaultCaret caret = (DefaultCaret)msgTextArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        
        
        // Build the left-side main navigation tab pane on all platforms.
        // The Benchmark tab contains the control panel (left) + chart (right).
        // The bottom tabbedPane (Benchmark Operations / Events / Drive Location) stays below.
        javax.swing.JTabbedPane mainTabPane = new javax.swing.JTabbedPane(javax.swing.JTabbedPane.LEFT);
        mainTabPane.putClientProperty("JTabbedPane.tabRotation", "auto");

        // Drive tab — always visible on all platforms, shown first
        Gui.drivePanel = new DrivePanel();
        mainTabPane.addTab("Drive", Gui.drivePanel);

        // All Drives table — lives in the bottom tabbedPane
        tabbedPane.addTab("All Drives", Gui.drivePanel.buildAllDrivesPanel());

        JPanel benchTab = new JPanel(new BorderLayout());
        benchTab.add(bControlMountPanel, BorderLayout.WEST);
        benchTab.add(cResultMountPanel, BorderLayout.CENTER);
        mainTabPane.addTab("Benchmark", benchTab);
        // Start on the Benchmark tab — it's the primary interaction surface.
        mainTabPane.setSelectedIndex(mainTabPane.getTabCount() - 1);

        // SMART tab — Linux only (requires smartctl / NVMe kernel support)
        if (App.isLinux()) {
            mainTabPane.addTab("SMART", Gui.smartPanel);
            Gui.smartReportsPanel = new SmartReportsPanel();
            // SMART Reports lives in the bottom tabbedPane alongside Benchmark Operations + Events
            tabbedPane.addTab("SMART Reports", Gui.smartReportsPanel);
        }
        // #117 Sharing tab — added programmatically so the NetBeans form is untouched.
        sharingPanel = new SharingPanel();
        tabbedPane.addTab("Sharing", sharingPanel);

        // Store reference so SmartReportsPanel can switch to the SMART tab on row selection.
        Gui.mainTabPane = mainTabPane;

        // Refresh SMART Reports when its bottom-pane tab is selected.
        tabbedPane.addChangeListener(e -> {
            int sel = tabbedPane.getSelectedIndex();
            if (sel >= 0 && "SMART Reports".equals(tabbedPane.getTitleAt(sel))) {
                if (Gui.smartReportsPanel != null) Gui.smartReportsPanel.refresh();
            }
        });

        // Rebuild the content pane: mainTabPane (top) and the bottom panel (tabbedPane +
        // progress bar) are separated by a draggable vertical JSplitPane divider.
        getContentPane().removeAll();
        getContentPane().setLayout(new BorderLayout());

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(tabbedPane, BorderLayout.CENTER);
        southPanel.add(progressPanel, BorderLayout.SOUTH);

        javax.swing.JSplitPane splitPane = new javax.swing.JSplitPane(
                javax.swing.JSplitPane.VERTICAL_SPLIT, mainTabPane, southPanel);
        splitPane.setResizeWeight(0.0);   // all new vertical space goes to the bottom pane
        splitPane.setDividerSize(6);
        splitPane.setContinuousLayout(true);

        // Place the divider at the minimum position so the bottom panel gets
        // maximum space on first launch. The user can drag it up to expose more
        // of the top panel.
        addComponentListener(new java.awt.event.ComponentAdapter() {
            private boolean initialised = false;
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (!initialised) {
                    initialised = true;
                    splitPane.setDividerLocation(splitPane.getMinimumDividerLocation());
                }
            }
        });

        getContentPane().add(splitPane, BorderLayout.CENTER);

        // Ensure the frame is tall enough to show 5 rows in the bottom table.
        // pack() sizes to preferred; we nudge the height up slightly after packing.
        addComponentListener(new java.awt.event.ComponentAdapter() {
            private boolean heightAdjusted = false;
            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                if (!heightAdjusted) {
                    heightAdjusted = true;
                    setSize(getWidth(), getHeight() + 30);
                }
            }
        });

        // Archive menu items — added programmatically to keep the NetBeans GEN block untouched.
        actionMenu.addSeparator();

        javax.swing.JMenuItem archiveSelectedItem = new javax.swing.JMenuItem("Archive Selected Benchmark");
        archiveSelectedItem.addActionListener(evt -> {
            List<UUID> ids = Gui.runPanel.getSelectedIds();
            if (ids.isEmpty()) {
                javax.swing.JOptionPane.showMessageDialog(this,
                        "No benchmark selected.", "Archive",
                        javax.swing.JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            App.msg("Archiving " + ids.size() + " benchmark(s).");
            App.archiveBenchmarks(ids);
            App.msg("Benchmark(s) archived.");
        });
        actionMenu.add(archiveSelectedItem);

        archiveViewItem.addActionListener(evt -> {
            App.archiveViewActive = !App.archiveViewActive;
            App.benchmarks.clear();
            App.loadBenchmarks();
            archiveViewItem.setText(App.archiveViewActive ? "Exit Archive View" : "View Archive");
            unarchiveSelectedItem.setEnabled(App.archiveViewActive);
            App.msg(App.archiveViewActive ? "Viewing archived benchmarks." : "Viewing benchmark history.");
            for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                String t = tabbedPane.getTitleAt(i);
                if (t.equals("Benchmarks") || t.equals("Archived Benchmarks")) {
                    tabbedPane.setTitleAt(i, App.archiveViewActive ? "Archived Benchmarks" : "Benchmark Operations");
                    break;
                }
            }
        });
        actionMenu.add(archiveViewItem);

        unarchiveSelectedItem.setEnabled(false);
        unarchiveSelectedItem.addActionListener(evt -> {
            List<UUID> ids = Gui.runPanel.getSelectedIds();
            if (ids.isEmpty()) {
                javax.swing.JOptionPane.showMessageDialog(this,
                        "No benchmark selected.", "Unarchive",
                        javax.swing.JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            App.msg("Unarchiving " + ids.size() + " benchmark(s).");
            App.unarchiveBenchmarks(ids);
            App.msg("Benchmark(s) unarchived.");
        });
        actionMenu.add(unarchiveSelectedItem);
    }
    
    public JPanel getMountPanel() {
        return cResultMountPanel;
    }

    /**
     * This method is called when the gui needs to be updated after a new config
     * has been loaded.
     */
    public void loadPropertiesConfig() {
        syncFromModel();
        if (App.locationDir != null) { // set the location dir if not null
            setLocation(App.locationDir.getAbsolutePath());
        }
        
        // Sharing tab reflects the current portal state.
        if (sharingPanel != null) {
            sharingPanel.refresh();
        }
        
        multiFileCheckBoxMenuItem.setSelected(App.multiFile);
        // display preferences
        showSingleOpMenuItem.setSelected(Gui.showSingleOp);
        showMaxMinCheckBoxMenuItem.setSelected(Gui.showMaxMin);
        showAccessCheckBoxMenuItem.setSelected(Gui.showDriveAccess);
        showBadgesCbMenuItem.setSelected(Gui.showBadges); // overrides initComponents() which hardcodes setSelected(true)
        graphThemeMenu.syncFromModel();
        graphPaletteMenu.syncFromModel();
    }

    public void syncFromModel() {
        // basic benchmark config
        if (Gui.controlPanel != null) {
            Gui.controlPanel.refreshSettings();
        }

        // advanced benchmark config
        multiFileCheckBoxMenuItem.setSelected(App.multiFile);
        switch (App.ioEngine) {
            case MODERN -> {
                engModernRbMenuItem.setSelected(true);
                directIoCbMenuItem.setEnabled(true);
                sectorAlignmentMenu.setEnabled(true);
            }
            case LEGACY -> {
                engLegacyRbMenuItem.setSelected(true);
                directIoCbMenuItem.setEnabled(false);
                sectorAlignmentMenu.setEnabled(false);
            }
        }
        writeSyncCheckBoxMenuItem.setSelected(App.writeSyncEnable);
        directIoCbMenuItem.setSelected(App.directEnable);
        // sector alignment
        switch (App.sectorAlignment) {
            case NONE -> alignNoneRbMenuItem.setSelected(true);
            case ALIGN_512 -> align512RbMenuItem.setSelected(true);
            case ALIGN_4K -> align4KRbMenuItem.setSelected(true);
            case ALIGN_8K -> align8KRbMenuItem.setSelected(true);
            case ALIGN_16K -> align16KRbMenuItem.setSelected(true);
            case ALIGN_64K -> align64KRbMenuItem.setSelected(true);
        }
        smartCbMenuItem.setSelected(Smart.smartEnable);
        exportMenu.setEnabled(App.benchmark != null);
        Gui.refreshChartBadges();
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        palettebuttonGroup = new javax.swing.ButtonGroup();
        ioEnginebuttonGroup = new javax.swing.ButtonGroup();
        sectorAlignbuttonGroup = new javax.swing.ButtonGroup();
        portalEndpointButtonGroup = new javax.swing.ButtonGroup();
        themeButtonGroup = new javax.swing.ButtonGroup();
        protocolButtonGroup = new javax.swing.ButtonGroup();
        tabbedPane = new javax.swing.JTabbedPane();
        runPanel = new jdiskmark.BenchmarkPanel();
        eventScrollPane = new javax.swing.JScrollPane();
        msgTextArea = new javax.swing.JTextArea();
        locationPanel = new javax.swing.JPanel();
        chooseButton = new javax.swing.JButton();
        locationText = new javax.swing.JTextField();
        openLocButton = new javax.swing.JButton();
        dataDirLabel = new javax.swing.JLabel();
        jLabel22 = new javax.swing.JLabel();
        cResultMountPanel = new javax.swing.JPanel();
        progressPanel = new javax.swing.JPanel();
        totalTxProgBar = new javax.swing.JProgressBar();
        jLabel7 = new javax.swing.JLabel();
        bControlMountPanel = new javax.swing.JPanel();
        menuBar = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        exportMenu = new javax.swing.JMenu();
        exportJsonMenuItem = new javax.swing.JMenuItem();
        exportYmlMenuItem = new javax.swing.JMenuItem();
        exportCsvMenuItem = new javax.swing.JMenuItem();
        exitMenuItem = new javax.swing.JMenuItem();
        actionMenu = new javax.swing.JMenu();
        clearLogsItem = new javax.swing.JMenuItem();
        deleteDataMenuItem = new javax.swing.JMenuItem();
        deleteSelBenchmarksItem = new javax.swing.JMenuItem();
        deleteAllBenchmarksItem = new javax.swing.JMenuItem();
        resetSequenceMenuItem = new javax.swing.JMenuItem();
        resetBenchmarkItem = new javax.swing.JMenuItem();
        optionMenu = new javax.swing.JMenu();
        ioEngineMenu = new javax.swing.JMenu();
        engModernRbMenuItem = new javax.swing.JRadioButtonMenuItem();
        engLegacyRbMenuItem = new javax.swing.JRadioButtonMenuItem();
        directIoCbMenuItem = new javax.swing.JCheckBoxMenuItem();
        writeSyncCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        sectorAlignmentMenu = new javax.swing.JMenu();
        alignNoneRbMenuItem = new javax.swing.JRadioButtonMenuItem();
        align512RbMenuItem = new javax.swing.JRadioButtonMenuItem();
        align4KRbMenuItem = new javax.swing.JRadioButtonMenuItem();
        align8KRbMenuItem = new javax.swing.JRadioButtonMenuItem();
        align16KRbMenuItem = new javax.swing.JRadioButtonMenuItem();
        align64KRbMenuItem = new javax.swing.JRadioButtonMenuItem();
        multiFileCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        jSeparator2 = new javax.swing.JPopupMenu.Separator();
        showBadgesCbMenuItem = new javax.swing.JCheckBoxMenuItem();
        showSingleOpMenuItem = new javax.swing.JCheckBoxMenuItem();
        showMaxMinCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        showAccessCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        jSeparator1 = new javax.swing.JPopupMenu.Separator();
        themeMenu = new javax.swing.JMenu();
        lightThemeRbMenuItem = new javax.swing.JRadioButtonMenuItem();
        darkThemeRbMenuItem = new javax.swing.JRadioButtonMenuItem();
        darculaThemeRbMenuItem = new javax.swing.JRadioButtonMenuItem();
        colorPaletteMenu = new javax.swing.JMenu();
        jSeparator3 = new javax.swing.JPopupMenu.Separator();
        smartCbMenuItem = new javax.swing.JCheckBoxMenuItem();
        advancedOptionsMenuItem = new javax.swing.JMenuItem();
        helpMenu = new javax.swing.JMenu();
        jMenuItem2 = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("JDiskMark");

        tabbedPane.addTab("Benchmarks", runPanel);

        msgTextArea.setEditable(false);
        msgTextArea.setColumns(20);
        msgTextArea.setFont(new java.awt.Font("Monospaced", 0, 11)); // NOI18N
        msgTextArea.setRows(5);
        msgTextArea.setTabSize(4);
        eventScrollPane.setViewportView(msgTextArea);

        tabbedPane.addTab("Events", eventScrollPane);

        chooseButton.setText("Browse");
        chooseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chooseButtonActionPerformed(evt);
            }
        });

        locationText.setEditable(false);

        openLocButton.setText("Open");
        openLocButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openLocButtonActionPerformed(evt);
            }
        });

        dataDirLabel.setText(SLASH_DATADIRNAME);

        jLabel22.setText("Specify drive location where data files will be generated and read from to mesaure performance.");

        javax.swing.GroupLayout locationPanelLayout = new javax.swing.GroupLayout(locationPanel);
        locationPanel.setLayout(locationPanelLayout);
        locationPanelLayout.setHorizontalGroup(
            locationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(locationPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(locationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(locationPanelLayout.createSequentialGroup()
                        .addComponent(jLabel22, javax.swing.GroupLayout.PREFERRED_SIZE, 561, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(locationPanelLayout.createSequentialGroup()
                        .addComponent(locationText, javax.swing.GroupLayout.PREFERRED_SIZE, 480, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(dataDirLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 191, Short.MAX_VALUE)
                        .addGap(18, 18, 18)
                        .addComponent(chooseButton, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(openLocButton, javax.swing.GroupLayout.PREFERRED_SIZE, 74, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        locationPanelLayout.setVerticalGroup(
            locationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(locationPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(locationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(locationText, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(chooseButton)
                    .addComponent(openLocButton)
                    .addComponent(dataDirLabel))
                .addGap(18, 18, 18)
                .addComponent(jLabel22)
                .addContainerGap(95, Short.MAX_VALUE))
        );

        tabbedPane.addTab("Drive Location", locationPanel);

        cResultMountPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        cResultMountPanel.setMaximumSize(new java.awt.Dimension(503, 200));

        javax.swing.GroupLayout cResultMountPanelLayout = new javax.swing.GroupLayout(cResultMountPanel);
        cResultMountPanel.setLayout(cResultMountPanelLayout);
        cResultMountPanelLayout.setHorizontalGroup(
            cResultMountPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        cResultMountPanelLayout.setVerticalGroup(
            cResultMountPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 346, Short.MAX_VALUE)
        );

        jLabel7.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        jLabel7.setText("Total Tx (KB)");

        javax.swing.GroupLayout progressPanelLayout = new javax.swing.GroupLayout(progressPanel);
        progressPanel.setLayout(progressPanelLayout);
        progressPanelLayout.setHorizontalGroup(
            progressPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, progressPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel7, javax.swing.GroupLayout.PREFERRED_SIZE, 83, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(totalTxProgBar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        progressPanelLayout.setVerticalGroup(
            progressPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(progressPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(progressPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel7, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(totalTxProgBar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );

        javax.swing.GroupLayout bControlMountPanelLayout = new javax.swing.GroupLayout(bControlMountPanel);
        bControlMountPanel.setLayout(bControlMountPanelLayout);
        bControlMountPanelLayout.setHorizontalGroup(
            bControlMountPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 242, Short.MAX_VALUE)
        );
        bControlMountPanelLayout.setVerticalGroup(
            bControlMountPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );

        fileMenu.setText("File");

        exportMenu.setText("Export");
        exportMenu.setEnabled(false);

        exportJsonMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_J, java.awt.event.InputEvent.ALT_DOWN_MASK));
        exportJsonMenuItem.setText("JSON (.json)");
        exportJsonMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportJsonMenuItemActionPerformed(evt);
            }
        });
        exportMenu.add(exportJsonMenuItem);

        exportYmlMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Y, java.awt.event.InputEvent.ALT_DOWN_MASK));
        exportYmlMenuItem.setText("YAML (.yml)");
        exportYmlMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportYmlMenuItemActionPerformed(evt);
            }
        });
        exportMenu.add(exportYmlMenuItem);

        exportCsvMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, java.awt.event.InputEvent.ALT_DOWN_MASK));
        exportCsvMenuItem.setText("CSV (.csv)");
        exportCsvMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportCsvMenuItemActionPerformed(evt);
            }
        });
        exportMenu.add(exportCsvMenuItem);

        fileMenu.add(exportMenu);

        exitMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F4, java.awt.event.InputEvent.ALT_DOWN_MASK));
        exitMenuItem.setText("Exit");
        exitMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(exitMenuItem);

        menuBar.add(fileMenu);

        actionMenu.setText("Action");

        clearLogsItem.setText("Clear Event Logs");
        clearLogsItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearLogsItemActionPerformed(evt);
            }
        });
        actionMenu.add(clearLogsItem);

        deleteDataMenuItem.setText("Delete Data Directory");
        deleteDataMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteDataMenuItemActionPerformed(evt);
            }
        });
        actionMenu.add(deleteDataMenuItem);

        deleteSelBenchmarksItem.setText("Delete Selected Benchmark");
        deleteSelBenchmarksItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteSelBenchmarksItemActionPerformed(evt);
            }
        });
        actionMenu.add(deleteSelBenchmarksItem);

        deleteAllBenchmarksItem.setText("Delete All Benchmarks");
        deleteAllBenchmarksItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteAllBenchmarksItemActionPerformed(evt);
            }
        });
        actionMenu.add(deleteAllBenchmarksItem);

        resetSequenceMenuItem.setText("Reset Sequence");
        resetSequenceMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetSequenceMenuItemActionPerformed(evt);
            }
        });
        actionMenu.add(resetSequenceMenuItem);

        resetBenchmarkItem.setText("Reset Benchmark");
        resetBenchmarkItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetBenchmarkItemActionPerformed(evt);
            }
        });
        actionMenu.add(resetBenchmarkItem);

        menuBar.add(actionMenu);

        optionMenu.setText("Options");

        ioEngineMenu.setText("IO Engine");

        ioEnginebuttonGroup.add(engModernRbMenuItem);
        engModernRbMenuItem.setText("Modern (FFM API)");
        engModernRbMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                engModernRbMenuItemActionPerformed(evt);
            }
        });
        ioEngineMenu.add(engModernRbMenuItem);

        ioEnginebuttonGroup.add(engLegacyRbMenuItem);
        engLegacyRbMenuItem.setText("Legacy (RandomAccessFile)");
        engLegacyRbMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                engLegacyRbMenuItemActionPerformed(evt);
            }
        });
        ioEngineMenu.add(engLegacyRbMenuItem);

        optionMenu.add(ioEngineMenu);

        directIoCbMenuItem.setText("Direct IO (unbuffered)");
        directIoCbMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                directIoCbMenuItemActionPerformed(evt);
            }
        });
        optionMenu.add(directIoCbMenuItem);

        writeSyncCheckBoxMenuItem.setSelected(true);
        writeSyncCheckBoxMenuItem.setText("Write Sync");
        writeSyncCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                writeSyncCheckBoxMenuItemActionPerformed(evt);
            }
        });
        optionMenu.add(writeSyncCheckBoxMenuItem);

        sectorAlignmentMenu.setText("Sector Alignment");

        sectorAlignbuttonGroup.add(alignNoneRbMenuItem);
        alignNoneRbMenuItem.setSelected(true);
        alignNoneRbMenuItem.setText(App.SectorAlignment.NONE.toString());
        alignNoneRbMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                alignNoneRbMenuItemActionPerformed(evt);
            }
        });
        sectorAlignmentMenu.add(alignNoneRbMenuItem);

        sectorAlignbuttonGroup.add(align512RbMenuItem);
        align512RbMenuItem.setText(App.SectorAlignment.ALIGN_512.toString());
        align512RbMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                align512RbMenuItemActionPerformed(evt);
            }
        });
        sectorAlignmentMenu.add(align512RbMenuItem);

        sectorAlignbuttonGroup.add(align4KRbMenuItem);
        align4KRbMenuItem.setText(App.SectorAlignment.ALIGN_4K.toString());
        align4KRbMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                align4KRbMenuItemActionPerformed(evt);
            }
        });
        sectorAlignmentMenu.add(align4KRbMenuItem);

        sectorAlignbuttonGroup.add(align8KRbMenuItem);
        align8KRbMenuItem.setText(App.SectorAlignment.ALIGN_8K.toString());
        align8KRbMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                align8KRbMenuItemActionPerformed(evt);
            }
        });
        sectorAlignmentMenu.add(align8KRbMenuItem);

        sectorAlignbuttonGroup.add(align16KRbMenuItem);
        align16KRbMenuItem.setText(App.SectorAlignment.ALIGN_16K.toString());
        align16KRbMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                align16KRbMenuItemActionPerformed(evt);
            }
        });
        sectorAlignmentMenu.add(align16KRbMenuItem);

        sectorAlignbuttonGroup.add(align64KRbMenuItem);
        align64KRbMenuItem.setText(App.SectorAlignment.ALIGN_64K.toString());
        align64KRbMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                align64KRbMenuItemActionPerformed(evt);
            }
        });
        sectorAlignmentMenu.add(align64KRbMenuItem);

        optionMenu.add(sectorAlignmentMenu);

        multiFileCheckBoxMenuItem.setSelected(true);
        multiFileCheckBoxMenuItem.setText("Multi Data File");
        multiFileCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                multiFileCheckBoxMenuItemActionPerformed(evt);
            }
        });
        optionMenu.add(multiFileCheckBoxMenuItem);
        optionMenu.add(jSeparator2);

        showBadgesCbMenuItem.setSelected(true);
        showBadgesCbMenuItem.setText("Show Chart Badges");
        showBadgesCbMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showBadgesCbMenuItemActionPerformed(evt);
            }
        });
        optionMenu.add(showBadgesCbMenuItem);

        showSingleOpMenuItem.setSelected(true);
        showSingleOpMenuItem.setText("Show Single Operation");
        showSingleOpMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showSingleOpMenuItemActionPerformed(evt);
            }
        });
        optionMenu.add(showSingleOpMenuItem);

        showMaxMinCheckBoxMenuItem.setSelected(false);
        showMaxMinCheckBoxMenuItem.setText("Show Max Min");
        showMaxMinCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showMaxMinCheckBoxMenuItemActionPerformed(evt);
            }
        });
        optionMenu.add(showMaxMinCheckBoxMenuItem);

        showAccessCheckBoxMenuItem.setSelected(true);
        showAccessCheckBoxMenuItem.setText("Show Latency");
        showAccessCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showAccessCheckBoxMenuItemActionPerformed(evt);
            }
        });
        optionMenu.add(showAccessCheckBoxMenuItem);
        optionMenu.add(jSeparator1);

        themeMenu.setText("Window Theme");

        themeButtonGroup.add(lightThemeRbMenuItem);
        lightThemeRbMenuItem.setText("Light");
        lightThemeRbMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                lightThemeRbMenuItemActionPerformed(evt);
            }
        });
        themeMenu.add(lightThemeRbMenuItem);

        themeButtonGroup.add(darkThemeRbMenuItem);
        darkThemeRbMenuItem.setSelected(true);
        darkThemeRbMenuItem.setText("Dark");
        darkThemeRbMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                darkThemeRbMenuItemActionPerformed(evt);
            }
        });
        themeMenu.add(darkThemeRbMenuItem);

        themeButtonGroup.add(darculaThemeRbMenuItem);
        darculaThemeRbMenuItem.setText("Darcula");
        darculaThemeRbMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                darculaThemeRbMenuItemActionPerformed(evt);
            }
        });
        themeMenu.add(darculaThemeRbMenuItem);

        optionMenu.add(themeMenu);

        colorPaletteMenu.setText("Graph Palette");
        palettebuttonGroup.add(colorPaletteMenu);
        optionMenu.add(colorPaletteMenu);
        optionMenu.add(jSeparator3);

        smartCbMenuItem.setSelected(true);
        smartCbMenuItem.setText("Run SMART with Benchmark");
        smartCbMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                smartCbMenuItemActionPerformed(evt);
            }
        });
        optionMenu.add(smartCbMenuItem);

        advancedOptionsMenuItem.setText("Advanced Options…");
        advancedOptionsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                advancedOptionsMenuItemActionPerformed(evt);
            }
        });
        optionMenu.add(advancedOptionsMenuItem);

        menuBar.add(optionMenu);

        helpMenu.setText("Help");

        jMenuItem2.setText("About...");
        jMenuItem2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                aboutMenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(jMenuItem2);

        menuBar.add(helpMenu);

        setJMenuBar(menuBar);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(tabbedPane)
                .addContainerGap())
            .addComponent(progressPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addComponent(bControlMountPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(cResultMountPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(cResultMountPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(bControlMountPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tabbedPane)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(progressPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void chooseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chooseButtonActionPerformed
        Gui.browseLocation();
    }//GEN-LAST:event_chooseButtonActionPerformed

    private void exitMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitMenuItemActionPerformed
        System.exit(0);
    }//GEN-LAST:event_exitMenuItemActionPerformed

    private void aboutMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_aboutMenuItemActionPerformed
        Gui.showAboutDialog();
    }//GEN-LAST:event_aboutMenuItemActionPerformed

    private void openLocButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openLocButtonActionPerformed
        try {
            Desktop.getDesktop().open(App.locationDir);
        } catch (IOException ex) {
            Logger.getLogger(MainFrame.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_openLocButtonActionPerformed

    private void clearLogsItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearLogsItemActionPerformed
        clearMessages();
    }//GEN-LAST:event_clearLogsItemActionPerformed

    private void multiFileCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_multiFileCheckBoxMenuItemActionPerformed
        App.multiFile = multiFileCheckBoxMenuItem.getState();
        App.saveConfig();
        Gui.refreshChartBadges();
    }//GEN-LAST:event_multiFileCheckBoxMenuItemActionPerformed

    private void deleteDataMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteDataMenuItemActionPerformed
        Util.deleteDirectory(App.dataDir);
        App.msg("Data dir " + App.dataDir + " has been deleted.");
    }//GEN-LAST:event_deleteDataMenuItemActionPerformed

    private void resetSequenceMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetSequenceMenuItemActionPerformed
        App.resetSequence();
    }//GEN-LAST:event_resetSequenceMenuItemActionPerformed

    private void showMaxMinCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showMaxMinCheckBoxMenuItemActionPerformed
        Gui.showMaxMin = showMaxMinCheckBoxMenuItem.getState();
        App.saveConfig();
    }//GEN-LAST:event_showMaxMinCheckBoxMenuItemActionPerformed

    private void writeSyncCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_writeSyncCheckBoxMenuItemActionPerformed
        App.writeSyncEnable = writeSyncCheckBoxMenuItem.getState();
        App.saveConfig();
        Gui.refreshChartBadges();
    }//GEN-LAST:event_writeSyncCheckBoxMenuItemActionPerformed

    private void deleteAllBenchmarksItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteAllBenchmarksItemActionPerformed
        int result = JOptionPane.showConfirmDialog(this, 
            "Delete all benchmarks?", 
            "Confirm Delete", 
            JOptionPane.YES_NO_OPTION);

        if (result == JOptionPane.YES_OPTION) {
            App.msg("Deleting all benchmarks...");
            App.deleteAllBenchmarks();
            App.msg("All benchmarks have been deleted.");
        }
    }//GEN-LAST:event_deleteAllBenchmarksItemActionPerformed

    private void showAccessCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showAccessCheckBoxMenuItemActionPerformed
        Gui.showDriveAccess = showAccessCheckBoxMenuItem.getState();
        App.saveConfig();
    }//GEN-LAST:event_showAccessCheckBoxMenuItemActionPerformed

    private void deleteSelBenchmarksItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteSelBenchmarksItemActionPerformed
        int result = JOptionPane.showConfirmDialog(this, 
            "Delete selected benchmarks?", 
            "Confirm Delete", 
            JOptionPane.YES_NO_OPTION);

        if (result == JOptionPane.YES_OPTION) {
            App.msg("Deleting selected benchmarks.");
            List<UUID> benchmarkIds = Gui.runPanel.getSelectedIds();
            App.deleteBenchmarks(benchmarkIds);
            App.msg("Deleted " + benchmarkIds.size() + " benchmark(s).");
        }
    }//GEN-LAST:event_deleteSelBenchmarksItemActionPerformed

    private void resetBenchmarkItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetBenchmarkItemActionPerformed
        App.resetTestData();
        Gui.resetBenchmarkData();
        Gui.updateLegendAndAxis();
    }//GEN-LAST:event_resetBenchmarkItemActionPerformed

    private void directIoCbMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_directIoCbMenuItemActionPerformed
        App.directEnable = directIoCbMenuItem.isSelected();
        App.saveConfig();
        Gui.refreshChartBadges();
    }//GEN-LAST:event_directIoCbMenuItemActionPerformed

    private void engModernRbMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_engModernRbMenuItemActionPerformed
        App.ioEngine = App.IoEngine.MODERN;
        directIoCbMenuItem.setEnabled(true);
        sectorAlignmentMenu.setEnabled(true);
        App.saveConfig();
        Gui.refreshChartBadges();
    }//GEN-LAST:event_engModernRbMenuItemActionPerformed

    private void engLegacyRbMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_engLegacyRbMenuItemActionPerformed
        App.ioEngine = App.IoEngine.LEGACY;
        directIoCbMenuItem.setEnabled(false);
        sectorAlignmentMenu.setEnabled(false);
        App.saveConfig();
        Gui.refreshChartBadges();
    }//GEN-LAST:event_engLegacyRbMenuItemActionPerformed

    private void align512RbMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_align512RbMenuItemActionPerformed
        App.sectorAlignment = App.SectorAlignment.ALIGN_512;
        App.saveConfig();
        Gui.refreshChartBadges();
    }//GEN-LAST:event_align512RbMenuItemActionPerformed

    private void align4KRbMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_align4KRbMenuItemActionPerformed
        App.sectorAlignment = App.SectorAlignment.ALIGN_4K;
        App.saveConfig();
        Gui.refreshChartBadges();
    }//GEN-LAST:event_align4KRbMenuItemActionPerformed

    private void align8KRbMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_align8KRbMenuItemActionPerformed
        App.sectorAlignment = App.SectorAlignment.ALIGN_8K;
        App.saveConfig();
        Gui.refreshChartBadges();
    }//GEN-LAST:event_align8KRbMenuItemActionPerformed

    private void align16KRbMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_align16KRbMenuItemActionPerformed
        App.sectorAlignment = App.SectorAlignment.ALIGN_16K;
        App.saveConfig();
        Gui.refreshChartBadges();
    }//GEN-LAST:event_align16KRbMenuItemActionPerformed

    private void align64KRbMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_align64KRbMenuItemActionPerformed
        App.sectorAlignment = App.SectorAlignment.ALIGN_64K;
        App.saveConfig();
        Gui.refreshChartBadges();
    }//GEN-LAST:event_align64KRbMenuItemActionPerformed

    private void alignNoneRbMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_alignNoneRbMenuItemActionPerformed
        App.sectorAlignment = App.SectorAlignment.NONE;
        App.saveConfig();
        Gui.refreshChartBadges();
    }//GEN-LAST:event_alignNoneRbMenuItemActionPerformed

    private void darkThemeRbMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_darkThemeRbMenuItemActionPerformed
        Gui.theme = Theme.DARK;
        Theme.DARK.apply();
        App.saveConfig();
    }//GEN-LAST:event_darkThemeRbMenuItemActionPerformed

    private void lightThemeRbMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_lightThemeRbMenuItemActionPerformed
        Gui.theme = Theme.LIGHT;
        Theme.LIGHT.apply();
        App.saveConfig();
    }//GEN-LAST:event_lightThemeRbMenuItemActionPerformed

    private void darculaThemeRbMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_darculaThemeRbMenuItemActionPerformed
        Gui.theme = Theme.DARCULA;
        Theme.DARCULA.apply();
        App.saveConfig();
    }//GEN-LAST:event_darculaThemeRbMenuItemActionPerformed

    private void exportJsonMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportJsonMenuItemActionPerformed
        Exporter.exportBenchmarkAction(App.benchmark, ExportFormat.JSON);
    }//GEN-LAST:event_exportJsonMenuItemActionPerformed

    private void showSingleOpMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showSingleOpMenuItemActionPerformed
        Gui.showSingleOp = showSingleOpMenuItem.isSelected();
        App.saveConfig();
        Gui.singleOpTrigReloadGraph();
    }//GEN-LAST:event_showSingleOpMenuItemActionPerformed

    private void exportYmlMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportYmlMenuItemActionPerformed
        Exporter.exportBenchmarkAction(App.benchmark, ExportFormat.YAML);
    }//GEN-LAST:event_exportYmlMenuItemActionPerformed

    private void exportCsvMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportCsvMenuItemActionPerformed
        Exporter.exportBenchmarkAction(App.benchmark, ExportFormat.CSV);
    }//GEN-LAST:event_exportCsvMenuItemActionPerformed

    private void advancedOptionsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_advancedOptionsMenuItemActionPerformed
        AdvancedOptionsFrame frame = Gui.getAdvancedFrame();
        frame.syncFromModel(); // ensure controls reflect current state before showing
        frame.setVisible(true);
    }//GEN-LAST:event_advancedOptionsMenuItemActionPerformed

    private void showBadgesCbMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showBadgesCbMenuItemActionPerformed
        Gui.showBadges = showBadgesCbMenuItem.isSelected();
        Gui.setChartBadgesVisible(Gui.showBadges);
        App.saveConfig();
    }//GEN-LAST:event_showBadgesCbMenuItemActionPerformed

    private void smartCbMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_smartCbMenuItemActionPerformed
        Smart.smartEnable = this.smartCbMenuItem.isSelected();
        App.saveConfig();
        
    }//GEN-LAST:event_smartCbMenuItemActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenu actionMenu;
    private javax.swing.JMenuItem advancedOptionsMenuItem;
    private javax.swing.JRadioButtonMenuItem align16KRbMenuItem;
    private javax.swing.JRadioButtonMenuItem align4KRbMenuItem;
    private javax.swing.JRadioButtonMenuItem align512RbMenuItem;
    private javax.swing.JRadioButtonMenuItem align64KRbMenuItem;
    private javax.swing.JRadioButtonMenuItem align8KRbMenuItem;
    private javax.swing.JRadioButtonMenuItem alignNoneRbMenuItem;
    private javax.swing.JPanel bControlMountPanel;
    private javax.swing.JPanel cResultMountPanel;
    private javax.swing.JButton chooseButton;
    private javax.swing.JMenuItem clearLogsItem;
    private javax.swing.JMenu colorPaletteMenu;
    private javax.swing.JRadioButtonMenuItem darculaThemeRbMenuItem;
    private javax.swing.JRadioButtonMenuItem darkThemeRbMenuItem;
    private javax.swing.JLabel dataDirLabel;
    private javax.swing.JMenuItem deleteAllBenchmarksItem;
    private javax.swing.JMenuItem deleteDataMenuItem;
    private javax.swing.JMenuItem deleteSelBenchmarksItem;
    private javax.swing.JCheckBoxMenuItem directIoCbMenuItem;
    private javax.swing.JRadioButtonMenuItem engLegacyRbMenuItem;
    private javax.swing.JRadioButtonMenuItem engModernRbMenuItem;
    private javax.swing.JScrollPane eventScrollPane;
    private javax.swing.JMenuItem exitMenuItem;
    private javax.swing.JMenuItem exportCsvMenuItem;
    private javax.swing.JMenuItem exportJsonMenuItem;
    private javax.swing.JMenu exportMenu;
    private javax.swing.JMenuItem exportYmlMenuItem;
    private javax.swing.JMenu fileMenu;
    private javax.swing.JMenu helpMenu;
    private javax.swing.JMenu ioEngineMenu;
    private javax.swing.ButtonGroup ioEnginebuttonGroup;
    private javax.swing.JLabel jLabel22;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JMenuItem jMenuItem2;
    private javax.swing.JPopupMenu.Separator jSeparator1;
    private javax.swing.JPopupMenu.Separator jSeparator2;
    private javax.swing.JPopupMenu.Separator jSeparator3;
    private javax.swing.JRadioButtonMenuItem lightThemeRbMenuItem;
    private javax.swing.JPanel locationPanel;
    private javax.swing.JTextField locationText;
    private javax.swing.JMenuBar menuBar;
    private javax.swing.JTextArea msgTextArea;
    private javax.swing.JCheckBoxMenuItem multiFileCheckBoxMenuItem;
    private javax.swing.JButton openLocButton;
    private javax.swing.JMenu optionMenu;
    private javax.swing.ButtonGroup palettebuttonGroup;
    private javax.swing.ButtonGroup portalEndpointButtonGroup;
    private javax.swing.JPanel progressPanel;
    private javax.swing.ButtonGroup protocolButtonGroup;
    private javax.swing.JMenuItem resetBenchmarkItem;
    private javax.swing.JMenuItem resetSequenceMenuItem;
    private jdiskmark.BenchmarkPanel runPanel;
    private javax.swing.ButtonGroup sectorAlignbuttonGroup;
    private javax.swing.JMenu sectorAlignmentMenu;
    private javax.swing.JCheckBoxMenuItem showAccessCheckBoxMenuItem;
    private javax.swing.JCheckBoxMenuItem showBadgesCbMenuItem;
    private javax.swing.JCheckBoxMenuItem showMaxMinCheckBoxMenuItem;
    private javax.swing.JCheckBoxMenuItem showSingleOpMenuItem;
    private javax.swing.JCheckBoxMenuItem smartCbMenuItem;
    private javax.swing.JTabbedPane tabbedPane;
    private javax.swing.ButtonGroup themeButtonGroup;
    private javax.swing.JMenu themeMenu;
    private javax.swing.JProgressBar totalTxProgBar;
    private javax.swing.JCheckBoxMenuItem writeSyncCheckBoxMenuItem;
    // End of variables declaration//GEN-END:variables

    public void setLocation(String path) {
        locationText.setText(path);
    }
    
    public void msg(String message) {
        msgTextArea.append(message + '\n');
    }
    
    public void applyTestParams() {
        if (Gui.controlPanel != null) {
            Gui.controlPanel.applySettings();
        }
        totalTxProgBar.setString(String.valueOf(App.targetBenchmarkTxSizeKb()));
    }
    
    public javax.swing.JProgressBar getProgressBar() {
        return totalTxProgBar;
    }
    
    public void clearMessages() {
        msgTextArea.setText("");
    }
    
    /**
     * Disable buttons during a benchmark operation to avoid
     * the user from updating parameters.
     */
    public void adjustSensitivity() {
        switch (App.state) {
            case App.State.DISK_TEST_STATE -> {
                if (Gui.controlPanel != null) {
                    Gui.controlPanel.startButton.setText("Cancel");
                    Gui.applyCancelButtonStyle(Gui.theme);
                    Gui.controlPanel.enableControls(false);
                }
                resetBenchmarkItem.setEnabled(false);
                exportMenu.setEnabled(false);
            }
            case App.State.IDLE_STATE -> {
                if (Gui.controlPanel != null) {
                    Gui.controlPanel.startButton.setText("Start");
                    Gui.applyStartButtonStyle(Gui.theme);
                    Gui.controlPanel.enableControls(true);
                }
                resetBenchmarkItem.setEnabled(true);
                exportMenu.setEnabled(App.benchmark != null);
            }
        }
    }
}
