package jdiskmark;

import java.util.List;
import jdiskmark.Benchmark.BenchmarkType;
import jdiskmark.Benchmark.BlockSequence;

/**
 * A named, pre-defined set of configuration parameters for a benchmark run.
 * Corresponds to a "Profile" in the GUI/CLI.
 */
public enum BenchmarkProfile {
    
    // --- 1. Quick Functional Test (Fastest check) ---
    QUICK_TEST(
            "Quick Test", 
            BenchmarkType.READ_WRITE, 
            BlockSequence.SEQUENTIAL,
            1,  // threads
            50, // samples
            25, // blocks
            1024  // block size
    ),
    
    // --- 2. Max Sequential Speed (Peak Throughput) ---
    MAX_SEQUENTIAL_SPEED(
            "Max Sequential Speed", 
            BenchmarkType.READ_WRITE, 
            BlockSequence.SEQUENTIAL, 
            1,   // threads
            100, // samples
            200, // blocks
            1024 // blk size kb
    ),

    // --- 3. High-Load Random (T32 Proxy / Max IOPS) ---
    HIGH_LOAD_RANDOM_T32(
            "Random 4K (T32)", 
            BenchmarkType.READ_WRITE, 
            BlockSequence.RANDOM, 
            32,  // threads 
            200, // samples
            100, // blocks
            4    // blk size kb
    ),

    // --- 4. Low-Load Random (T1 / System Responsiveness) ---
    LOW_LOAD_RANDOM_T1(
            "Random 4K (T1)", 
            BenchmarkType.READ_WRITE, 
            BlockSequence.RANDOM, 
            1,   // thread
            150, // samples
            50,  // blocks
            4    // blk size kb
    ),

    // --- 5. Max Write Stress (Endurance/Sustained Write Test) ---
    MAX_WRITE_STRESS(
            "Max Write Stress (T4)", 
            BenchmarkType.WRITE, 
            BlockSequence.SEQUENTIAL, 
            4,   // thread 
            250, // samples
            500, // blocks
            512  // blk size kb
    ),

    // --- 6. Custom (option indicator, not actual profile) ---
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
        return List.of(QUICK_TEST,
            MAX_SEQUENTIAL_SPEED,
            HIGH_LOAD_RANDOM_T32,
            LOW_LOAD_RANDOM_T1,
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