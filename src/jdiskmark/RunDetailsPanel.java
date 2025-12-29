package jdiskmark;

import java.awt.GridLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;

public class RunDetailsPanel extends JPanel {

    private final JLabel modeLabel = new JLabel("-");
    private final JLabel samplesLabel = new JLabel("-");
    private final JLabel blocksLabel = new JLabel("-");
    private final JLabel threadsLabel = new JLabel("-");
    private final JLabel avgLabel = new JLabel("-");
    private final JLabel minMaxLabel = new JLabel("-");
    private final JLabel accLabel = new JLabel("-");

    // --- NEW purge metadata labels ---
    private final JLabel purgeMethodLabel = new JLabel("-");
    private final JLabel purgeSizeLabel = new JLabel("-");
    private final JLabel purgeDurationLabel = new JLabel("-");

    public RunDetailsPanel() {
        setLayout(new GridLayout(0, 2));
        setBorder(new TitledBorder("Run Details"));

        add(new JLabel("Mode:")); add(modeLabel);
        add(new JLabel("Samples:")); add(samplesLabel);
        add(new JLabel("Blocks:")); add(blocksLabel);
        add(new JLabel("Threads:")); add(threadsLabel);
        add(new JLabel("Avg Speed (MB/s):")); add(avgLabel);
        add(new JLabel("Min/Max (MB/s):")); add(minMaxLabel);
        add(new JLabel("Access Time (ms):")); add(accLabel);

        // --- PURGE METADATA ---
        add(new JLabel("Purge Method:")); add(purgeMethodLabel);
        add(new JLabel("Purge Size (MB):")); add(purgeSizeLabel);
        add(new JLabel("Purge Duration (ms):")); add(purgeDurationLabel);
    }

    /**
    * Populate details for a selected benchmark operation.
    *
    * @param op the BenchmarkOperation to load into the panel
    */
    public void load(BenchmarkOperation op) {
        if (op == null) return;

        Benchmark b = op.getBenchmark();

        modeLabel.setText(op.getModeDisplay());
        samplesLabel.setText(String.valueOf(op.numSamples));
        blocksLabel.setText(op.getBlocksDisplay());
        threadsLabel.setText(String.valueOf(op.numThreads));
        avgLabel.setText(String.valueOf(op.bwAvg));
        minMaxLabel.setText(op.getBwMinMaxDisplay());
        accLabel.setText(op.getAccTimeDisplay());

        purgeMethodLabel.setText(b.cachePurgeMethod.toString());
        purgeSizeLabel.setText(String.valueOf(b.cachePurgeSizeBytes / (1024 * 1024)));
        purgeDurationLabel.setText(String.valueOf(b.cachePurgeDurationMs));
    }
}
