// Reproduces the suspected JDK Direct I/O handle leak on Windows FAT32/exFAT (#260).
//
// Usage (JDK 11+, no build needed):
//   java DirectIoLockRepro.java <folder on the drive to test>
//   e.g.  java DirectIoLockRepro.java E:\
//
// Expected on FAT32/exFAT: BUFFERED recreate OK, DIRECT recreate FAILED (bug).
// Expected on NTFS:        both OK.

import com.sun.nio.file.ExtendedOpenOption;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class DirectIoLockRepro {

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("usage: java DirectIoLockRepro.java <folder on FAT32/exFAT drive>");
            System.exit(2);
        }
        Path dir = Path.of(args[0]).toAbsolutePath();
        System.out.println("Java       : " + System.getProperty("java.vendor") + " "
                + System.getProperty("java.runtime.version"));
        System.out.println("OS         : " + System.getProperty("os.name") + " "
                + System.getProperty("os.version"));
        System.out.println("Folder     : " + dir);
        System.out.println("File system: " + Files.getFileStore(dir).type());
        System.out.println();

        boolean bufferedOk = test(dir.resolve("repro-buffered.dat"), false);
        boolean directOk   = test(dir.resolve("repro-direct.dat"), true);

        System.out.println();
        if (bufferedOk && !directOk) {
            System.out.println("RESULT: bug reproduced - the Direct I/O file can't be re-created "
                    + "until this JVM exits.");
            System.exit(1);
        }
        System.out.println(directOk
                ? "RESULT: not reproduced on this drive (expected on NTFS)."
                : "RESULT: inconclusive - the buffered control also failed.");
    }

    /** Opens and closes the file, deletes it, then tries to create it again. */
    private static boolean test(Path file, boolean direct) throws Exception {
        String label = direct ? "DIRECT  " : "BUFFERED";
        Files.deleteIfExists(file);
        Files.write(file, new byte[4096]);

        OpenOption[] options = direct
                ? new OpenOption[]{StandardOpenOption.READ, ExtendedOpenOption.DIRECT}
                : new OpenOption[]{StandardOpenOption.READ};
        try (FileChannel fc = FileChannel.open(file, options)) {
            // open and close only; the leak happens at open time
        }

        Files.delete(file);
        try {
            FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).close();
            Files.delete(file);
            System.out.println(label + " recreate after delete: OK");
            return true;
        } catch (Exception e) {
            System.out.println(label + " recreate after delete: FAILED " + e);
            return false;
        }
    }
}
