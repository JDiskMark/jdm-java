package jdiskmark;

import static jdiskmark.App.msg;

import jakarta.persistence.EntityManager;
import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingWorker;

public class BatchWorker extends SwingWorker<BatchResult, BatchEvent> {
    private static final Logger LOG = Logger.getLogger(BatchWorker.class.getName());

    private final BatchConfig config;
    private final File originalLocationDir;
    private final File originalDataDir;

    public BatchWorker(BatchConfig config) {
        this.config = config;
        this.originalLocationDir = App.locationDir;
        this.originalDataDir = App.dataDir;
    }

    @Override
    protected BatchResult doInBackground() throws Exception {
        UUID batchId = UUID.randomUUID();
        BatchResult batchResult = new BatchResult(config.profiles(), batchId);
        List<File> drives = config.drives();
        List<BenchmarkProfile> profiles = config.profiles();
        int totalRuns = config.totalRuns();
        int runIndex = 0;
        // Track when each drive last finished a benchmark (for smart cooldown)
        java.util.Map<File, Long> driveLastFinish = new java.util.HashMap<>();

        // Profile-first: run each profile across all drives before the next profile.
        // Drives cool naturally while other drives are benchmarked.
        for (int p = 0; p < profiles.size() && !isCancelled(); p++) {
            BenchmarkProfile profile = profiles.get(p);

            for (int d = 0; d < drives.size() && !isCancelled(); d++) {
                File drive = drives.get(d);
                String driveModel = Util.getDriveModel(drive);
                if (driveModel == null || driveModel.isBlank()) driveModel = drive.getAbsolutePath();

                // Smart cooldown: only wait if this drive hasn't cooled long enough
                if (config.cooldownSeconds() > 0 && driveLastFinish.containsKey(drive)) {
                    long elapsed = (System.currentTimeMillis() - driveLastFinish.get(drive)) / 1000;
                    int remaining = config.cooldownSeconds() - (int) elapsed;
                    if (remaining > 0 && !isCancelled()) {
                        publish(new BatchEvent.CooldownStarted(runIndex, remaining));
                        for (int s = remaining; s > 0 && !isCancelled(); s--) {
                            publish(new BatchEvent.CooldownTick(s));
                            Thread.sleep(1000);
                        }
                    }
                }

                publish(new BatchEvent.RunStarted(runIndex, totalRuns,
                        drive.getAbsolutePath(), driveModel, profile));
                msg("Batch: " + driveModel + " — " + profile.getName()
                        + " (" + (runIndex + 1) + "/" + totalRuns + ")");

                BatchResult.RunResult result = runSingle(runIndex, batchId, drive, driveModel, profile, false);

                if (!result.isSuccess() && !isCancelled()) {
                    publish(new BatchEvent.RunRetrying(runIndex, result.errorMessage(), profile));
                    msg("Batch: retrying " + driveModel + " — " + profile.getName());
                    result = runSingle(runIndex, batchId, drive, driveModel, profile, true);
                }

                driveLastFinish.put(drive, System.currentTimeMillis());
                batchResult.addResult(result);

                if (result.isSuccess()) {
                    publish(new BatchEvent.RunCompleted(runIndex, result.benchmark(), profile));
                    msg("Batch: completed " + driveModel + " — " + profile.getName());
                } else {
                    publish(new BatchEvent.RunSkipped(runIndex, result.errorMessage(), profile));
                    msg("Batch: skipped " + driveModel + " — " + profile.getName()
                            + " — " + result.errorMessage());
                }

                runIndex++;
            }
        }

        batchResult.recordEndTime();

        if (isCancelled()) {
            publish(new BatchEvent.BatchCancelled(batchResult));
        } else {
            if (App.sharePortal) {
                for (BatchResult.RunResult rr : batchResult.getSuccessfulResults()) {
                    CompletableFuture.runAsync(() -> Portal.upload(rr.benchmark()))
                        .exceptionally(ex -> {
                            App.err("Batch portal upload error: " + ex.getMessage());
                            return null;
                        });
                }
            }
            publish(new BatchEvent.BatchCompleted(batchResult));
        }
        return batchResult;
    }

    private BatchResult.RunResult runSingle(int runIndex, UUID batchId, File drive, String driveModel,
                                             BenchmarkProfile profile, boolean isRetry) {
        try {
            config.applyProfileToApp(profile, drive);

            File dataDir = App.dataDir;
            if (dataDir.exists()) {
                Util.deleteDirectory(dataDir);
            }
            dataDir.mkdirs();

            if (!DriveChecker.validateTargetDirectory(drive, false)) {
                return new BatchResult.RunResult(drive, driveModel, profile, null,
                        isRetry ? BatchResult.DriveStatus.RETRIED_THEN_SKIPPED
                                : BatchResult.DriveStatus.SKIPPED,
                        "Target directory validation failed");
            }

            if (!DriveChecker.checkDiskSpace(drive)) {
                return new BatchResult.RunResult(drive, driveModel, profile, null,
                        isRetry ? BatchResult.DriveStatus.RETRIED_THEN_SKIPPED
                                : BatchResult.DriveStatus.SKIPPED,
                        "Insufficient disk space");
            }

            App.nextSampleNumber = 1;
            App.resetTestData();

            final int idx = runIndex;
            BenchmarkRunner.BenchmarkListener listener = new BenchmarkRunner.BenchmarkListener() {
                @Override public void onSampleComplete(Sample sample) {}

                @Override
                public void onProgressUpdate(long completed, long total) {
                    publish(new BatchEvent.RunProgress(idx, (int) completed));
                }

                @Override
                public boolean isCancelled() {
                    return BatchWorker.this.isCancelled();
                }

                @Override
                public void attemptCacheDrop() {
                    Gui.dropCache();
                }
            };

            BenchmarkRunner bRunner = new BenchmarkRunner(listener, App.getConfig());
            Benchmark benchmark = bRunner.execute();
            benchmark.recordEndTime();
            benchmark.setBatchId(batchId);

            if (App.autoSave) {
                EntityManager em = EM.getEntityManager();
                try {
                    em.getTransaction().begin();
                    em.persist(benchmark);
                    em.getTransaction().commit();
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, "Failed to save batch benchmark to DB", e);
                    if (em.getTransaction().isActive()) em.getTransaction().rollback();
                }
            }

            final Benchmark finalBenchmark = benchmark;
            javax.swing.SwingUtilities.invokeLater(() -> {
                App.benchmarks.put(finalBenchmark.getStartTimeString(), finalBenchmark);
                for (BenchmarkOperation o : finalBenchmark.getOperations()) {
                    App.operations.put(o.getStartTimeString(), o);
                }
                if (Gui.runPanel != null) Gui.runPanel.addRun(finalBenchmark);
            });

            return new BatchResult.RunResult(drive, driveModel, profile, benchmark,
                    isRetry ? BatchResult.DriveStatus.RETRIED_THEN_COMPLETED
                            : BatchResult.DriveStatus.COMPLETED,
                    null);

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Batch benchmark failed for " + drive + " / " + profile.getName(), e);
            String err = (e.getMessage() == null || e.getMessage().isBlank()) ? e.toString() : e.getMessage();
            return new BatchResult.RunResult(drive, driveModel, profile, null,
                    isRetry ? BatchResult.DriveStatus.RETRIED_THEN_SKIPPED
                            : BatchResult.DriveStatus.SKIPPED,
                    err);
        } finally {
            File dataDir = new File(drive.getAbsolutePath() + File.separator + App.DATADIRNAME);
            if (dataDir.exists()) {
                Util.deleteDirectory(dataDir);
            }
        }
    }

    @Override
    protected void process(List<BatchEvent> events) {
        for (BatchEvent event : events) {
            if (eventListener != null) eventListener.onEvent(event);
        }
    }

    @Override
    protected void done() {
        App.locationDir = originalLocationDir;
        App.dataDir = originalDataDir;
        App.batchRunning = false;
        if (Gui.mainFrame != null) {
            Gui.mainFrame.adjustSensitivity();
        }
        // Refresh the batch reports bottom tab
        if (Gui.batchReportsPanel != null) {
            Gui.batchReportsPanel.refresh();
        }
    }

    @FunctionalInterface
    public interface EventListener {
        void onEvent(BatchEvent event);
    }

    private EventListener eventListener;

    public void setEventListener(EventListener listener) {
        this.eventListener = listener;
    }
}
