
package jdiskmark;

import static jdiskmark.App.dataDir;
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
import static jdiskmark.App.IoEngine.MODERN;
import static jdiskmark.App.SectorAlignment.ALIGN_512;
import jdiskmark.Benchmark.BenchmarkType;
import jdiskmark.Benchmark.BlockSequence;
import net.miginfocom.swing.MigLayout;

/**
 * The parent frame of the app
 */
public final class MainFrame extends javax.swing.JFrame {

    public static final DecimalFormat DF = new DecimalFormat("###.##");
    
    /**
     * Creates new form MainFrame
     */
    @SuppressWarnings("unchecked")
    public MainFrame() {
        initComponents();
        
        //for diagnostics
        //controlsPanel.setBackground(Color.blue);
        
        Gui.createChartPanel();
        cResultMountPanel.setLayout(new BorderLayout());
        Gui.chartPanel.setSize(cResultMountPanel.getSize());
        Gui.chartPanel.setSize(cResultMountPanel.getWidth(), 200);
        cResultMountPanel.add(Gui.chartPanel);
        BenchmarkControlPanel bcPanel = Gui.createControlPanel();
        bControlMountPanel.setLayout(new MigLayout());
        bControlMountPanel.add(bcPanel);
        totalTxProgBar.setStringPainted(true);
        totalTxProgBar.setValue(0);
        totalTxProgBar.setString("");
        
        StringBuilder titleSb = new StringBuilder();
        titleSb.append(getTitle()).append(" ").append(App.VERSION);    

        initializeComboSettings();
        
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
    }

    public JPanel getMountPanel() {
        return cResultMountPanel;
    }
    
    /**
     * This method is called when the gui needs to be updated after a new config
     * has been loaded.
     */
    public void loadPropertiesConfig() {
        loadBenchmarkConfig();
        if (App.locationDir != null) { // set the location dir if not null
            setLocation(App.locationDir.getAbsolutePath());
        }
        
        // test portal settings
        portalUploadMenuItem.setSelected(App.sharePortal);
        portalEndpointMenu.setEnabled(App.sharePortal);
        if (Portal.uploadUrl.equalsIgnoreCase(Portal.LOCAL_UPLOAD_ENDPOINT)) {
            localEndpointRbMenuItem.setSelected(true);
        }
        if (Portal.uploadUrl.equalsIgnoreCase(Portal.TEST_UPLOAD_ENDPOINT)) {
            testEndpointRbMenuItem.setSelected(true);
        }
        if (Portal.uploadUrl.equalsIgnoreCase(Portal.PRODUCTION_UPLOAD_ENDPOINT)) {
            prodEndpointRbMenuItem.setSelected(true);
        }
        
        multiFileCheckBoxMenuItem.setSelected(App.multiFile);
        autoRemoveCheckBoxMenuItem.setSelected(App.autoRemoveData);
        autoResetCheckBoxMenuItem.setSelected(App.autoReset);
        // display preferences
        showMaxMinCheckBoxMenuItem.setSelected(App.showMaxMin);
        showAccessCheckBoxMenuItem.setSelected(App.showDriveAccess);
        switch (Gui.palette) {
            case CLASSIC -> {
                classicPaletteMenuItem.setSelected(true);
                Gui.setClassicColorScheme();
            }
            case BLUE_GREEN -> {
                blueGreenPaletteMenuItem.setSelected(true);
                Gui.setBlueGreenScheme();
            }
            case BARD_COOL -> {
                bardCoolPaletteMenuItem.setSelected(true);
                Gui.setCoolColorScheme();
            }
            case BARD_WARM -> {
                bardWarmPaletteMenuItem.setSelected(true);
                Gui.setWarmColorScheme();
            }
        }
    }
    
    private void setProfileToCustom() {
        // do not adjust if profile is being triggered
        if (Gui.controlPanel.profileCombo.hasFocus()) return;
        // Check if the current profile is already CUSTOM_TEST to prevent unnecessary UI flicker
        if (App.activeProfile == BenchmarkProfile.CUSTOM_TEST) return;
        
        App.activeProfile = BenchmarkProfile.CUSTOM_TEST;
        Gui.controlPanel.profileCombo.setSelectedItem(BenchmarkProfile.CUSTOM_TEST);
        System.out.println("Profile reset to CUSTOM_TEST due to configuration change.");
    }
    
    public void initializeComboSettings() {
        loadBenchmarkConfig();
        
        // action listeners to detect change and update custom profile
        final BenchmarkType[] previousBenchmarkType = { (BenchmarkType) Gui.controlPanel.typeCombo.getSelectedItem() };
        Gui.controlPanel.typeCombo.addActionListener(e -> {
            if (!Gui.controlPanel.typeCombo.hasFocus()) return;
            BenchmarkType currentSelection = (BenchmarkType)Gui.controlPanel.typeCombo.getSelectedItem();
            if (currentSelection != null && !currentSelection.equals(previousBenchmarkType[0])) {
                //System.out.println("previous=" + previousBenchmarkType[0] + " curr=" + currentSelection);
                previousBenchmarkType[0] = currentSelection; // update for next check
                setProfileToCustom();
            }
        });
        final BlockSequence[] previousSequence = { (BlockSequence) Gui.controlPanel.orderCombo.getSelectedItem() };
        Gui.controlPanel.orderCombo.addActionListener(e -> {
            if (!Gui.controlPanel.orderCombo.hasFocus()) return;
            BlockSequence currentSelection = (BlockSequence)Gui.controlPanel.orderCombo.getSelectedItem();
            if (currentSelection != null && !currentSelection.equals(previousSequence[0])) {
                previousSequence[0] = currentSelection; // update for next check
                setProfileToCustom();
            }
        });
        final int[] previousNumThreads = { Integer.parseInt((String)Gui.controlPanel.numThreadsCombo.getSelectedItem()) };
        Gui.controlPanel.numThreadsCombo.addActionListener(e -> {
            int currentSelection = Integer.parseInt((String)Gui.controlPanel.numThreadsCombo.getSelectedItem());
            if (currentSelection != previousNumThreads[0]) {
                previousNumThreads[0] = currentSelection; // update for next check
                setProfileToCustom();
            }
        });
        final int[] previousNumSamples = { Integer.parseInt((String)Gui.controlPanel.numSamplesCombo.getSelectedItem()) };
        Gui.controlPanel.numSamplesCombo.addActionListener(e -> {
            int currentSelection = Integer.parseInt((String)Gui.controlPanel.numSamplesCombo.getSelectedItem());
            if (currentSelection != previousNumSamples[0]) {
                previousNumSamples[0] = currentSelection; // update for next check
                setProfileToCustom();
            }
        });
        final int[] previousNumBlocks = { Integer.parseInt((String)Gui.controlPanel.numBlocksCombo.getSelectedItem()) };
        Gui.controlPanel.numBlocksCombo.addActionListener(e -> {
            int currentSelection = Integer.parseInt((String)Gui.controlPanel.numBlocksCombo.getSelectedItem());
            if (currentSelection != previousNumBlocks[0]) {
                previousNumBlocks[0] = currentSelection; // update for next check
                setProfileToCustom();
            }
        });
        final int[] previousBlockSize = { Integer.parseInt((String)Gui.controlPanel.blockSizeCombo.getSelectedItem()) };
        Gui.controlPanel.blockSizeCombo.addActionListener(e -> {
            int currentSelection = Integer.parseInt((String)Gui.controlPanel.blockSizeCombo.getSelectedItem());
            if (currentSelection != previousBlockSize[0]) {
                previousBlockSize[0] = currentSelection; // update for next check
                setProfileToCustom();
            }
        });
    }

    public void loadBenchmarkConfig() {
        Gui.controlPanel.profileCombo.setSelectedItem(App.activeProfile);
        // basic benchmark config
        Gui.controlPanel.typeCombo.setSelectedItem(App.benchmarkType);
        Gui.controlPanel.numThreadsCombo.setSelectedItem(String.valueOf(App.numOfThreads));
        Gui.controlPanel.orderCombo.setSelectedItem(App.blockSequence);
        Gui.controlPanel.numBlocksCombo.setSelectedItem(String.valueOf(App.numOfBlocks));
        Gui.controlPanel.blockSizeCombo.setSelectedItem(String.valueOf(App.blockSizeKb));
        Gui.controlPanel.numSamplesCombo.setSelectedItem(String.valueOf(App.numOfSamples));
        // advanced benchmark config
        multiFileCheckBoxMenuItem.setSelected(App.multiFile);
        switch (App.ioEngine) {
            case MODERN -> engModernRbMenuItem.setSelected(true);
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
            case ALIGN_512 -> align512RbMenuItem.setSelected(true);
            case ALIGN_4K -> align4KRbMenuItem.setSelected(true);
            case ALIGN_8K -> align8KRbMenuItem.setSelected(true);
            case ALIGN_16K -> align16KRbMenuItem.setSelected(true);
            case ALIGN_64K -> align64KRbMenuItem.setSelected(true);
        }
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
        jMenuItem1 = new javax.swing.JMenuItem();
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
        align512RbMenuItem = new javax.swing.JRadioButtonMenuItem();
        align4KRbMenuItem = new javax.swing.JRadioButtonMenuItem();
        align8KRbMenuItem = new javax.swing.JRadioButtonMenuItem();
        align16KRbMenuItem = new javax.swing.JRadioButtonMenuItem();
        align64KRbMenuItem = new javax.swing.JRadioButtonMenuItem();
        jSeparator3 = new javax.swing.JPopupMenu.Separator();
        multiFileCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        autoRemoveCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        autoResetCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        jSeparator2 = new javax.swing.JPopupMenu.Separator();
        showMaxMinCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        showAccessCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
        jSeparator1 = new javax.swing.JPopupMenu.Separator();
        colorPaletteMenu = new javax.swing.JMenu();
        classicPaletteMenuItem = new javax.swing.JRadioButtonMenuItem();
        blueGreenPaletteMenuItem = new javax.swing.JRadioButtonMenuItem();
        bardCoolPaletteMenuItem = new javax.swing.JRadioButtonMenuItem();
        bardWarmPaletteMenuItem = new javax.swing.JRadioButtonMenuItem();
        helpMenu = new javax.swing.JMenu();
        portalUploadMenuItem = new javax.swing.JCheckBoxMenuItem();
        portalEndpointMenu = new javax.swing.JMenu();
        localEndpointRbMenuItem = new javax.swing.JRadioButtonMenuItem();
        testEndpointRbMenuItem = new javax.swing.JRadioButtonMenuItem();
        prodEndpointRbMenuItem = new javax.swing.JRadioButtonMenuItem();
        jMenuItem2 = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("JDiskMark");

        tabbedPane.addTab("Benchmark Operations", runPanel);

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
                .addContainerGap(48, Short.MAX_VALUE))
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
            .addGap(0, 393, Short.MAX_VALUE)
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

        jMenuItem1.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F4, java.awt.event.InputEvent.ALT_DOWN_MASK));
        jMenuItem1.setText("Exit");
        jMenuItem1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(jMenuItem1);

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

        directIoCbMenuItem.setSelected(true);
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
        optionMenu.add(jSeparator3);

        multiFileCheckBoxMenuItem.setSelected(true);
        multiFileCheckBoxMenuItem.setText("Multi Data File");
        multiFileCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                multiFileCheckBoxMenuItemActionPerformed(evt);
            }
        });
        optionMenu.add(multiFileCheckBoxMenuItem);

        autoRemoveCheckBoxMenuItem.setSelected(true);
        autoRemoveCheckBoxMenuItem.setText("Auto Remove Data Dir");
        autoRemoveCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                autoRemoveCheckBoxMenuItemActionPerformed(evt);
            }
        });
        optionMenu.add(autoRemoveCheckBoxMenuItem);

        autoResetCheckBoxMenuItem.setSelected(true);
        autoResetCheckBoxMenuItem.setText("Auto Reset");
        autoResetCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                autoResetCheckBoxMenuItemActionPerformed(evt);
            }
        });
        optionMenu.add(autoResetCheckBoxMenuItem);
        optionMenu.add(jSeparator2);

        showMaxMinCheckBoxMenuItem.setSelected(true);
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

        colorPaletteMenu.setText("Color Palette");
        palettebuttonGroup.add(colorPaletteMenu);

        palettebuttonGroup.add(classicPaletteMenuItem);
        classicPaletteMenuItem.setText("Classic");
        classicPaletteMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                classicPaletteMenuItemActionPerformed(evt);
            }
        });
        colorPaletteMenu.add(classicPaletteMenuItem);

        palettebuttonGroup.add(blueGreenPaletteMenuItem);
        blueGreenPaletteMenuItem.setText("Blue Green");
        blueGreenPaletteMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                blueGreenPaletteMenuItemActionPerformed(evt);
            }
        });
        colorPaletteMenu.add(blueGreenPaletteMenuItem);

        palettebuttonGroup.add(bardCoolPaletteMenuItem);
        bardCoolPaletteMenuItem.setText("Bard Cool");
        bardCoolPaletteMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bardCoolPaletteMenuItemActionPerformed(evt);
            }
        });
        colorPaletteMenu.add(bardCoolPaletteMenuItem);

        palettebuttonGroup.add(bardWarmPaletteMenuItem);
        bardWarmPaletteMenuItem.setText("Bard Warm");
        bardWarmPaletteMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bardWarmPaletteMenuItemActionPerformed(evt);
            }
        });
        colorPaletteMenu.add(bardWarmPaletteMenuItem);

        optionMenu.add(colorPaletteMenu);

        menuBar.add(optionMenu);

        helpMenu.setText("Help");

        portalUploadMenuItem.setSelected(true);
        portalUploadMenuItem.setText("Portal Upload");
        portalUploadMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                portalUploadMenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(portalUploadMenuItem);

        portalEndpointMenu.setText("Portal Endpoint");

        portalEndpointButtonGroup.add(localEndpointRbMenuItem);
        localEndpointRbMenuItem.setText("LOCALHOST");
        localEndpointRbMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                localEndpointRbMenuItemActionPerformed(evt);
            }
        });
        portalEndpointMenu.add(localEndpointRbMenuItem);

        portalEndpointButtonGroup.add(testEndpointRbMenuItem);
        testEndpointRbMenuItem.setText("test.jdiskmark.net");
        testEndpointRbMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                testEndpointRbMenuItemActionPerformed(evt);
            }
        });
        portalEndpointMenu.add(testEndpointRbMenuItem);

        portalEndpointButtonGroup.add(prodEndpointRbMenuItem);
        prodEndpointRbMenuItem.setText("www.jdiskmark.net");
        prodEndpointRbMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                prodEndpointRbMenuItemActionPerformed(evt);
            }
        });
        portalEndpointMenu.add(prodEndpointRbMenuItem);

        helpMenu.add(portalEndpointMenu);

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
                .addComponent(tabbedPane, javax.swing.GroupLayout.DEFAULT_SIZE, 146, Short.MAX_VALUE)
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
        JOptionPane.showMessageDialog(Gui.mainFrame, 
                "JDiskMark " + App.VERSION, "About...", JOptionPane.PLAIN_MESSAGE);
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
    }//GEN-LAST:event_multiFileCheckBoxMenuItemActionPerformed

    private void autoRemoveCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_autoRemoveCheckBoxMenuItemActionPerformed
        App.autoRemoveData = autoRemoveCheckBoxMenuItem.getState();
        App.saveConfig();
    }//GEN-LAST:event_autoRemoveCheckBoxMenuItemActionPerformed

    private void deleteDataMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteDataMenuItemActionPerformed
        Util.deleteDirectory(dataDir);
    }//GEN-LAST:event_deleteDataMenuItemActionPerformed

    private void autoResetCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_autoResetCheckBoxMenuItemActionPerformed
        App.autoReset = autoResetCheckBoxMenuItem.getState();
        App.saveConfig();
    }//GEN-LAST:event_autoResetCheckBoxMenuItemActionPerformed

    private void resetSequenceMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetSequenceMenuItemActionPerformed
        App.resetSequence();
    }//GEN-LAST:event_resetSequenceMenuItemActionPerformed

    private void showMaxMinCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showMaxMinCheckBoxMenuItemActionPerformed
        App.showMaxMin = showMaxMinCheckBoxMenuItem.getState();
        App.saveConfig();
    }//GEN-LAST:event_showMaxMinCheckBoxMenuItemActionPerformed

    private void writeSyncCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_writeSyncCheckBoxMenuItemActionPerformed
        App.writeSyncEnable = writeSyncCheckBoxMenuItem.getState();
        App.saveConfig();
    }//GEN-LAST:event_writeSyncCheckBoxMenuItemActionPerformed

    private void deleteAllBenchmarksItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteAllBenchmarksItemActionPerformed
        int result = JOptionPane.showConfirmDialog(this, 
            "Delete all benchmarks?", 
            "Confirm Delete", 
            JOptionPane.YES_NO_OPTION);

        if (result == JOptionPane.YES_OPTION) {
            App.msg("Deleting all benchmarks.");
            App.deleteAllBenchmarks();
        }
    }//GEN-LAST:event_deleteAllBenchmarksItemActionPerformed

    private void showAccessCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showAccessCheckBoxMenuItemActionPerformed
        App.showDriveAccess = showAccessCheckBoxMenuItem.getState();
        App.saveConfig();
    }//GEN-LAST:event_showAccessCheckBoxMenuItemActionPerformed

    private void blueGreenPaletteMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_blueGreenPaletteMenuItemActionPerformed
        Gui.setBlueGreenScheme();
        App.saveConfig();
    }//GEN-LAST:event_blueGreenPaletteMenuItemActionPerformed

    private void classicPaletteMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_classicPaletteMenuItemActionPerformed
        Gui.setClassicColorScheme();
        App.saveConfig();
    }//GEN-LAST:event_classicPaletteMenuItemActionPerformed

    private void bardCoolPaletteMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bardCoolPaletteMenuItemActionPerformed
        Gui.setCoolColorScheme();
        App.saveConfig();
    }//GEN-LAST:event_bardCoolPaletteMenuItemActionPerformed

    private void bardWarmPaletteMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bardWarmPaletteMenuItemActionPerformed
        Gui.setWarmColorScheme();
        App.saveConfig();
    }//GEN-LAST:event_bardWarmPaletteMenuItemActionPerformed

    private void deleteSelBenchmarksItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteSelBenchmarksItemActionPerformed
        int result = JOptionPane.showConfirmDialog(this, 
            "Delete selected benchmarks?", 
            "Confirm Delete", 
            JOptionPane.YES_NO_OPTION);

        if (result == JOptionPane.YES_OPTION) {
            App.msg("Deleting selected benchmarks.");
            List<UUID> benchmarkIds = Gui.runPanel.getSelectedIds();
            App.deleteBenchmarks(benchmarkIds);
        }
    }//GEN-LAST:event_deleteSelBenchmarksItemActionPerformed

    private void resetBenchmarkItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetBenchmarkItemActionPerformed
        App.resetTestData();
        Gui.resetBenchmarkData();
        Gui.updateLegendAndAxis();
    }//GEN-LAST:event_resetBenchmarkItemActionPerformed

    private void portalUploadMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_portalUploadMenuItemActionPerformed
        if (portalUploadMenuItem.getState() == true) {
            PortalEnableDialog dialog = new PortalEnableDialog(this);
            dialog.setVisible(true); // Execution pauses here because it's modal
            if (!dialog.isAuthorized()) {
                msg("test passcode required to upload benchmarks");
                portalUploadMenuItem.setSelected(false);
                return;
            }
        }
        App.sharePortal = portalUploadMenuItem.getState();
        App.saveConfig();
        if (App.sharePortal) {
            App.msg("portal upload enabled");
        } else {
            App.msg("portal upload disabled");
        }
        portalEndpointMenu.setEnabled(App.sharePortal);
    }//GEN-LAST:event_portalUploadMenuItemActionPerformed
    private void directIoCbMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_directIoCbMenuItemActionPerformed
        App.directEnable = directIoCbMenuItem.isSelected();
        App.saveConfig();
    }//GEN-LAST:event_directIoCbMenuItemActionPerformed

    private void engModernRbMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_engModernRbMenuItemActionPerformed
        App.ioEngine = App.IoEngine.MODERN;
        directIoCbMenuItem.setEnabled(true);
        sectorAlignmentMenu.setEnabled(true);
        App.saveConfig();
    }//GEN-LAST:event_engModernRbMenuItemActionPerformed

    private void engLegacyRbMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_engLegacyRbMenuItemActionPerformed
        App.ioEngine = App.IoEngine.LEGACY;
        directIoCbMenuItem.setEnabled(false);
        sectorAlignmentMenu.setEnabled(false);
        App.saveConfig();
    }//GEN-LAST:event_engLegacyRbMenuItemActionPerformed

    private void align512RbMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_align512RbMenuItemActionPerformed
        App.sectorAlignment = App.SectorAlignment.ALIGN_512;
        App.saveConfig();
    }//GEN-LAST:event_align512RbMenuItemActionPerformed

    private void align4KRbMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_align4KRbMenuItemActionPerformed
        App.sectorAlignment = App.SectorAlignment.ALIGN_4K;
        App.saveConfig();
    }//GEN-LAST:event_align4KRbMenuItemActionPerformed

    private void align8KRbMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_align8KRbMenuItemActionPerformed
        App.sectorAlignment = App.SectorAlignment.ALIGN_8K;
        App.saveConfig();
    }//GEN-LAST:event_align8KRbMenuItemActionPerformed

    private void align16KRbMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_align16KRbMenuItemActionPerformed
        App.sectorAlignment = App.SectorAlignment.ALIGN_16K;
        App.saveConfig();
    }//GEN-LAST:event_align16KRbMenuItemActionPerformed

    private void align64KRbMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_align64KRbMenuItemActionPerformed
        App.sectorAlignment = App.SectorAlignment.ALIGN_64K;
        App.saveConfig();
    }//GEN-LAST:event_align64KRbMenuItemActionPerformed

    private void localEndpointRbMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_localEndpointRbMenuItemActionPerformed
        Portal.uploadUrl = Portal.LOCAL_UPLOAD_ENDPOINT;
        App.saveConfig();
    }//GEN-LAST:event_localEndpointRbMenuItemActionPerformed

    private void testEndpointRbMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_testEndpointRbMenuItemActionPerformed
        Portal.uploadUrl = Portal.TEST_UPLOAD_ENDPOINT;
        App.saveConfig();
    }//GEN-LAST:event_testEndpointRbMenuItemActionPerformed

    private void prodEndpointRbMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_prodEndpointRbMenuItemActionPerformed
        Portal.uploadUrl = Portal.PRODUCTION_UPLOAD_ENDPOINT;
        App.saveConfig();
    }//GEN-LAST:event_prodEndpointRbMenuItemActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenu actionMenu;
    private javax.swing.JRadioButtonMenuItem align16KRbMenuItem;
    private javax.swing.JRadioButtonMenuItem align4KRbMenuItem;
    private javax.swing.JRadioButtonMenuItem align512RbMenuItem;
    private javax.swing.JRadioButtonMenuItem align64KRbMenuItem;
    private javax.swing.JRadioButtonMenuItem align8KRbMenuItem;
    private javax.swing.JCheckBoxMenuItem autoRemoveCheckBoxMenuItem;
    private javax.swing.JCheckBoxMenuItem autoResetCheckBoxMenuItem;
    private javax.swing.JPanel bControlMountPanel;
    private javax.swing.JRadioButtonMenuItem bardCoolPaletteMenuItem;
    private javax.swing.JRadioButtonMenuItem bardWarmPaletteMenuItem;
    private javax.swing.JRadioButtonMenuItem blueGreenPaletteMenuItem;
    private javax.swing.JPanel cResultMountPanel;
    private javax.swing.JButton chooseButton;
    private javax.swing.JRadioButtonMenuItem classicPaletteMenuItem;
    private javax.swing.JMenuItem clearLogsItem;
    private javax.swing.JMenu colorPaletteMenu;
    private javax.swing.JLabel dataDirLabel;
    private javax.swing.JMenuItem deleteAllBenchmarksItem;
    private javax.swing.JMenuItem deleteDataMenuItem;
    private javax.swing.JMenuItem deleteSelBenchmarksItem;
    private javax.swing.JCheckBoxMenuItem directIoCbMenuItem;
    private javax.swing.JRadioButtonMenuItem engLegacyRbMenuItem;
    private javax.swing.JRadioButtonMenuItem engModernRbMenuItem;
    private javax.swing.JScrollPane eventScrollPane;
    private javax.swing.JMenu fileMenu;
    private javax.swing.JMenu helpMenu;
    private javax.swing.JMenu ioEngineMenu;
    private javax.swing.ButtonGroup ioEnginebuttonGroup;
    private javax.swing.JLabel jLabel22;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JMenuItem jMenuItem1;
    private javax.swing.JMenuItem jMenuItem2;
    private javax.swing.JPopupMenu.Separator jSeparator1;
    private javax.swing.JPopupMenu.Separator jSeparator2;
    private javax.swing.JPopupMenu.Separator jSeparator3;
    private javax.swing.JRadioButtonMenuItem localEndpointRbMenuItem;
    private javax.swing.JPanel locationPanel;
    private javax.swing.JTextField locationText;
    private javax.swing.JMenuBar menuBar;
    private javax.swing.JTextArea msgTextArea;
    private javax.swing.JCheckBoxMenuItem multiFileCheckBoxMenuItem;
    private javax.swing.JButton openLocButton;
    private javax.swing.JMenu optionMenu;
    private javax.swing.ButtonGroup palettebuttonGroup;
    private javax.swing.ButtonGroup portalEndpointButtonGroup;
    private javax.swing.JMenu portalEndpointMenu;
    private javax.swing.JCheckBoxMenuItem portalUploadMenuItem;
    private javax.swing.JRadioButtonMenuItem prodEndpointRbMenuItem;
    private javax.swing.JPanel progressPanel;
    private javax.swing.JMenuItem resetBenchmarkItem;
    private javax.swing.JMenuItem resetSequenceMenuItem;
    private jdiskmark.BenchmarkPanel runPanel;
    private javax.swing.ButtonGroup sectorAlignbuttonGroup;
    private javax.swing.JMenu sectorAlignmentMenu;
    private javax.swing.JCheckBoxMenuItem showAccessCheckBoxMenuItem;
    private javax.swing.JCheckBoxMenuItem showMaxMinCheckBoxMenuItem;
    private javax.swing.JTabbedPane tabbedPane;
    private javax.swing.JRadioButtonMenuItem testEndpointRbMenuItem;
    private javax.swing.JProgressBar totalTxProgBar;
    private javax.swing.JCheckBoxMenuItem writeSyncCheckBoxMenuItem;
    // End of variables declaration//GEN-END:variables

    public void setLocation(String path ) {
        locationText.setText(path);
    }
    
    public void msg(String message) {
        msgTextArea.append(message+'\n');
    }
  
    public void applyTestParams() {
        BenchmarkType mode = (BenchmarkType) Gui.controlPanel.typeCombo.getSelectedItem();
        App.benchmarkType = mode;
        App.blockSequence = (BlockSequence) Gui.controlPanel.orderCombo.getSelectedItem();
        App.numOfSamples = Integer.parseInt((String) Gui.controlPanel.numSamplesCombo.getSelectedItem());
        App.numOfBlocks = Integer.parseInt((String) Gui.controlPanel.numBlocksCombo.getSelectedItem());
        App.blockSizeKb = Integer.parseInt((String) Gui.controlPanel.blockSizeCombo.getSelectedItem());
        App.numOfThreads = Integer.parseInt((String) Gui.controlPanel.numThreadsCombo.getSelectedItem());
        //Gui.controlPanel.sampleSizeLabel.setText(String.valueOf(App.targetMarkSizeKb()));
        totalTxProgBar.setString(String.valueOf(App.targetTxSizeKb()));
    }
    
    public void refreshWriteMetrics() {
        String value;
        value = App.wMin == -1 ? "- -" : DF.format(App.wMin);
        Gui.controlPanel.wMinLabel.setText(value);
        value = App.wMax == -1 ? "- -" : DF.format(App.wMax);
        Gui.controlPanel.wMaxLabel.setText(value);
        value = App.wAvg == -1 ? "- -" : DF.format(App.wAvg);
        Gui.controlPanel.wAvgLabel.setText(value);
        value = App.wAcc == -1 ? "- -" : DF.format(App.wAcc);
        Gui.controlPanel.wAccessLabel.setText(value);
        value = App.wIops == -1 ? "- -" : String.valueOf(App.wIops);
        Gui.controlPanel.wIopsLabel.setText(value);
    }
    
    public void refreshReadMetrics() {
        String value;
        value = App.rMin == -1 ? "- -" : DF.format(App.rMin);
        Gui.controlPanel.rMinLabel.setText(value);
        value = App.rMax == -1 ? "- -" : DF.format(App.rMax);
        Gui.controlPanel.rMaxLabel.setText(value);
        value = App.rAvg == -1 ? "- -" : DF.format(App.rAvg);
        Gui.controlPanel.rAvgLabel.setText(value);
        value = App.rAcc == -1 ? "- -" : DF.format(App.rAcc);
        Gui.controlPanel.rAccessLabel.setText(value);
        value = App.rIops == -1 ? "- -" : String.valueOf(App.rIops);
        Gui.controlPanel.rIopsLabel.setText(value);
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
                Gui.controlPanel.startButton.setText("Cancel");
                Gui.controlPanel.profileCombo.setEnabled(false);
                Gui.controlPanel.orderCombo.setEnabled(false);
                Gui.controlPanel.blockSizeCombo.setEnabled(false);
                Gui.controlPanel.numBlocksCombo.setEnabled(false);
                Gui.controlPanel.numSamplesCombo.setEnabled(false);
                Gui.controlPanel.typeCombo.setEnabled(false);
                Gui.controlPanel.numThreadsCombo.setEnabled(false);
                resetBenchmarkItem.setEnabled(false);
            }
            case App.State.IDLE_STATE -> {
                Gui.controlPanel.startButton.setText("Start");
                Gui.controlPanel.profileCombo.setEnabled(true);
                Gui.controlPanel.orderCombo.setEnabled(true);
                Gui.controlPanel.blockSizeCombo.setEnabled(true);
                Gui.controlPanel.numBlocksCombo.setEnabled(true);
                Gui.controlPanel.numSamplesCombo.setEnabled(true);
                Gui.controlPanel.typeCombo.setEnabled(true);
                Gui.controlPanel.numThreadsCombo.setEnabled(true);
                resetBenchmarkItem.setEnabled(true);
            }
        }
    }
}
