package jdiskmark;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.io.File;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

@Command(name = "run", description = "Starts a disk benchmark test with specified parameters.")
public class RunBenchmarkCommand implements Callable<Integer> {

    // --- REQUIRED PARAMETER ---
    @Option(names = {"-l", "--location"}, description = "The directory path where test files will be created.", required = true)
    File locationDir;

    // --- OPTIONAL PARAMETERS (Mapped to GUI controls) ---
    
    @Option(names = {"-t", "--type"},
            description = "Benchmark type: ${COMPLETION-CANDIDATES}. (Default: ${DEFAULT-VALUE})",
            defaultValue = "WRITE")
    Benchmark.BenchmarkType benchmarkType;

    @Option(names = {"-T", "--threads"}, 
            description = "Number of threads to use for testing. (Default: ${DEFAULT-VALUE})",
            defaultValue = "1")
    int numOfThreads;

    @Option(names = {"-o", "--order"}, 
            description = "Block order: ${COMPLETION-CANDIDATES}. (Default: ${DEFAULT-VALUE})",
            defaultValue = "SEQUENTIAL")
    BenchmarkOperation.BlockSequence blockSequence;

    @Option(names = {"-b", "--blocks"}, 
            description = "Number of blocks/chunks per sample. (Default: ${DEFAULT-VALUE})",
            defaultValue = "32")
    int numOfBlocks;

    @Option(names = {"-s", "--block-size"}, 
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

    @Option(names = {"--clean"}, description = "Remove existing JDiskMark data directory before starting.")
    boolean autoRemoveData = false;


    @Override
    public Integer call() {
        try {
            // 1. Apply CLI parameters to the global App state
            App.setLocationDir(locationDir);
            App.benchmarkType = benchmarkType;
            App.numOfThreads = numOfThreads;
            App.blockSequence = blockSequence;
            App.numOfBlocks = numOfBlocks;
            App.blockSizeKb = blockSizeKb;
            App.numOfSamples = numOfSamples;
            App.autoRemoveData = autoRemoveData; // Apply the --clean flag

            // 2. Output final configuration before starting
            System.out.println("--- Starting JDiskMark Benchmark (CLI) ---");
            System.out.println(App.getConfigString());
            
            // 3. Execute the benchmark (You will need to adjust startBenchmark to run without a GUI)
            // NOTE: The existing App.startBenchmark() relies on a SwingWorker and Gui components.
            // You MUST refactor the core benchmarking logic out of the SwingWorker and 
            // into a dedicated CLI execution class/method for this to work.
                        
            System.out.println("Benchmark initiated successfully. Need to wait for completion...");
            App.startBenchmark();
            App.waitBenchmarkDone();
            return 0; // Success exit code
            
        } catch (RuntimeException e) {
            // Handle any exceptions during setup or execution
            System.err.println("Error running benchmark: " + e.getMessage());
            Logger.getLogger(RunBenchmarkCommand.class.getName()).log(Level.SEVERE, null, e);
            return 1; // Failure exit code
        }
    }
}