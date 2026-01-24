package jdiskmark;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;
import jdiskmark.App.IoEngine;
import jdiskmark.App.SectorAlignment;
import jdiskmark.Benchmark.BenchmarkType;
import jdiskmark.Benchmark.BlockSequence;

/**
 * A named, pre-defined set of configuration parameters for a benchmark run.
 * Corresponds to a "Profile" in the GUI/CLI.
 */
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum BenchmarkProfile {
    
    // --- 1. Quick Functional Test (Fastest check) ---
    QUICK_TEST(
            "Quick Test", 
            BenchmarkType.READ_WRITE, 
            BlockSequence.SEQUENTIAL,
            1,  // threads
            50, // samples
            25, // blocks
            1024, // blk size kb
            IoEngine.LEGACY, // jdk io
            false, // direct io
            false, // writeSync
            SectorAlignment.NONE,
            false // multiFile
    ),
    
    // --- 2. Max Sequential Speed (Peak Throughput) ---
    MAX_SEQUENTIAL_SPEED(
            "Max Sequential Speed", 
            BenchmarkType.READ_WRITE, 
            BlockSequence.SEQUENTIAL, 
            1,   // threads
            100, // samples
            200, // blocks
            1024, // blk size kb
            IoEngine.MODERN, // jdk io
            true, // direct io
            false,// writeSync
            SectorAlignment.ALIGN_4K,
            false // multiFile
    ),

    // --- 3. High-Load Random (T32 Proxy / Max IOPS) ---
    HIGH_LOAD_RANDOM_T32(
            "Random 4K (T32)", 
            BenchmarkType.READ_WRITE, 
            BlockSequence.RANDOM, 
            32,  // threads 
            200, // samples
            100, // blocks
            4, // blk size kb
            IoEngine.MODERN, // jdk io
            true, // direct io
            false,// writeSync
            SectorAlignment.ALIGN_4K,
            true // multiFile
    ),

    // --- 4. Low-Load Random (T1 / System Responsiveness) ---
    LOW_LOAD_RANDOM_T1(
            "Random 4K (T1)", 
            BenchmarkType.READ_WRITE, 
            BlockSequence.RANDOM, 
            1,   // thread
            150, // samples
            50,  // blocks
            4, // blk size kb
            IoEngine.LEGACY, // jdk io
            false, // direct io
            false, // writeSync
            SectorAlignment.NONE,
            false // multiFile
    ),

    // --- 5. Max Write Stress (Endurance/Sustained Write Test) ---
    MAX_WRITE_STRESS(
            "Max Write Stress (T4)", 
            BenchmarkType.WRITE, 
            BlockSequence.SEQUENTIAL, 
            4,   // thread 
            250, // samples
            500, // blocks
            512, // blk size kb
            IoEngine.MODERN, // jdk io
            true, // direct io
            true, // writeSync
            SectorAlignment.ALIGN_4K,
            true // multiFile
    ),

    // --- 6. Custom (option indicator, not actual profile) ---
    CUSTOM_TEST(
        "Custom Test", BenchmarkType.READ_WRITE, 
        BlockSequence.SEQUENTIAL, 1, 1, 1, 1,
        IoEngine.LEGACY, false, false, SectorAlignment.NONE, false
    );
    
    // identifiers
    final String symbol;
    final String name;
    final BenchmarkType benchmarkType;
    
    // basic settings (define workload)
    final BlockSequence blockSequence;
    final int numThreads;       // The -T argument
    final int numSamples;       // The -n argument
    final int numBlocks;        // The number of blocks per sample
    final int blockSizeKb;      // The size of a block in KB
    
    // advanced settings (execution options)
    final IoEngine ioEngine;    // The I/O engine to use
    final boolean directEnable; // skip page cache
    final boolean writeSyncEnable; // Whether to use synchronous write mode ("rwd")    
    final SectorAlignment sectorAlignment;
    final boolean multiFile;    // Whether to use a single test file or multiple

    // --- Constructor ---
    
    BenchmarkProfile(String name, BenchmarkType benchmarkType,
            BlockSequence blockSequence, int numberThreads, int numSamples,
            int numBlocks, int blockSizeKB, 
            IoEngine ioEngine, boolean directEnable, boolean writeSyncEnable,
            SectorAlignment alignment, boolean multiFile) {
        this.symbol = this.name();
        this.name = name;
        this.benchmarkType = benchmarkType;
        this.blockSequence = blockSequence;
        this.numThreads = numberThreads;
        this.numSamples = numSamples;
        this.numBlocks = numBlocks; // block per sample
        this.blockSizeKb = blockSizeKB;
        this.ioEngine = ioEngine;
        this.directEnable = directEnable;
        this.writeSyncEnable = writeSyncEnable;
        this.sectorAlignment = alignment;
        this.multiFile = multiFile;
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

    // identity
    public String getSymbol() { return symbol; }
    public String getName() { return name; }
    public BenchmarkType getBenchmarkType() { return benchmarkType; }
    // data definition
    public BlockSequence getBlockSequence() { return blockSequence; }
    public int getNumThreads() { return numThreads; }
    public int getNumSamples() { return numSamples; }
    public int getNumBlocks() { return numBlocks; }
    public int getBlockSizeKb() { return blockSizeKb; }
    // io options
    public IoEngine getIoEngine() { return ioEngine; }
    public boolean isDirectEnable() { return directEnable; }
    public boolean isWriteSyncEnable() { return writeSyncEnable; }
    public SectorAlignment getSectorAlignment() { return sectorAlignment; }
    public boolean isMultiFile() { return multiFile; }
}