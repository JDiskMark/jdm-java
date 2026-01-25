package jdiskmark;
// constants
import static jdiskmark.Sample.Type.READ;
import static jdiskmark.Sample.Type.WRITE;
// global variables
import static jdiskmark.App.msg;
import static jdiskmark.App.dataDir;

import jakarta.persistence.EntityManager;
import java.util.List;
import javax.swing.SwingWorker;

/**
 * Thread running the disk benchmarking. only one of these threads can run at
 * once.
 */
public class BenchmarkWorker extends SwingWorker<Benchmark, Sample> {
    
    @Override
    protected Benchmark doInBackground() throws Exception {

        if (App.verbose) {
            msg("*** starting new worker thread");
            msg("Running readTest " + App.isReadEnabled() + "   writeTest " + App.isWriteEnabled());
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
        
        BenchmarkLogic.BenchmarkListener listener = new BenchmarkLogic.BenchmarkListener() {
            @Override
            public void onSampleComplete(Sample s) { publish(s); }

            @Override
            public void onProgressUpdate(long completed, long total) { setProgress((int) completed); }

            @Override
            public boolean isCancelled() { return BenchmarkWorker.this.isCancelled(); }

            @Override
            public void requestCacheDrop() { Gui.dropCache(); }
        };

        BenchmarkLogic logic = new BenchmarkLogic(listener);
        Benchmark benchmark = logic.execute();
        
        // update gui title
        Gui.chart.getTitle().setText(benchmark.getDriveInfoDisplay());
        Gui.chart.getTitle().setVisible(true);

        if (App.autoSave) {
            EntityManager em = EM.getEntityManager();
            em.getTransaction().begin();
            em.persist(benchmark);
            em.getTransaction().commit();
            App.benchmarks.put(benchmark.getStartTimeString(), benchmark);
            for (BenchmarkOperation o : benchmark.getOperations()) {
                App.operations.put(o.getStartTimeString(), o);
            }
        }
        // #67 upload to community portal (in progress)
        if (App.sharePortal) {
            Portal.upload(benchmark);
        }
        if (App.exportPath != null) {
            JsonExporter.writeBenchmarkToJson(benchmark, App.exportPath.getAbsolutePath());
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
        if (App.autoRemoveData) {
            Util.deleteDirectory(dataDir);
        }
        App.state = App.State.IDLE_STATE;
        Gui.mainFrame.adjustSensitivity();
    }
}
