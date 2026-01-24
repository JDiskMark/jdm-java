package jdiskmark;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class BenchmarkConfig {
    // app version performing the benchmark
    @Column
    String appVersion;
    public String getAppVersion() { return appVersion; }
    
    @Column
    BenchmarkProfile profile;
    public BenchmarkProfile getProfile() { return profile; }
    
    // benchmark parameters
    @Column
    Benchmark.BenchmarkType benchmarkType;
    public Benchmark.BenchmarkType getBenchmarkType() { return benchmarkType; }
    
    @Column
    Benchmark.BlockSequence blockOrder;
    public Benchmark.BlockSequence getBlockOrder() { return blockOrder; }
    
    @Column
    int numBlocks = 0;
    public int getNumBlocks() { return numBlocks; }
    
    @Column
    int blockSize = 0;
    public int getBlockSize() { return blockSize; }
    
    @Column
    int numSamples = 0;
    public int getNumSamples() { return numSamples; }
    
    @Column
    long txSize = 0;
    public long getTxSize() { return txSize; }
    
    @Column
    int numThreads = 1;
    public int getNumThreads() { return numThreads; }
    
    // NEW: whether write-sync was enabled for this run (only meaningful for WRITE; may be null for READ)
    @Column
    Boolean writeSyncEnabled;
    public Boolean getWriteSyncEnabled() { return writeSyncEnabled; }
    
    public BenchmarkConfig() {
        appVersion = App.VERSION;
    }
}
