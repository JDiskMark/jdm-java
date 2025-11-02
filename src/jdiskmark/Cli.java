package jdiskmark;

import java.io.File;
import java.util.Scanner;
import picocli.CommandLine.Command;

@Command(name = "jdiskmark", mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "JDiskMark - Java Disk Benchmark Utility. Use 'run' to start a test.",
        subcommands = {
            RunBenchmarkCommand.class
        })
public class Cli {
    static public void dropCache() {
        String osName = System.getProperty("os.name");
        if (osName.contains("Linux")) {
            if (App.isRoot) {
                // GH-2 automate catch dropping
                UtilOs.flushDataToDriveLinux();
                UtilOs.dropWriteCacheLinux();
            } else {
                /* Revised the drop_caches command so it works. - JSL 2024-01-16 */
                String message = """
                        \nRun JDiskMark with sudo to automatically clear the disk cache.
                        
                        For a valid READ benchmark please clear the disk cache now 
                        by using: \"sudo sh -c \'sync; echo 1 > /proc/sys/vm/drop_caches\'\".
                        
                        Press OK to continue when disk cache has been dropped.""";
                System.out.println(message);
                try (Scanner scanner = new Scanner(System.in)) {
                    // Block until Enter is pressed
                    scanner.nextLine();
                } catch (java.util.NoSuchElementException ex) {
                    System.err.println("Input stream closed unexpectedly.");
                }
            }
        } else if (osName.contains("Mac OS")) {
            if (App.isRoot) {
                // GH-2 automate catch dropping
                UtilOs.flushDataToDriveMacOs();
                UtilOs.dropWriteCacheMacOs();
            } else {
                String message = """
                        \nFor valid READ benchmarks please clear the disk cache.

                        Removable drives can be disconnected and reconnected.

                        For system drives perform a WRITE benchmark, restart 
                        the OS and then perform a READ benchmark.

                        Press OK to continue when disk cache has been cleared.""";
                System.out.println(message);
                try (Scanner scanner = new Scanner(System.in)) {
                    // Block until Enter is pressed
                    scanner.nextLine();
                } catch (java.util.NoSuchElementException ex) {
                    System.err.println("Input stream closed unexpectedly.");
                }
            }
        } else if (osName.contains("Windows")) {
            File emptyStandbyListExe = new File(".\\" + App.ESBL_EXE);
            if (!emptyStandbyListExe.exists()) {
                // jpackage windows relative environment
                emptyStandbyListExe = new File(".\\app\\" + App.ESBL_EXE);
            }
            if (App.verbose) {
                System.out.println("\nemptyStandbyListExe.exist=" + emptyStandbyListExe.exists());
            }
            if (App.isAdmin && emptyStandbyListExe.exists()) {
                // GH-2 drop cahe, delays in place of flushing cache
                try { Thread.sleep(1300); } catch (InterruptedException ex) {}
                UtilOs.emptyStandbyListWindows(emptyStandbyListExe);
                try { Thread.sleep(700); } catch (InterruptedException ex) {}
            } else  if (App.isAdmin && !emptyStandbyListExe.exists()) {
                String message = """
                        \nUnable to find EmptyStandbyList.exe. This must be
                        present in the install directory for the disk cache
                        to be automatically cleared.
                        
                        For valid READ benchmarks please clear the disk cache by
                        using EmptyStandbyList.exe or RAMMap.exe utilities.

                        For system drives perform a WRITE benchmark, restart 
                        the OS and then perform a READ benchmark.

                        Press OK to continue when disk cache has been cleared.
                        """;
                System.out.println(message);
                try (Scanner scanner = new Scanner(System.in)) {
                    // Block until Enter is pressed
                    scanner.nextLine();
                } catch (java.util.NoSuchElementException ex) {
                    System.err.println("Input stream closed unexpectedly.");
                }
            } else if (!App.isAdmin) {
                String message = """
                        \nRun JDiskMark as admin to automatically clear the disk cache.

                        For valid READ benchmarks please clear the disk cache by
                        using EmptyStandbyList.exe or RAMMap.exe utilities.

                        For system drives perform a WRITE benchmark, restart 
                        the OS and then perform a READ benchmark.

                        Press OK to continue when disk cache has been cleared.""";
                System.out.println(message);
                try (Scanner scanner = new Scanner(System.in)) {
                    // Block until Enter is pressed
                    scanner.nextLine();
                } catch (java.util.NoSuchElementException ex) {
                    System.err.println("Input stream closed unexpectedly.");
                }
            }
        } else {
            String message = "Unrecognized OS: " + osName + "\n" +
                    """
                    \nFor valid READ benchmarks please clear the disk cache now.

                    Removable drives can be disconnected and reconnected.

                    For system drives perform a WRITE benchmark, restart 
                    the OS and then perform a READ benchmarks benchmark.

                    Press OK to continue when disk cache has been cleared.""";
            System.out.println(message);
            try (Scanner scanner = new Scanner(System.in)) {
                // Block until Enter is pressed
                scanner.nextLine();
            } catch (java.util.NoSuchElementException ex) {
                System.err.println("Input stream closed unexpectedly.");
            }
        }
    }
}
