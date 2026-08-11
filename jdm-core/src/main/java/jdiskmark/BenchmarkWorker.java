package jdiskmark;
// constants
import static jdiskmark.Sample.Type.READ;
import static jdiskmark.Sample.Type.WRITE;
// global variables
import static jdiskmark.App.msg;
import static jdiskmark.App.dataDir;

import jakarta.persistence.EntityManager;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.metricus.jdm.ui.Tabs;

/**
 * Thread running the disk benchmarking. only one of these threads can run at
 * once.
 */
public class BenchmarkWorker extends SwingWorker<Benchmark, Sample> {
    private static final Logger LOG = Logger.getLogger(BenchmarkWorker.class.getName());
    /** Render mode snapshot — captured once when the worker is created. */
    private final RenderFrequencyMode renderMode = App.rmOption;

    // Buffers for non-PER_SAMPLE modes
    private final java.util.List<Sample> operationBuffer = new java.util.ArrayList<>();
    private final java.util.List<Sample> intervalBuffer  = new java.util.ArrayList<>();
    private long nextPublishTime = 0;

    BenchmarkRunner.BenchmarkListener listener = new BenchmarkRunner.BenchmarkListener() {
        @Override
        public void onSampleComplete(Sample s) {
            switch (renderMode) {
                case PER_SAMPLE -> publish(s);
                case PER_OPERATION -> {
                    synchronized (operationBuffer) { operationBuffer.add(s); }
                }
                case PER_100MS, PER_500MS, PER_1000MS -> {
                    long interval = renderMode.getIntervalMillis();
                    long now = System.currentTimeMillis();
                    synchronized (intervalBuffer) {
                        intervalBuffer.add(s);
                        if (now >= nextPublishTime) {
                            // flush all buffered samples
                            for (Sample buffered : intervalBuffer) { publish(buffered); }
                            intervalBuffer.clear();
                            nextPublishTime = now + interval;
                        }
                    }
                }
            }
        }

        @Override
        public void onProgressUpdate(long completed, long total) { setProgress((int) completed); }

        @Override
        public boolean isCancelled() { return BenchmarkWorker.this.isCancelled(); }

        @Override
        public void attemptCacheDrop() { Gui.dropCache(); }

        @Override
        public void onOperationComplete() {
            if (renderMode == RenderFrequencyMode.PER_OPERATION) {
                // Copy and clear the buffer under the lock, then render
                // synchronously on the EDT so I/O and graphing never overlap.
                List<Sample> toFlush;
                synchronized (operationBuffer) {
                    toFlush = new ArrayList<>(operationBuffer);
                    operationBuffer.clear();
                }
                try {
                    SwingUtilities.invokeAndWait(() -> {
                        for (Sample s : toFlush) {
                            switch (s.type) {
                                case WRITE -> Gui.addWriteSample(s);
                                case READ  -> Gui.addReadSample(s);
                            }
                        }
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (InvocationTargetException e) {
                    LOG.log(Level.WARNING, "Chart update failed", e);
                }
            }
        }
    };
    
    @Override
    protected Benchmark doInBackground() throws Exception {
        // Clear amber stale-highlights from any previous run's setting changes.
        // The new baseline will be App.benchmark.config once this run completes.
        Gui.clearAllStaleHighlights();

        // --- Event: benchmark started ---
        String startedMsg = String.format(
                "Benchmark started — %s | %s | %d samples × %d blocks × %d KB | %d thread(s) | drive: %s",
                App.benchmarkType,
                App.activeProfile + (App.profileModified ? "*" : ""),
                App.numOfSamples,
                App.numOfBlocks,
                App.blockSizeKb,
                App.numOfThreads,
                App.locationDir != null ? App.locationDir.getAbsolutePath() : "(none)");
        if (App.mode == App.Mode.GUI) {
            SwingUtilities.invokeLater(() -> msg(startedMsg));
        } else {
            msg(startedMsg);
        }

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
        Gui.lockSampleAxis(App.numOfSamples);

        BenchmarkRunner bRunner = new BenchmarkRunner(listener, App.getConfig());
        Benchmark benchmark = bRunner.execute();

        // Flush any samples still in the interval buffer for timed render modes.
        // PER_OPERATION is handled by onOperationComplete(); PER_SAMPLE needs no flush.
        if (renderMode == RenderFrequencyMode.PER_100MS
                || renderMode == RenderFrequencyMode.PER_500MS
                || renderMode == RenderFrequencyMode.PER_1000MS) {
            synchronized (intervalBuffer) {
                intervalBuffer.forEach(this::publish);
                intervalBuffer.clear();
            }
        }

        // --- Event: benchmark completed or cancelled ---
        final String completedMsg;
        if (isCancelled()) {
            completedMsg = "Benchmark cancelled.";
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
            completedMsg = result.toString();
        }
        if (App.mode == App.Mode.GUI) {
            SwingUtilities.invokeLater(() -> msg(completedMsg));
        } else {
            msg(completedMsg);
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
            try {
                em.getTransaction().begin();
                em.persist(benchmark);
                em.getTransaction().commit();
            } catch (Exception e) {
                Logger.getLogger(BenchmarkWorker.class.getName())
                      .log(Level.SEVERE, "Failed to save benchmark to DB", e);
                if (em.getTransaction().isActive()) {
                    em.getTransaction().rollback();
                }
            }
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
            // Normal cancellation path — no error to report
        } catch (ExecutionException e) {
            LOG.log(Level.SEVERE, "Benchmark failed", e.getCause());
            App.err("Benchmark failed: " + e.getCause().getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (App.autoRemoveData) {
            Util.deleteDirectory(dataDir);
        }
        Gui.unlockSampleAxis();
        App.state = App.State.IDLE_STATE;
        Gui.mainFrame.adjustSensitivity();
        // Switch to Benchmarks tab and select the new record for immediate visibility.
        Gui.selectBottomTab(Tabs.BOTTOM_BENCHMARKS);
        if (Gui.runPanel != null) Gui.runPanel.selectLastRow();
    }
}
