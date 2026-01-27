package jdiskmark;

import static jdiskmark.App.msg;

import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CLI version of the benchmark runner.
 * Uses BenchmarkRunner to perform the disk I/O while rendering a progress bar.
 */
public class BenchmarkCallable implements Callable<Benchmark> {
    private static final Logger logger = Logger.getLogger(BenchmarkCallable.class.getName());
    static final int CLI_BAR_LENGTH = 50;

    /**
     * Renders a standard CLI progress bar with a carriage return
     */
    private void drawProgressBar(int completed, int total) {
        float percent = (float) completed / total * 100f;
        int displayPercent = Math.min(100, (int) percent);
        int numChars = (int) Math.floor((double) displayPercent / 100 * CLI_BAR_LENGTH);
        
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < CLI_BAR_LENGTH; i++) {
            bar.append(i < numChars ? "#" : " ");
        }
        bar.append("]");
        
        System.out.printf("\rProgress: %s %3d%% (%d/%d units) ", bar.toString(), displayPercent, completed, total);
        System.out.flush();
    }
    
    // Implementation of the listener for CLI output
    private final BenchmarkRunner.BenchmarkListener listener = new BenchmarkRunner.BenchmarkListener() {
        @Override
        public void onSampleComplete(Sample s) {
            if (App.verbose) {
                System.out.println(String.format("\n%s Sample %d: %s MB/s", s.type, s.sampleNum, s.getBwMbSecDisplay()));
            }
        }

        @Override
        public void onProgressUpdate(long completed, long total) {
            drawProgressBar((int) completed, (int) total);
        }

        @Override
        public boolean isCancelled() {
            // CLI benchmarks usually run to completion unless the process is killed
            return false; 
        }

        @Override
        public void requestCacheDrop() {
            System.out.println("\nDropping OS caches...");
            Cli.dropCache();
        }
    };

    public BenchmarkCallable() {}

    @Override
    public Benchmark call() throws Exception {
        // 1. Profile Awareness: Apply "Quick Test" as the default CLI behavior if not specified
        if (App.activeProfile == null) {
            App.loadProfile(BenchmarkProfile.QUICK_TEST);
        }

        System.out.println(App.benchmarkType + " benchmark started...");
        long start = System.currentTimeMillis();

        if (App.verbose) {
            msg("*** starting new CLI benchmark thread");
            msg("Profile: " + App.activeProfile);
            msg("num samples: " + App.numOfSamples + ", num blks: " + App.numOfBlocks
                    + ", blk size (kb): " + App.blockSizeKb + ", blockSequence: "
                    + App.blockSequence);
        }

        // Standard Pre-benchmark cleanup
        if (App.autoReset) {
            App.resetTestData();
        }

        // Orchestration via BenchmarkRunner
        BenchmarkRunner bRunner = new BenchmarkRunner(listener, App.getConfig());
        Benchmark benchmark = bRunner.execute();

        // 4. Persistence & Export
        handlePostBenchmark(benchmark);

        long duration = System.currentTimeMillis() - start;
        System.out.println(); 
        System.out.println(App.benchmarkType + " benchmark finished after " + duration + "ms.");
        
        return benchmark;
    }

    private void handlePostBenchmark(Benchmark benchmark) {
        if (App.autoSave) {
            try {
                EntityManager em = EM.getEntityManager();
                em.getTransaction().begin();
                em.persist(benchmark);
                em.getTransaction().commit();
                App.benchmarks.put(benchmark.getStartTimeString(), benchmark);
                for (BenchmarkOperation o : benchmark.getOperations()) {
                    App.operations.put(o.getStartTimeString(), o);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to save benchmark to DB", e);
            }
        }

        if (App.exportPath != null) {
            try {
                JsonExporter.writeBenchmarkToJson(benchmark, App.exportPath.getAbsolutePath());
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "export error", ex);
            }
        }
        
        App.nextSampleNumber += App.numOfSamples;
    }
}