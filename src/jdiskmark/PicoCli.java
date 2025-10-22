package jdiskmark;

import picocli.CommandLine.Command;

@Command(name = "jdm", mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "JDiskMark Disk Benchmark Utility. Use 'run' to start a test.",
        subcommands = {
            RunBenchmarkCommand.class
        })
public class PicoCli {
    // this class is just for structure
}
