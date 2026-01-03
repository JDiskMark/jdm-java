package jdiskmark;

import java.util.List;
import jdiskmark.Benchmark.BenchmarkType;
import jdiskmark.Benchmark.BlockSequence;

/**
 * A named, pre-defined set of configuration parameters for a benchmark run.
 * Corresponds to a "Profile" in the GUI/CLI.
 */
public enum BenchmarkProfile {
    
    // --- 1. Max Sequential Speed (Peak Throughput) ---
    MAX_SEQUENTIAL_SPEED(
        "Max Sequential", BenchmarkType.READ_WRITE, 
        BlockSequence.SEQUENTIAL, 1, 100, 200, 1024
    ),

    // --- 2. High-Load Random (Q32T1 Proxy / Max IOPS) ---
    HIGH_LOAD_RANDOM_Q32T1(
        "Random 4K (Q32T1)", BenchmarkType.READ_WRITE, 
        BlockSequence.RANDOM, 32, 200, 100, 4
    ),

    // --- 3. Low-Load Random (Q1T1 / System Responsiveness) ---
    LOW_LOAD_RANDOM_Q1T1(
        "Random 4K (Q1T1)", BenchmarkType.READ_WRITE, 
        BlockSequence.RANDOM, 1, 150, 50, 4
    ),

    // --- 4. Max Write Stress (Endurance/Sustained Write Test) ---
    MAX_WRITE_STRESS(
        "Max Write Stress", BenchmarkType.WRITE, 
        BlockSequence.SEQUENTIAL, 4, 250, 500, 512
    ),

    // --- 5. Quick Functional Test (Fastest check) ---
    QUICK_TEST(
        "Quick Test", BenchmarkType.READ_WRITE, 
        BlockSequence.SEQUENTIAL, 1, 50, 25, 64
    ),
    
    // --- 6. Custom ---
    CUSTOM_TEST(
        "Custom Test", BenchmarkType.READ_WRITE, 
        BlockSequence.SEQUENTIAL, 1, 1, 1, 1
    );
    
    // basic settings
    final private String name;
    final private BenchmarkType benchmarkType;
    final private BlockSequence blockSequence;
    final private int numThreads;       // The -T argument
    final private int numSamples;       // The -n argument
    final private int numBlocks;        // The number of blocks per sample
    final private int blockSizeKb;      // The size of a block in KB
    
    // advanced settings
    final private boolean multiFile = true;        // Whether to use a single test file or multiple
    final private boolean writeSyncEnable = false; // Whether to use synchronous write mode ("rwd")    
    
    // --- Constructor ---
    
    BenchmarkProfile(String name, BenchmarkType benchmarkType,
            BlockSequence blockSequence, int numberThreads, int numSamples,
            int numBlocks, int blockSizeKB) {
        this.name = name;
        this.benchmarkType = benchmarkType;
        this.blockSequence = blockSequence;
        this.numThreads = numberThreads;
        this.numSamples = numSamples;
        this.numBlocks = numBlocks; // block per sample
        this.blockSizeKb = blockSizeKB;
    }

    @Override
    public String toString() { return name; }
    
    // --- Getters ---

    public static BenchmarkProfile[] getDefaults() {
        return List.of(
            QUICK_TEST,
            MAX_SEQUENTIAL_SPEED,
            HIGH_LOAD_RANDOM_Q32T1,
            LOW_LOAD_RANDOM_Q1T1,
            MAX_WRITE_STRESS,
            CUSTOM_TEST
        ).toArray(BenchmarkProfile[]::new);
    }

    public String getName() { return name; }
    public BenchmarkType getBenchmarkType() { return benchmarkType; }
    public BlockSequence getBlockSequence() { return blockSequence; }
    public int getNumThreads() { return numThreads; }
    public int getNumSamples() { return numSamples; }
    public int getNumBlocks() { return numBlocks; }
    public int getBlockSizeKb() { return blockSizeKb; }
    public boolean isMultiFile() { return multiFile; }
    public boolean isWriteSyncEnable() { return writeSyncEnable; }
}