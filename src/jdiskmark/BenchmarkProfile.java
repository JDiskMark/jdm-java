package jdiskmark;

import java.util.List;
import jdiskmark.Benchmark.BenchmarkType;
import jdiskmark.Benchmark.BlockSequence;

/**
 * A named, pre-defined set of configuration parameters for a benchmark run.
 * Corresponds to a "Profile" in the GUI/CLI.
 */
public class BenchmarkProfile {
    
    // basic settings
    private String name;
    private BenchmarkType benchmarkType;
    private BlockSequence blockSequence;
    private int numThreads;       // The -T argument
    private int numSamples;       // The -n argument
    private int numBlocks = 1;    // The number of blocks per sample
    private int blockSizeKb;      // The size of a block in KB
    
    // advanced settings
    private boolean multiFile = true;      // Whether to use a single test file or multiple
    private boolean writeSyncEnable = false; // Whether to use synchronous write mode ("rwd")    
    
    // --- Static Predefined Profiles ---
    
    // --- 1. Max Sequential Speed (Peak Throughput) ---
    public static final BenchmarkProfile MAX_SEQUENTIAL_SPEED = new BenchmarkProfile(
        "Max Sequential", BenchmarkType.READ_WRITE, 
        BlockSequence.SEQUENTIAL, 1, 100, 200, 1024
    );

    // --- 2. High-Load Random (Q32T1 Proxy / Max IOPS) ---
    public static final BenchmarkProfile HIGH_LOAD_RANDOM_Q32T1 = new BenchmarkProfile(
        "Random 4K (Q32T1)", BenchmarkType.READ_WRITE, 
        BlockSequence.RANDOM, 32, 200, 100, 4
    );

    // --- 3. Low-Load Random (Q1T1 / System Responsiveness) ---
    public static final BenchmarkProfile LOW_LOAD_RANDOM_Q1T1 = new BenchmarkProfile(
        "Random 4K (Q1T1)", BenchmarkType.READ_WRITE, 
        BlockSequence.RANDOM, 1, 150, 50, 4
    );

    // --- 4. Max Write Stress (Endurance/Sustained Write Test) ---
    public static final BenchmarkProfile MAX_WRITE_STRESS = new BenchmarkProfile(
        "Max Write Stress", BenchmarkType.WRITE, 
        BlockSequence.SEQUENTIAL, 4, 250, 500, 512
    );

    // --- 5. Quick Functional Test (Fastest check) ---
    public static final BenchmarkProfile QUICK_TEST = new BenchmarkProfile(
        "Quick Test", BenchmarkType.READ_WRITE, 
        BlockSequence.SEQUENTIAL, 1, 50, 25, 64
    );
    
    // --- 6. Custom ---
    public static final BenchmarkProfile CUSTOM_TEST = new BenchmarkProfile(
        "Custom Test", BenchmarkType.READ_WRITE, 
        BlockSequence.SEQUENTIAL, 1, 1, 1, 1
    );
    
    // --- Constructor ---
    
    public BenchmarkProfile(String name, BenchmarkType benchmarkType,
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
    
    // --- Setters ---
    
    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Profile name cannot be empty.");
        }
        this.name = name;
    }

    public void setBenchmarkType(BenchmarkType benchmarkType) {
        if (benchmarkType == null) {
            throw new IllegalArgumentException("BenchmarkType cannot be null.");
        }
        this.benchmarkType = benchmarkType;
    }

    public void setBlockSequence(BlockSequence blockSequence) {
        if (blockSequence == null) {
            throw new IllegalArgumentException("BlockSequence cannot be null.");
        }
        this.blockSequence = blockSequence;
    }

    public void setNumThreads(int numThreads) {
        // Threads must be at least 1
        if (numThreads < 1) {
            throw new IllegalArgumentException("Number of threads must be 1 or greater.");
        }
        this.numThreads = numThreads;
    }

    public void setNumSamples(int numSamples) {
        // Samples must be at least 1 to generate data points
        if (numSamples < 1) {
            throw new IllegalArgumentException("Number of samples must be 1 or greater.");
        }
        this.numSamples = numSamples;
    }

    public void setNumBlocks(int numBlocks) {
        // Blocks per sample must be at least 1
        if (numBlocks < 1) {
            throw new IllegalArgumentException("Number of blocks per sample must be 1 or greater.");
        }
        this.numBlocks = numBlocks;
    }

    public void setBlockSizeKb(int blockSizeKb) {
        // Block size should be a positive, reasonable value (e.g., at least 1KB)
        if (blockSizeKb < 1) {
            throw new IllegalArgumentException("Block size in KB must be 1 or greater.");
        }
        this.blockSizeKb = blockSizeKb;
    }

    // --- Advanced Settings Setters ---

    public void setMultiFile(boolean multiFile) {
        this.multiFile = multiFile;
    }

    public void setWriteSyncEnable(boolean writeSyncEnable) {
        this.writeSyncEnable = writeSyncEnable;
    }
}