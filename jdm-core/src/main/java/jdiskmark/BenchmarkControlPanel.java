package jdiskmark;

import com.formdev.flatlaf.FlatLightLaf;
import java.awt.Color;
import java.awt.Font;
import net.miginfocom.swing.MigLayout;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import static jdiskmark.MainFrame.DF;
import org.metricus.jdm.ui.ButtonStyles;

public class BenchmarkControlPanel extends JPanel {

    final Font HEADER_FONT = new JLabel().getFont().deriveFont(Font.BOLD);
    final Integer[] THREAD_OPTIONS = {1,2,4,8,16,32};
    final Integer[] NUM_BLOCK_OPTIONS = {1,2,4,8,16,32,64,128,256,512,1024,2048};
    final Integer[] BLOCK_SIZES = {1,2,4,8,16,32,64,128,256,512,1024,2048};
    final Integer[] NUM_SAMPLE_OPTIONS = {25,50,100,200,300,500,1000,2000,3000,5000,10000};
    
    JLabel profileLabel     = new JLabel("Profile");
    JLabel typeLabel        = new JLabel("Type");
    JLabel numThreadsLabel  = new JLabel("Number Threads");
    JLabel orderLabel       = new JLabel("Block Order");
    JLabel numBlocksLabel   = new JLabel("Blocks / Sample");
    JLabel blockSizeLabel   = new JLabel("Block Size (KB)");
    JLabel numSamplesLabel  = new JLabel("Number Samples");

    /** All row labels — used to bulk-clear amber highlights. */
    private final java.util.List<JLabel> rowLabels = java.util.List.of(
            profileLabel, typeLabel, numThreadsLabel, orderLabel,
            numBlocksLabel, blockSizeLabel, numSamplesLabel);
    
    public JComboBox<BenchmarkProfile> profileCombo = new JComboBox<>(BenchmarkProfile.getDefaults());
    public JComboBox<Benchmark.BenchmarkType> typeCombo = new JComboBox<>(Benchmark.BenchmarkType.values());
    public JComboBox<Integer> numThreadsCombo = new JComboBox<>(THREAD_OPTIONS);
    public JComboBox<Benchmark.BlockSequence> orderCombo = new JComboBox<>(Benchmark.BlockSequence.values());
    public JComboBox<Integer> numBlocksCombo = new JComboBox<>(NUM_BLOCK_OPTIONS);
    public JComboBox<Integer> blockSizeCombo = new JComboBox<>(BLOCK_SIZES);
    public JComboBox<Integer> numSamplesCombo = new JComboBox<>(NUM_SAMPLE_OPTIONS);
    
    public JButton startButton = new JButton("Start");
    
    public JLabel wAvgLabel = new JLabel("- -");
    public JLabel wAccessLabel = new JLabel("- -");
    public JLabel wIopsLabel = new JLabel("- -");
    public JLabel rAvgLabel = new JLabel("- -");
    public JLabel rAccessLabel = new JLabel("- -");
    public JLabel rIopsLabel = new JLabel("- -");
    

    public BenchmarkControlPanel() {
        initComponents();
        
        // TODO: review if needed???
        configChangeDetection();
        
        // locks down the preferred size to its initialized sized
        setPreferredSize(getPreferredSize());
        
        // configure combo action listeners
        
        profileCombo.addActionListener((ActionEvent evt) -> {

            // only run when interacted with
            if (!profileCombo.hasFocus()) { return; }
            
            BenchmarkProfile profile = (BenchmarkProfile)profileCombo.getSelectedItem();
            
            if (profile == null) return;
            
            App.loadProfile(profile);
            
            // only if initialized, calls our refresh
            if (Gui.mainFrame != null) {
                Gui.mainFrame.syncFromModel();
            }
            
            showSettingsDrift();
        });
        
        typeCombo.addActionListener((ActionEvent evt) -> {
            if (typeCombo.hasFocus()) {
                App.benchmarkType = (Benchmark.BenchmarkType)typeCombo.getSelectedItem();
                showSettingsDrift();
                App.saveConfig();
            }
        });
        
        numThreadsCombo.addActionListener((ActionEvent evt) -> {
            Object sel = numThreadsCombo.getSelectedItem();
            if (sel == null) return;
            App.numOfThreads = (Integer) sel;
            showSettingsDrift();
            App.saveConfig();
        });
        
        orderCombo.addActionListener((ActionEvent evt) -> {
            Object selected = orderCombo.getSelectedItem();
            if (selected != null) {
                App.blockSequence = (Benchmark.BlockSequence) selected;
                showSettingsDrift();
                App.saveConfig();
            }
        });
        
        numBlocksCombo.addActionListener((ActionEvent evt) -> {
            Object selected = numBlocksCombo.getSelectedItem();
            if (selected != null) {
                App.numOfBlocks = (Integer) selected;
                showSettingsDrift();
                Gui.updateProgress();
                App.saveConfig();
            }
        });
        
        blockSizeCombo.addActionListener((ActionEvent evt) -> {
            Object selected = blockSizeCombo.getSelectedItem();
            if (selected != null) {
                App.blockSizeKb = (Integer) selected;
                Gui.updateProgress();
                showSettingsDrift();
                App.saveConfig();
            }
        });

        numSamplesCombo.addActionListener((ActionEvent evt) -> {
            Object selected = numSamplesCombo.getSelectedItem();
            if (selected != null) {
                App.numOfSamples = (Integer) selected;
                Gui.updateProgress();
                showSettingsDrift();
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
        
        // editable combos are a workaround to loading non selectable values
        // to use this need to add input guard against non numerics
//        numThreadsCombo.setEditable(true);
//        numBlocksCombo.setEditable(true);
//        blockSizeCombo.setEditable(true);
//        numSamplesCombo.setEditable(true);
        
        // 3 column layout framework
        setLayout(new MigLayout("insets 0 5 0 5, fillx, wrap 3", "[30%][27%][43%]", "[]10[]"));

        // Profile
        add(profileLabel, "align left");
        add(profileCombo, "span 2, growx");

        // Type
        add(typeLabel, "align left");
        add(typeCombo, "span 2, growx");

        // --- Bottom Inputs Section (roughly 2/3 - 1/3) ---

        // Number Threads
        add(numThreadsLabel, "span 2, align left");
        add(numThreadsCombo, "span 1, growx");
        // Block Order
        add(orderLabel, "span 2, align left");
        add(orderCombo, "span 1, growx");
        // Blocks / Sample
        add(numBlocksLabel, "span 2, align left");
        add(numBlocksCombo, "span 1, growx");
        // Block Size
        add(blockSizeLabel, "span 2, align left");
        add(blockSizeCombo, "span 1, growx");
        // No. Samples
        add(numSamplesLabel, "span 2, align left");
        add(numSamplesCombo, "span 1, growx");

        // summary info
        wAvgLabel.setHorizontalAlignment(SwingConstants.CENTER);
        wAccessLabel.setHorizontalAlignment(SwingConstants.CENTER);
        wIopsLabel.setHorizontalAlignment(SwingConstants.CENTER);
        rAccessLabel.setHorizontalAlignment(SwingConstants.CENTER);
        rAvgLabel.setHorizontalAlignment(SwingConstants.CENTER);
        rIopsLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // --- Start Button ---
        // Style resolved at theme-apply time; seed it here with the default.
        // Gui.applyStartButtonStyle() will override this on every theme switch.
        startButton.putClientProperty("FlatLaf.style", ButtonStyles.DEFAULT_START);
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
    
    /**
     * Set profile to custom if a setting has been modified
     */
    private void configChangeDetection() {
        ActionListener changeListener = e -> {
            if (((JComboBox<?>)e.getSource()).getSelectedItem() == null) return;
            App.profileModified = true;
        };

        // Attach to all combos
        profileCombo.addActionListener(changeListener);
        typeCombo.addActionListener(changeListener);
        orderCombo.addActionListener(changeListener);
        numSamplesCombo.addActionListener(changeListener);
        numBlocksCombo.addActionListener(changeListener);
        blockSizeCombo.addActionListener(changeListener);
        numThreadsCombo.addActionListener(changeListener);
    }
    
    public void refreshSettings() {

        // TODO: disable profile change for now since we are doing loaded benchmark
        // settings changes which use the last run config instead and logic might
        // be confusing.
        // if (App.profileModified) {
        //     Color accent = UIManager.getColor("Component.accentColor");
        //     if (accent == null) {
        //         accent = (Gui.theme == Gui.Theme.LIGHT)
        //                  ? new Color(0, 51, 153) // dark blue
        //                  : new Color(102, 178, 255); // light blue
        //     }
        //     profileLabel.setForeground(accent);
        // } else {
        //     profileLabel.setForeground(UIManager.getColor("Label.foreground"));
        // }

        profileCombo.setSelectedItem(App.activeProfile);
        typeCombo.setSelectedItem(App.benchmarkType);
        numThreadsCombo.setSelectedItem(App.numOfThreads);
        orderCombo.setSelectedItem(App.blockSequence);
        numBlocksCombo.setSelectedItem(App.numOfBlocks);
        blockSizeCombo.setSelectedItem(App.blockSizeKb);
        numSamplesCombo.setSelectedItem(App.numOfSamples);

        showSettingsDrift();
    }

    /**
     * Resets all row label foregrounds to the default LAF color.
     * Called at the start of each run via {@link Gui#snapshotLastRunConfig()}.
     */
    public void clearRowHighlights() {
        Color defaultFg = UIManager.getColor("Label.foreground");
        for (JLabel lbl : rowLabels) {
            lbl.setForeground(defaultFg);
            lbl.setFont(lbl.getFont().deriveFont(Font.PLAIN));
        }
    }

    /**
     * Compares each control-panel row against lastRunConfig and colors the
     * row label bold-amber if its value has changed since the last run.
     */
    public void showSettingsDrift() {
        BenchmarkConfig lr = (App.benchmark != null) ? App.benchmark.config : null;
        if (lr == null) return; // no run yet
        Color amber     = Gui.BADGE_STALE_BG;
        Color defaultFg = UIManager.getColor("Label.foreground");
        Font  boldFont   = typeLabel.getFont().deriveFont(Font.BOLD);
        Font  normalFont = typeLabel.getFont().deriveFont(Font.PLAIN);

        boolean anyStale = false;
        anyStale |= setRowStaleReturn(profileLabel,    App.activeProfile != lr.getProfile(),                     amber, defaultFg, boldFont, normalFont);
        anyStale |= setRowStaleReturn(typeLabel,       App.benchmarkType != lr.benchmarkType,                     amber, defaultFg, boldFont, normalFont);
        anyStale |= setRowStaleReturn(numThreadsLabel, App.numOfThreads  != lr.numThreads,                        amber, defaultFg, boldFont, normalFont);
        anyStale |= setRowStaleReturn(orderLabel,      App.blockSequence != lr.blockOrder,                        amber, defaultFg, boldFont, normalFont);
        anyStale |= setRowStaleReturn(numBlocksLabel,  App.numOfBlocks   != lr.numBlocks,                         amber, defaultFg, boldFont, normalFont);
        anyStale |= setRowStaleReturn(blockSizeLabel,  App.blockSizeKb   != (int)(lr.blockSize / App.KILOBYTE),   amber, defaultFg, boldFont, normalFont);
        anyStale |= setRowStaleReturn(numSamplesLabel, App.numOfSamples  != lr.numSamples,                        amber, defaultFg, boldFont, normalFont);
        // profile label keeps its existing modified-accent logic; skip here

        // Subtitle: combine row-level staleness with badge-level staleness
        Gui.setChartModifiedIndicator(anyStale || Gui.isAnyBadgeStale());
    }

    private boolean setRowStaleReturn(JLabel label, boolean stale,
                                      Color amber, Color defaultFg, Font bold, Font normal) {
        label.setForeground(stale ? amber : defaultFg);
        label.setFont(stale ? bold : normal);
        return stale;
    }
    
    public void refreshWriteMetrics() {
        String value;
        value = App.wAvg == -1 ? "- -" : DF.format(App.wAvg);
        wAvgLabel.setText(value);
        value = App.wAcc == -1 ? "- -" : DF.format(App.wAcc);
        wAccessLabel.setText(value);
        value = App.wIops == -1 ? "- -" : String.valueOf(App.wIops);
        wIopsLabel.setText(value);
    }
    
    public void refreshReadMetrics() {
        String value;
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
        App.benchmarkType = Optional.ofNullable(typeCombo.getSelectedItem())
                .map(Benchmark.BenchmarkType.class::cast)
                .orElse(App.benchmarkType);
        App.blockSequence = Optional.ofNullable(orderCombo.getSelectedItem())
                .map(Benchmark.BlockSequence.class::cast)
                .orElse(App.blockSequence);
        App.numOfSamples = Optional.ofNullable(numSamplesCombo.getSelectedItem())
                .map(Integer.class::cast)
                .orElse(App.numOfSamples);
        App.numOfBlocks = Optional.ofNullable(numBlocksCombo.getSelectedItem())
                .map(Integer.class::cast)
                .orElse(App.numOfBlocks);
        App.blockSizeKb = Optional.ofNullable(blockSizeCombo.getSelectedItem())
                .map(Integer.class::cast)
                .orElse(App.blockSizeKb);
        App.numOfThreads = Optional.ofNullable(numThreadsCombo.getSelectedItem())
                .map(Integer.class::cast)
                .orElse(App.numOfThreads);
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
