package jdiskmark;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.io.File;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import jdiskmark.Benchmark.BenchmarkType;
import jdiskmark.Benchmark.BlockSequence;

@Command(name = "run", description = "Starts a disk benchmark test with specified parameters.")
public class RunBenchmarkCommand implements Callable<Integer> {

    // for hiding and showing the cursor during a cli benchmark
    public static final String ANSI_HIDE_CURSOR = "\u001b[?25l";
    public static final String ANSI_SHOW_CURSOR = "\u001b[?25h";
    
    // --- OPTIONAL PARAMETERS ---
    @Option(names = {"-l", "--location"},
            description = "The directory path where test files will be created.",
            defaultValue = "${user.home}")
    File locationDir;
    
    @Option(names = {"-e", "--export"},
            description = "The output file to export benchmark results in json format.")
    File exportPath;

    @Option(names = {"-t", "--type"},
            description = "Benchmark type: ${COMPLETION-CANDIDATES}. (Default: ${DEFAULT-VALUE})",
            defaultValue = "WRITE")
    BenchmarkType benchmarkType;

    @Option(names = {"-T", "--threads"}, 
            description = "Number of threads to use for testing. (Default: ${DEFAULT-VALUE})",
            defaultValue = "1")
    int numOfThreads;

    @Option(names = {"-o", "--order"}, 
            description = "Block order: ${COMPLETION-CANDIDATES}. (Default: ${DEFAULT-VALUE})",
            defaultValue = "SEQUENTIAL")
    BlockSequence blockSequence;

    @Option(names = {"-b", "--blocks"},
            description = "Number of blocks/chunks per sample. (Default: ${DEFAULT-VALUE})",
            defaultValue = "32")
    int numOfBlocks;

    @Option(names = {"-z", "--block-size"},
            description = "Size of a block/chunk in Kilobytes (KB). (Default: ${DEFAULT-VALUE})",
            defaultValue = "512")
    int blockSizeKb;

    @Option(names = {"-n", "--samples"},
            description = "Total number of samples/files to write/read. (Default: ${DEFAULT-VALUE})",
            defaultValue = "200")
    int numOfSamples;

    // --- FLAGS / UTILITY OPTIONS ---
    
    @Option(names = {"--verbose", "-v"}, description = "Enable detailed logging.")
    boolean verbose = false;

    @Option(names = {"--save", "-s"}, description = "Enable saving the benchmark.")
    boolean save = false;
    
    @Option(names = {"--clean", "-c"}, description = "Remove existing JDiskMark data directory before starting.")
    boolean autoRemoveData = false;

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Display this help and exit.")
    boolean helpRequested;

    @Override
    public Integer call() {
        if (helpRequested) {
            return 0; // Return 0 (Success) immediately after help is printed
        }
        try {
            
            App.isCliMode = true;

            // 1. Apply CLI parameters to the global App state
            App.setLocationDir(locationDir);
            App.benchmarkType = benchmarkType;
            App.numOfThreads = numOfThreads;
            App.blockSequence = blockSequence;
            App.numOfBlocks = numOfBlocks;
            App.blockSizeKb = blockSizeKb;
            App.numOfSamples = numOfSamples;
            App.autoRemoveData = autoRemoveData; // Apply the --clean flag
            App.verbose = verbose;
            App.autoSave = save;
            App.exportPath = exportPath;

            // 2. Output final configuration before starting
            if (App.verbose) {
                System.out.println("--- Starting JDiskMark Benchmark (CLI) ---");
            }
            App.init();
            if (App.verbose) {
                String configString = App.getConfigString();
                System.out.println(configString);
                System.out.println("Benchmark initiated successfully. Need to wait for completion...");
            }
            
            // 3. Execute the benchmark (You will need to adjust startBenchmark to run without a GUI)
            // NOTE: The existing App.startBenchmark() relies on a SwingWorker and Gui components.
            // You MUST refactor the core benchmarking logic out of the SwingWorker and 
            // into a dedicated CLI execution class/method for this to work.
            
            try {
                System.out.print(ANSI_HIDE_CURSOR);
                App.startBenchmark();
                App.waitBenchmarkDone();
            } finally {
                System.out.print(ANSI_SHOW_CURSOR);
            }
            return 0; // Success exit code
            
        } catch (RuntimeException e) {
            // Handle any exceptions during setup or execution
            System.err.println("Error running benchmark: " + e.getMessage());
            Logger.getLogger(RunBenchmarkCommand.class.getName()).log(Level.SEVERE, null, e);
            return 1; // Failure exit code
        }
    }
}