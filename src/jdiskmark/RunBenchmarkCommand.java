package jdiskmark;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jdiskmark.Benchmark.BenchmarkType;
import jdiskmark.Benchmark.BlockSequence;
import jdiskmark.App.IoEngine;
import picocli.CommandLine;
import picocli.CommandLine.Spec;

@Command(name = "run", description = "Starts a disk benchmark test with specified parameters.")
public class RunBenchmarkCommand implements Callable<Integer> {

    public static final String ANSI_HIDE_CURSOR = "\u001b[?25l";
    public static final String ANSI_SHOW_CURSOR = "\u001b[?25h";
    
    // --- PICOCLI INJECTION ---
    @Spec 
    CommandLine.Model.CommandSpec spec;
    
    // --- Profile selection ---
    
    @Option(names = {"-p", "--profile"},
        // This forces the help menu to show the actual Enum constants
        completionCandidates = ProfileCandidates.class, 
        description = "Profile: ${COMPLETION-CANDIDATES}. (Default: ${DEFAULT-VALUE})",
        defaultValue = "QUICK_TEST")
    BenchmarkProfile profile;

    // Helper class to provide the symbols to the help menu
    static class ProfileCandidates extends ArrayList<String> {
        ProfileCandidates() { 
            super(Arrays.stream(BenchmarkProfile.values())
                        .map(Enum::name)
                        .collect(Collectors.toList())); 
        }
    }
    
    // --- Profile Workload Definition ---
    
    @Option(names = {"-t", "--type"},
            description = "Benchmark type: ${COMPLETION-CANDIDATES}. (Profile default used if not specified)",
            defaultValue = "WRITE")
    BenchmarkType benchmarkType;

    @Option(names = {"-T", "--threads"}, 
            description = "Number of threads to use for testing. (Profile default used if not specified)",
            defaultValue = "1")
    int numOfThreads;

    @Option(names = {"-o", "--order"}, 
            description = "Block order: ${COMPLETION-CANDIDATES}. (Profile default used if not specified)",
            defaultValue = "SEQUENTIAL")
    BlockSequence blockSequence;

    @Option(names = {"-b", "--blocks"},
            description = "Number of blocks/chunks per sample. (Profile default used if not specified)",
            defaultValue = "32")
    int numOfBlocks;

    @Option(names = {"-z", "--block-size"},
            description = "Size of a block/chunk in Kilobytes (KB). (Profile default used if not specified)",
            defaultValue = "512")
    int blockSizeKb;

    @Option(names = {"-n", "--samples"},
            description = "Total number of samples/files to write/read. (Profile default used if not specified)",
            defaultValue = "200")
    int numOfSamples;

    // --- Profile IO Strategy ---

    @Option(names = {"-i", "--io-engine"},
            description = "I/O Engine: ${COMPLETION-CANDIDATES}. (Profile default used if not specified)",
            defaultValue = "MODERN")
    IoEngine ioEngine;

    @Option(names = {"-d", "--direct"},
            description = "Enable Direct I/O (bypass OS cache). Only works with MODERN engine.")
    boolean directEnable = false;

    @Option(names = {"-y", "--write-sync"},
            description = "Enable Write Sync (flush to disk).")
    boolean writeSyncEnable = false;

    @Option(names = {"-a", "--alignment"},
            description = "Sector alignment: ${COMPLETION-CANDIDATES}. (Profile default used if not specified)",
            defaultValue = "NONE")
    App.SectorAlignment sectorAlignment;

    @Option(names = {"-m", "--multi-file"},
            description = "Create a new file for every sample instead of using one large file.")
    boolean multiFile = false;
    
    // --- Environmental and Persistance ---
    
    @Option(names = {"-l", "--location"},
            description = "The directory path where test files will be created.",
            defaultValue = "${user.home}")
    File locationDir;
    
    @Option(names = {"-e", "--export"},
            description = "The output file to export benchmark results in json format.")
    File exportPath;

    @Option(names = {"-s", "--save"}, description = "Enable saving the benchmark results to the database.")
    boolean save = false;
    
    @Option(names = {"-c", "--clean"}, description = "Remove existing JDiskMark data directory before starting.")
    boolean autoRemoveData = false;

    // --- Utility and Diagnostics ---
    
    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Display this help and exit.")
    boolean helpRequested;
    
    @Option(names = {"-v", "--verbose"}, description = "Enable detailed logging.")
    boolean verbose = false;

    // overrides to profile-controlled parameters
    private void applyOverrides(CommandLine.ParseResult pr) {
        // Workload Definition
        if (pr.hasMatchedOption("--type"))         App.benchmarkType = benchmarkType;
        if (pr.hasMatchedOption("--threads"))      App.numOfThreads = numOfThreads;
        if (pr.hasMatchedOption("--order"))        App.blockSequence = blockSequence;
        if (pr.hasMatchedOption("--blocks"))       App.numOfBlocks = numOfBlocks;
        if (pr.hasMatchedOption("--block-size"))   App.blockSizeKb = blockSizeKb;
        if (pr.hasMatchedOption("--samples"))      App.numOfSamples = numOfSamples;
        // IO Strategy
        if (pr.hasMatchedOption("--io-engine"))    App.ioEngine = ioEngine;
        if (pr.hasMatchedOption("--direct"))       App.directEnable = directEnable;
        if (pr.hasMatchedOption("--write-sync"))   App.writeSyncEnable = writeSyncEnable;
        if (pr.hasMatchedOption("--alignment"))    App.sectorAlignment = sectorAlignment;
        if (pr.hasMatchedOption("--multi-file"))   App.multiFile = multiFile;
    }
    
    @Override
    public Integer call() {
        if (helpRequested) {
            return 0; // Return 0 (Success) immediately after help is printed
        }
        try {
            // configure the profile
            System.out.println("loading profile: " + profile.name);
            App.loadProfile(profile);
            // apply profile parameter overrides
            applyOverrides(spec.commandLine().getParseResult());
            // environment and persistance
            App.setLocationDir(locationDir);
            App.autoRemoveData = autoRemoveData;
            App.verbose = verbose;
            App.autoSave = save;
            App.exportPath = exportPath;

            // Initialization and Start
            if (App.verbose) {
                System.out.println("--- Starting JDiskMark Benchmark (CLI) ---");
            }
            
            App.init();
            
            if (App.verbose) {
                System.out.println(App.getConfigString());
                System.out.println("Benchmark initiated successfully. Starting execution...");
            }
            
            try {
                System.out.print(ANSI_HIDE_CURSOR);
                App.startBenchmark();
                App.waitBenchmarkDone();
            } finally {
                System.out.print(ANSI_SHOW_CURSOR);
            }
            return 0; // Success exit code
            
        } catch (RuntimeException e) {
            System.err.println("Error running benchmark: " + e.getMessage());
            Logger.getLogger(RunBenchmarkCommand.class.getName()).log(Level.SEVERE, "Trace:", e);
            return 1; // Failure exit code
        }
    }
}