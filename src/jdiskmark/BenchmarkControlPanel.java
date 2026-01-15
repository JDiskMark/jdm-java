package jdiskmark;

import com.formdev.flatlaf.FlatLightLaf;
import java.awt.Font;
import net.miginfocom.swing.MigLayout;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BenchmarkControlPanel extends JPanel {

    final Font HEADER_FONT = new JLabel().getFont().deriveFont(Font.BOLD);
    
    public JComboBox<BenchmarkProfile> profileCombo = new JComboBox<>(BenchmarkProfile.getDefaults());
    public JComboBox<Benchmark.BenchmarkType> typeCombo = new JComboBox<>(Benchmark.BenchmarkType.values());
    public JComboBox numThreadsCombo = new JComboBox<>(new String[]{"1","2","4","8","16", "32"});
    public JComboBox<Benchmark.BlockSequence> orderCombo = new JComboBox<>(Benchmark.BlockSequence.values());
    public JComboBox numBlocksCombo = new JComboBox<>(new String[]{"1","2","4","8","16","32","64","128","256","512","1024","2048"});
    public JComboBox blockSizeCombo = new JComboBox<>(new String[]{"1","2","4","8","16","32","64","128","256","512","1024","2048"});
    public JComboBox numSamplesCombo = new JComboBox<>(new String[]{"25","50","100","200","300","500","1000","2000","3000","5000","10000"});
    
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
            
            BenchmarkProfile profile = (BenchmarkProfile) profileCombo.getSelectedItem();
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
                Gui.mainFrame.loadBenchmarkConfig();
            }
        });
        
        typeCombo.addActionListener((ActionEvent evt) -> {
            if (typeCombo.hasFocus()) {
                Benchmark.BenchmarkType mode = (Benchmark.BenchmarkType) typeCombo.getSelectedItem();
                App.benchmarkType = mode;
                App.saveConfig();
                System.out.println("emulate custom: " + evt.paramString());
            }
        });
        
        numThreadsCombo.addActionListener((ActionEvent evt) -> {
            // NOTE: selecting a value from dropdown does not trigger the below
            if (numThreadsCombo.hasFocus()) {
                App.numOfThreads = Integer.parseInt((String) numThreadsCombo.getSelectedItem());
                App.saveConfig();
            }
        });
        
        orderCombo.addActionListener((ActionEvent evt) -> {
            if (orderCombo.hasFocus()) {
                App.blockSequence = (Benchmark.BlockSequence) orderCombo.getSelectedItem();
                App.saveConfig();
            }
        });
        
        numBlocksCombo.addActionListener((ActionEvent evt) -> {
            // NOTE: selecting a value from dropdown does not trigger the below
            if (numBlocksCombo.hasFocus()) {
                App.numOfBlocks = Integer.parseInt((String) numBlocksCombo.getSelectedItem());
                //sampleSizeLabel.setText(String.valueOf(App.targetMarkSizeKb()));
                //totalTxProgBar.setString(String.valueOf(App.targetTxSizeKb()));
                App.saveConfig();
            }
        });
        
        blockSizeCombo.addActionListener((ActionEvent evt) -> {
            // NOTE: selecting a value from dropdown does not trigger the below
            if (blockSizeCombo.hasFocus()) {
                App.blockSizeKb = Integer.parseInt((String) blockSizeCombo.getSelectedItem());
                //sampleSizeLabel.setText(String.valueOf(App.targetMarkSizeKb()));
                //totalTxProgBar.setString(String.valueOf(App.targetTxSizeKb()));
                App.saveConfig();
            }
        });
        
        numSamplesCombo.addActionListener((ActionEvent evt) -> {
            // NOTE: selecting a value from dropdown does not trigger the below
            if (numSamplesCombo.hasFocus()) {
                App.numOfSamples = Integer.parseInt((String) numSamplesCombo.getSelectedItem());
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