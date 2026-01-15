package jdiskmark;

import com.formdev.flatlaf.FlatLightLaf;
import java.awt.Font;
import net.miginfocom.swing.MigLayout;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.logging.Level;
import java.util.logging.Logger;
import static jdiskmark.MainFrame.DF;

public class BenchmarkControlPanel extends JPanel {

    final Font HEADER_FONT = new JLabel().getFont().deriveFont(Font.BOLD);
    final Integer [] THREAD_OPTIONS = {1,2,4,8,16,32};
    final Integer [] BLOCK_OPTIONS = {1,2,4,8,16,32,64,128,256,512,1024,2048};
    final Integer [] BLOCK_SIZES = {1,2,4,8,16,32,64,128,256,512,1024,2048};
    final Integer [] SAMPLE_OPTIONS = {25,50,100,200,300,500,1000,2000,3000,5000,10000};
    
    public JComboBox<BenchmarkProfile> profileCombo = new JComboBox<>(BenchmarkProfile.getDefaults());
    public JComboBox<Benchmark.BenchmarkType> typeCombo = new JComboBox<>(Benchmark.BenchmarkType.values());
    public JComboBox<Integer> numThreadsCombo = new JComboBox<>(THREAD_OPTIONS);
    public JComboBox<Benchmark.BlockSequence> orderCombo = new JComboBox<>(Benchmark.BlockSequence.values());
    public JComboBox<Integer> numBlocksCombo = new JComboBox<>(BLOCK_OPTIONS);
    public JComboBox<Integer> blockSizeCombo = new JComboBox<>(BLOCK_SIZES);
    public JComboBox<Integer> numSamplesCombo = new JComboBox<>(SAMPLE_OPTIONS);
    
    public JButton startButton = new JButton("Start");
    
    //public JLabel sampleSizeLabel = new JLabel("- -");
    public JLabel wMinLabel = new JLabel("- -");
    public JLabel wMaxLabel = new JLabel("- -");
    public JLabel wAvgLabel = new JLabel("- -");
    public JLabel wAccessLabel = new JLabel("- -");
    public JLabel wIopsLabel = new JLabel("- -");
    public JLabel rMinLabel = new JLabel("- -");
    public JLabel rMaxLabel = new JLabel("- -");
    public JLabel rAvgLabel = new JLabel("- -");
    public JLabel rAccessLabel = new JLabel("- -");
    public JLabel rIopsLabel = new JLabel("- -");
    
    public BenchmarkControlPanel() {
        initComponents();
        
        // locks down the preferred size to it's initialized sized
        setPreferredSize(getPreferredSize());
        
        // configure combo action listeners
        
        profileCombo.addActionListener((ActionEvent evt) -> {

            // only run when interacted with
            if (!profileCombo.hasFocus()) { return; }
            
            BenchmarkProfile profile = (BenchmarkProfile)profileCombo.getSelectedItem();
            App.activeProfile = profile;
            
            // skip adjustments if custom test was selected
            if (profile.equals(BenchmarkProfile.CUSTOM_TEST)) {
                return;
            }
            
            // TODO: later relocate into a BenchmarkConfiguration.java
            App.benchmarkType = profile.getBenchmarkType();
            App.blockSequence = profile.getBlockSequence();
            App.numOfThreads = profile.getNumThreads();
            App.numOfSamples = profile.getNumSamples();
            App.numOfBlocks = profile.getNumBlocks();
            App.blockSizeKb = profile.getBlockSizeKb();
            App.writeSyncEnable = profile.isWriteSyncEnable();
            App.multiFile = profile.isMultiFile();
            
            // only signal if initialized
            if (Gui.mainFrame != null) {
                Gui.mainFrame.loadActiveConfig();
            }
        });
        
        typeCombo.addActionListener((ActionEvent evt) -> {
            if (typeCombo.hasFocus()) {
                App.benchmarkType = (Benchmark.BenchmarkType)typeCombo.getSelectedItem();
                App.saveConfig();
            }
        });
        
        numThreadsCombo.addActionListener((ActionEvent evt) -> {
            // NOTE: selecting a value from dropdown does not trigger the below
            if (numThreadsCombo.hasFocus()) {
                App.numOfThreads = (Integer)numThreadsCombo.getSelectedItem();
                App.saveConfig();
            }
        });
        
        orderCombo.addActionListener((ActionEvent evt) -> {
            if (orderCombo.hasFocus()) {
                App.blockSequence = (Benchmark.BlockSequence)orderCombo.getSelectedItem();
                App.saveConfig();
            }
        });
        
        numBlocksCombo.addActionListener((ActionEvent evt) -> {
            // NOTE: selecting a value from dropdown does not trigger the below
            if (numBlocksCombo.hasFocus()) {
                App.numOfBlocks = (Integer)numBlocksCombo.getSelectedItem();
                //sampleSizeLabel.setText(String.valueOf(App.targetMarkSizeKb()));
                //totalTxProgBar.setString(String.valueOf(App.targetTxSizeKb()));
                App.saveConfig();
            }
        });
        
        blockSizeCombo.addActionListener((ActionEvent evt) -> {
            // NOTE: selecting a value from dropdown does not trigger the below
            if (blockSizeCombo.hasFocus()) {
                App.blockSizeKb = (Integer)blockSizeCombo.getSelectedItem();
                //sampleSizeLabel.setText(String.valueOf(App.targetMarkSizeKb()));
                //totalTxProgBar.setString(String.valueOf(App.targetTxSizeKb()));
                App.saveConfig();
            }
        });
        
        numSamplesCombo.addActionListener((ActionEvent evt) -> {
            // NOTE: selecting a value from dropdown does not trigger the below
            if (numSamplesCombo.hasFocus()) {
                App.numOfSamples = (Integer)numSamplesCombo.getSelectedItem();
                //sampleSizeLabel.setText(String.valueOf(App.targetMarkSizeKb()));
                //totalTxProgBar.setString(String.valueOf(App.targetTxSizeKb()));
                App.saveConfig();
            }
        });

        startButton.addActionListener((ActionEvent evt) -> {
            if (App.state == App.State.DISK_TEST_STATE) {
                App.cancelBenchmark();
            } else if (App.state == App.State.IDLE_STATE) {
                Gui.mainFrame.applyTestParams();
                App.saveConfig();
                App.startBenchmark();
            }
        });
    }
    
    private void initComponents() {
        
        // 3 column layout framework
        setLayout(new MigLayout("insets 0 5 0 5, fillx, wrap 3", "[30%][27%][43%]", "[]10[]"));

        // Profile
        add(new JLabel("Profile"), "align left");
        add(profileCombo, "span 2, growx");

        // Type
        add(new JLabel("Type"), "align left");
        add(typeCombo, "span 2, growx");

        // --- Bottom Inputs Section (roughly 2/3 - 1/3) ---

        // Number Threads
        add(new JLabel("Number Threads"), "span 2, align left");
        add(numThreadsCombo, "span 1, growx");
        // Block Order
        add(new JLabel("Block Order"), "span 2, align left");
        add(orderCombo, "span 1, growx");
        // Blocks / Sample
        add(new JLabel("Blocks / Sample"), "span 2, align left");
        add(numBlocksCombo, "span 1, growx");
        // Block Size
        add(new JLabel("Block Size (KB)"), "span 2, align left");
        add(blockSizeCombo, "span 1, growx");
        // No. Samples
        add(new JLabel("Number Samples"), "span 2, align left");
        add(numSamplesCombo, "span 1, growx");

        // summary info
        wAvgLabel.setHorizontalAlignment(SwingConstants.CENTER);
        wAccessLabel.setHorizontalAlignment(SwingConstants.CENTER);
        wIopsLabel.setHorizontalAlignment(SwingConstants.CENTER);
        rAccessLabel.setHorizontalAlignment(SwingConstants.CENTER);
        rAvgLabel.setHorizontalAlignment(SwingConstants.CENTER);
        rIopsLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // --- Start Button ---
        // "span": Spans all columns (100% width)
        add(startButton, "span, growx, gaptop 5, h 40!");

        // --- Stats Section (3-Column Grid) ---
        // Column 1: Metric name (left aligned)
        // Column 2: Write results (grow, width 0:pref)
        // Column 3: Read results (grow, width 0:pref)
        JPanel statsPanel = new JPanel(new MigLayout("insets 5, fillx, gap 10", "[][0:pref, grow][0:pref, grow]", "[]5[]"));
        
        // Header Row: blank | Write | Read
        statsPanel.add(new JLabel(""));
        JLabel writeHeader = new JLabel("Write");
        JLabel readHeader = new JLabel("Read");
        writeHeader.setFont(HEADER_FONT);
        readHeader.setFont(HEADER_FONT);
        statsPanel.add(writeHeader, "center");
        statsPanel.add(readHeader, "wrap, center");
        // Row 1: Throughput / BW
        addThreeColumnRow(statsPanel, "Bandwidth (MB/s)", wAvgLabel, rAvgLabel);
        // Row 2: Latency
        addThreeColumnRow(statsPanel, "Latency (ms)", wAccessLabel, rAccessLabel);
        // Row 3: IOPS
        addThreeColumnRow(statsPanel, "IOPS", wIopsLabel, rIopsLabel);
        // Add to main panel
        add(statsPanel, "span, growx, gaptop 0");
        
        startButton.requestFocus();
    }

    // Helper for the 3-column layout
    private void addThreeColumnRow(JPanel panel, String metric, JLabel writeVal, JLabel readVal) {
        panel.add(new JLabel(metric), "left");
        // wmin 0 prevents text updates from pushing the window wider
        panel.add(writeVal, "center, wmin 0, growx"); 
        panel.add(readVal, "center, wmin 0, growx, wrap");
    }
    
    private void setProfileToCustom() {
        // do not adjust if profile is being triggered
        if (profileCombo.hasFocus()) return;
        // Check if the current profile is already CUSTOM_TEST to prevent unnecessary UI flicker
        if (App.activeProfile == BenchmarkProfile.CUSTOM_TEST) return;
        
        App.activeProfile = BenchmarkProfile.CUSTOM_TEST;
        profileCombo.setSelectedItem(BenchmarkProfile.CUSTOM_TEST);
        System.out.println("Profile reset to CUSTOM_TEST due to configuration change.");
    }
    
    public void initializeComboSettings() {

        // action listeners to detect change and update custom profile
        final Benchmark.BenchmarkType[] previousBenchmarkType = { (Benchmark.BenchmarkType)typeCombo.getSelectedItem() };
        typeCombo.addActionListener(e -> {
            if (!typeCombo.hasFocus()) return;
            Benchmark.BenchmarkType currentSelection = (Benchmark.BenchmarkType)typeCombo.getSelectedItem();
            if (currentSelection != null && !currentSelection.equals(previousBenchmarkType[0])) {
                //System.out.println("previous=" + previousBenchmarkType[0] + " curr=" + currentSelection);
                previousBenchmarkType[0] = currentSelection; // update for next check
                setProfileToCustom();
            }
        });
        final Benchmark.BlockSequence[] previousSequence = { (Benchmark.BlockSequence)orderCombo.getSelectedItem() };
        orderCombo.addActionListener(e -> {
            if (!orderCombo.hasFocus()) return;
            Benchmark.BlockSequence currentSelection = (Benchmark.BlockSequence)orderCombo.getSelectedItem();
            if (currentSelection != null && !currentSelection.equals(previousSequence[0])) {
                previousSequence[0] = currentSelection; // update for next check
                setProfileToCustom();
            }
        });
        final int[] previousNumThreads = { (Integer)numThreadsCombo.getSelectedItem() };
        numThreadsCombo.addActionListener(e -> {
            int currentSelection = (Integer)numThreadsCombo.getSelectedItem();
            if (currentSelection != previousNumThreads[0]) {
                previousNumThreads[0] = currentSelection; // update for next check
                setProfileToCustom();
            }
        });
        final int[] previousNumSamples = { (Integer)numSamplesCombo.getSelectedItem() };
        numSamplesCombo.addActionListener(e -> {
            int currentSelection = (Integer)numSamplesCombo.getSelectedItem();
            if (currentSelection != previousNumSamples[0]) {
                previousNumSamples[0] = currentSelection; // update for next check
                setProfileToCustom();
            }
        });
        final int[] previousNumBlocks = { (Integer)numBlocksCombo.getSelectedItem() };
        numBlocksCombo.addActionListener(e -> {
            int currentSelection = (Integer)numBlocksCombo.getSelectedItem();
            if (currentSelection != previousNumBlocks[0]) {
                previousNumBlocks[0] = currentSelection; // update for next check
                setProfileToCustom();
            }
        });
        final int[] previousBlockSize = { (Integer)blockSizeCombo.getSelectedItem() };
        blockSizeCombo.addActionListener(e -> {
            int currentSelection = (Integer)blockSizeCombo.getSelectedItem();
            if (currentSelection != previousBlockSize[0]) {
                previousBlockSize[0] = currentSelection; // update for next check
                setProfileToCustom();
            }
        });
    }
    
    public void loadActiveConfig() {
        profileCombo.setSelectedItem(App.activeProfile);
        typeCombo.setSelectedItem(App.benchmarkType);
        numThreadsCombo.setSelectedItem(App.numOfThreads);
        orderCombo.setSelectedItem(App.blockSequence);
        numBlocksCombo.setSelectedItem(App.numOfBlocks);
        blockSizeCombo.setSelectedItem(App.blockSizeKb);
        numSamplesCombo.setSelectedItem(App.numOfSamples);
    }
    
    public void refreshWriteMetrics() {
        String value;
        // not currently used
//        value = App.wMin == -1 ? "- -" : DF.format(App.wMin);
//        wMinLabel.setText(value);
//        value = App.wMax == -1 ? "- -" : DF.format(App.wMax);
//        wMaxLabel.setText(value);
        value = App.wAvg == -1 ? "- -" : DF.format(App.wAvg);
        wAvgLabel.setText(value);
        value = App.wAcc == -1 ? "- -" : DF.format(App.wAcc);
        wAccessLabel.setText(value);
        value = App.wIops == -1 ? "- -" : String.valueOf(App.wIops);
        wIopsLabel.setText(value);
    }
    
    public void refreshReadMetrics() {
        String value;
        // not currently used
//        value = App.rMin == -1 ? "- -" : DF.format(App.rMin);
//        rMinLabel.setText(value);
//        value = App.rMax == -1 ? "- -" : DF.format(App.rMax);
//        rMaxLabel.setText(value);
        value = App.rAvg == -1 ? "- -" : DF.format(App.rAvg);
        rAvgLabel.setText(value);
        value = App.rAcc == -1 ? "- -" : DF.format(App.rAcc);
        rAccessLabel.setText(value);
        value = App.rIops == -1 ? "- -" : String.valueOf(App.rIops);
        rIopsLabel.setText(value);
    }
    
    public void enableControls(boolean enable) {
        profileCombo.setEnabled(enable);
        orderCombo.setEnabled(enable);
        blockSizeCombo.setEnabled(enable);
        numBlocksCombo.setEnabled(enable);
        numSamplesCombo.setEnabled(enable);
        typeCombo.setEnabled(enable);
        numThreadsCombo.setEnabled(enable);
    }
    
    public void applySettings() {
        App.benchmarkType = (Benchmark.BenchmarkType)typeCombo.getSelectedItem();
        App.blockSequence = (Benchmark.BlockSequence)orderCombo.getSelectedItem();
        App.numOfSamples = (Integer)numSamplesCombo.getSelectedItem();
        App.numOfBlocks = (Integer)numBlocksCombo.getSelectedItem();
        App.blockSizeKb = (Integer)blockSizeCombo.getSelectedItem();
        App.numOfThreads = (Integer)numThreadsCombo.getSelectedItem();
    }
    
    // Test Harness to view it immediately
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (UnsupportedLookAndFeelException ex) {
            Logger.getLogger(BenchmarkControlPanel.class.getName()).log(Level.SEVERE, null, ex);
        }
        JFrame frame = new JFrame("MigLayout Test");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(new BenchmarkControlPanel());
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}