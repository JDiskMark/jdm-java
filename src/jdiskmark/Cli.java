package jdiskmark;

import picocli.CommandLine.Command;

@Command(name = "jdiskmark", mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "JDiskMark - Java Disk Benchmark Utility. Use 'run' to start a test.",
        subcommands = {
            RunBenchmarkCommand.class
        })
public class Cli {
    // this class is just for structure
}
