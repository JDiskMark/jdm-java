package jdiskmark;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.nio.file.Path;
import jdiskmark.App.IoEngine;
import jdiskmark.App.SectorAlignment;

@Embeddable
public class BenchmarkConfig {
    // app version performing the benchmark
    @Column
    String appVersion;
    public String getAppVersion() { return appVersion; }
    
    @Column
    @Enumerated(EnumType.STRING)
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
    long blockSize = 0;
    public long getBlockSize() { return blockSize; }
    
    @Column
    int numSamples = 0;
    public int getNumSamples() { return numSamples; }
    
    @Column
    long txSize = 0;
    public long getTxSize() { return txSize; }
    
    @Column
    int numThreads = 1;
    public int getNumThreads() { return numThreads; }
    
    // --- I/O Engine Settings ---

    @Column
    @Enumerated(EnumType.STRING)
    IoEngine ioEngine;
    public IoEngine getIoEngine() { return ioEngine; }
    public void setIoEngine(IoEngine engine) { ioEngine = engine; }

    @Column
    Boolean directIoEnabled;
    public Boolean getDirectIoEnabled() { return directIoEnabled; }
    public void setDirectIoEnabled(Boolean enable) { directIoEnabled = enable; }

    @Column
    Boolean writeSyncEnabled;
    public Boolean getWriteSyncEnabled() { return writeSyncEnabled; }
    public void setWriteSyncEnabled(Boolean enable) { writeSyncEnabled = enable; }

    @Column
    @Enumerated(EnumType.STRING)
    SectorAlignment sectorAlignment;
    public SectorAlignment getSectorAlignment() { return sectorAlignment; }
    public void setSectorAlignment(SectorAlignment bytes) { sectorAlignment = bytes; }

    @Column
    Boolean multiFileEnabled;
    public Boolean getMultiFileEnabled() { return multiFileEnabled; }
    public void setMultiFileEnabled(Boolean enable) { multiFileEnabled = enable; }
    
    @Column
    String testDir;
    public String getTestDir() { return testDir; }
    public void setTestDir(String testDir) { this.testDir = testDir; }
    
    public BenchmarkConfig() {}
    
    public boolean hasReadOperation() {
        return benchmarkType == Benchmark.BenchmarkType.READ || benchmarkType == Benchmark.BenchmarkType.READ_WRITE;
    }

    public boolean hasWriteOperation() {
        return benchmarkType == Benchmark.BenchmarkType.WRITE || benchmarkType == Benchmark.BenchmarkType.READ_WRITE;
    }
}
