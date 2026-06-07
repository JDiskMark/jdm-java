package jdiskmark;
// constants
import static jdiskmark.Sample.Type.READ;
import static jdiskmark.Sample.Type.WRITE;
// global variables
import static jdiskmark.App.msg;
import static jdiskmark.App.dataDir;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingWorker;

/**
 * Thread running the disk benchmarking. only one of these threads can run at
 * once.
 */
public class BenchmarkWorker extends SwingWorker<Benchmark, Sample> {
    BenchmarkRunner.BenchmarkListener listener = new BenchmarkRunner.BenchmarkListener() {
        @Override
        public void onSampleComplete(Sample s) { publish(s); }

        @Override
        public void onProgressUpdate(long completed, long total) { setProgress((int) completed); }

        @Override
        public boolean isCancelled() { return BenchmarkWorker.this.isCancelled(); }

        @Override
        public void attemptCacheDrop() { Gui.dropCache(); }
    };
    
    @Override
    protected Benchmark doInBackground() throws Exception {

        // --- Event: benchmark started ---
        msg(String.format("Benchmark started — %s | %s | %d samples × %d blocks × %d KB | %d thread(s) | drive: %s",
                App.benchmarkType,
                App.activeProfile + (App.profileModified ? "*" : ""),
                App.numOfSamples,
                App.numOfBlocks,
                App.blockSizeKb,
                App.numOfThreads,
                App.locationDir != null ? App.locationDir.getAbsolutePath() : "(none)"));

        if (App.verbose) {
            msg("*** starting new worker thread");
            msg("Running readTest " + App.hasReadOperation() + "   writeTest " + App.hasWriteOperation());
            msg("num samples: " + App.numOfSamples + ", num blks: " + App.numOfBlocks
                    + ", blk size (kb): " + App.blockSizeKb + ", blockSequence: "
                    + App.blockSequence);
        }

        Gui.updateLegendAndAxis();

        if (App.autoReset == true) {
            App.resetTestData();
            Gui.resetBenchmarkData();
            Gui.updateLegendAndAxis();
        }

        BenchmarkRunner bRunner = new BenchmarkRunner(listener, App.getConfig());
        Benchmark benchmark = bRunner.execute();

        // --- Event: benchmark completed or cancelled ---
        if (isCancelled()) {
            msg("Benchmark cancelled.");
        } else {
            // Build a concise result line covering whichever operations ran.
            StringBuilder result = new StringBuilder("Benchmark completed");
            for (BenchmarkOperation op : benchmark.getOperations()) {
                switch (op.ioMode) {
                    case WRITE -> result.append(String.format(
                            " | Write avg=%.2f max=%.2f min=%.2f MB/s  IOPS=%d",
                            op.bwAvg, op.bwMax, op.bwMin, op.iops));
                    case READ -> result.append(String.format(
                            " | Read avg=%.2f max=%.2f min=%.2f MB/s  IOPS=%d",
                            op.bwAvg, op.bwMax, op.bwMin, op.iops));
                }
            }
            // Elapsed time
            if (benchmark.startTime != null && benchmark.endTime != null) {
                long elapsedSec = java.time.Duration.between(benchmark.startTime, benchmark.endTime).getSeconds();
                result.append(String.format(" | duration=%ds", elapsedSec));
            }
            msg(result.toString());
        }
        
        // update gui title
        Gui.chart.getTitle().setText(benchmark.getDriveInfoDisplay());
        Gui.chart.getTitle().setVisible(true);

        // store local app state
        App.benchmark = benchmark;
        App.benchmarks.put(benchmark.getStartTimeString(), benchmark);
        for (BenchmarkOperation o : benchmark.getOperations()) {
            App.operations.put(o.getStartTimeString(), o);
        }
        
        if (App.autoSave) {
            EntityManager em = EM.getEntityManager();
            em.getTransaction().begin();
            em.persist(benchmark);
            em.getTransaction().commit();
        }
        // #67 upload to community portal (in progress)
        // Run asynchronously so the benchmark result returns to the UI immediately
        // without blocking on the socket timeout + HTTP POST in Portal.upload().
        if (App.sharePortal) {
            CompletableFuture.runAsync(() -> Portal.upload(benchmark))
                .exceptionally(ex -> {
                    App.err("Portal upload error: " + ex.getMessage());
                    return null;
                });
        }
        Gui.runPanel.addRun(benchmark);
        App.nextSampleNumber += App.numOfSamples;
        return benchmark;
    }

    @Override
    protected void process(List<Sample> sampleList) {
        sampleList.stream().forEach((Sample s) -> {
            switch (s.type) {
                case WRITE -> Gui.addWriteSample(s);
                case READ -> Gui.addReadSample(s);
            }
        });
    }

    @Override
    protected void done() {
        try {
            get();
        } catch (CancellationException e) {
            // Normal cancellation path — cancellation event already logged in doInBackground
        } catch (ExecutionException e) {
            // --- Event: benchmark IO error ---
            Logger.getLogger(BenchmarkWorker.class.getName()).log(Level.SEVERE, "Benchmark failed", e.getCause());
            App.err("Benchmark error: " + e.getCause().getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (App.autoRemoveData) {
            Util.deleteDirectory(dataDir);
        }
        App.state = App.State.IDLE_STATE;
        Gui.mainFrame.adjustSensitivity();
    }
}
