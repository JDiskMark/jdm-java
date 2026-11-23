package jdiskmark;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricus.jdm.os.UtilsLinuxOs;
import org.metricus.jdm.os.UtilsMacOs;
import org.metricus.jdm.os.UtilsWinOs;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BenchmarkRunner static utilities.
 * Tests divideIntoRanges() — pure logic, no I/O required.
 */
class BenchmarkRunnerTest {

    @Test
    void deleteDirectoryReason_removesTreeAndReturnsNull(@TempDir Path tmp) throws IOException {
        Path dataDir = tmp.resolve("jdm-data");
        Files.createDirectories(dataDir.resolve("sub"));
        Files.write(dataDir.resolve("testdata.jdm"), new byte[16]);
        Files.write(dataDir.resolve("sub").resolve("testdata1.jdm"), new byte[16]);
        assertNull(Util.deleteDirectoryReason(dataDir.toFile()));
        assertFalse(Files.exists(dataDir));
        assertNull(Util.deleteDirectoryReason(dataDir.toFile()));
    }

    @Test
    void prepareDirectory_createsUsableDirectory(@TempDir Path tmp) {
        Path dataDir = tmp.resolve("jdm-data");
        assertNull(Util.prepareDirectory(dataDir.toFile()));
        assertTrue(Files.isDirectory(dataDir));
        assertNull(Util.prepareDirectory(dataDir.toFile()));
    }

    @Test
    void describeIoError_givesReadableReason() {
        assertEquals("access denied (file may be in use or pending delete)",
                Util.describeIoError(new AccessDeniedException("G:\\jdm-data\\testdata.jdm")));
        assertEquals("file not found",
                Util.describeIoError(new NoSuchFileException("x")));
        assertEquals("disk full",
                Util.describeIoError(new FileSystemException("x", null, "disk full")));
        assertEquals("boom", Util.describeIoError(new IOException("boom")));
    }

    @Test
    void divideIntoRanges_singleThread_returnsOneRange() {
        int[][] ranges = BenchmarkRunner.divideIntoRanges(1, 11, 1);
        assertEquals(1, ranges.length);
        assertEquals(1, ranges[0][0]);
        assertEquals(11, ranges[0][1]);
    }

    @Test
    void divideIntoRanges_evenSplit_returnsEqualRanges() {
        int[][] ranges = BenchmarkRunner.divideIntoRanges(0, 10, 2);
        assertEquals(2, ranges.length);
        assertEquals(0, ranges[0][0]);
        assertEquals(5, ranges[0][1]);
        assertEquals(5, ranges[1][0]);
        assertEquals(10, ranges[1][1]);
    }

    @Test
    void divideIntoRanges_unevenSplit_distributesRemainder() {
        // 10 elements across 3 threads: 4, 3, 3
        int[][] ranges = BenchmarkRunner.divideIntoRanges(0, 10, 3);
        assertEquals(3, ranges.length);
        // Verify total coverage without gaps or overlaps
        assertEquals(0, ranges[0][0]);
        assertEquals(ranges[0][1], ranges[1][0], "No gap between thread 0 and 1");
        assertEquals(ranges[1][1], ranges[2][0], "No gap between thread 1 and 2");
        assertEquals(10, ranges[2][1], "Last range ends at endIndex");
    }

    @Test
    void divideIntoRanges_invalidNumThreads_returnsEmpty() {
        int[][] ranges = BenchmarkRunner.divideIntoRanges(0, 10, 0);
        assertEquals(0, ranges.length, "Zero threads should return empty array");
    }

    @Test
    void divideIntoRanges_invertedBounds_returnsEmpty() {
        int[][] ranges = BenchmarkRunner.divideIntoRanges(10, 5, 2);
        assertEquals(0, ranges.length, "Inverted start/end should return empty array");
    }

    @Test
    void divideIntoRanges_allRangesCoverFullSpan() {
        // Property test: for any valid split, first start == startIndex, last end == endIndex
        for (int threads = 1; threads <= 8; threads++) {
            int[][] ranges = BenchmarkRunner.divideIntoRanges(1, 201, threads);
            assertEquals(threads, ranges.length);
            assertEquals(1, ranges[0][0]);
            assertEquals(201, ranges[threads - 1][1]);
        }
    }

    @Test
    void formatDriveModelWithUsbIds_appendsWhenPresent() {
        Util.UsbIds ids = new Util.UsbIds("0781", "5583");
        assertEquals("SanDisk 3.2Gen1 [0781:5583]",
                Util.formatDriveModelWithUsbIds("SanDisk 3.2Gen1", ids));
        assertEquals("SanDisk 3.2Gen1 [0781:5583]",
                Util.formatDriveModelWithUsbIds("SanDisk 3.2Gen1 [0781:5583]", ids));
        assertEquals("SanDisk 3.2Gen1",
                Util.formatDriveModelWithUsbIds("SanDisk 3.2Gen1", null));
    }

    @Test
    void parseUsbIdsFromPnpId_extractsLowercaseHex() {
        Util.UsbIds ids = UtilsWinOs.parseUsbIdsFromPnpId("USB\\VID_0781&PID_5583\\0401A1B2C3");
        assertEquals(new Util.UsbIds("0781", "5583"), ids);

        Util.UsbIds composite = UtilsWinOs.parseUsbIdsFromPnpId("USB\\VID_0BDA&PID_9210&MI_00\\6&1234&0");
        assertEquals(new Util.UsbIds("0bda", "9210"), composite);

        assertNull(UtilsWinOs.parseUsbIdsFromPnpId("USBSTOR\\DISK&VEN_SANDISK&PROD_3.2GEN1\\123"));
        assertNull(UtilsWinOs.parseUsbIdsFromPnpId(null));
    }

    @Test
    void mapUsbVersion_mapsSpeedAndV2Flags() {
        assertEquals("3.2 Gen 2", UtilsWinOs.mapUsbVersion(2, 0x07));
        assertEquals("3.2 Gen 2", UtilsWinOs.mapUsbVersion(2, 0x04));
        assertEquals("3.0", UtilsWinOs.mapUsbVersion(2, 0x03));
        assertEquals("3.0", UtilsWinOs.mapUsbVersion(2, 0x01));
        assertEquals("2.0", UtilsWinOs.mapUsbVersion(2, 0));
        assertEquals("1.1", UtilsWinOs.mapUsbVersion(1, 0));
        assertEquals("1.0", UtilsWinOs.mapUsbVersion(0, 0));
        assertNull(UtilsWinOs.mapUsbVersion(99, 0));
    }

    @Test
    void normalizeHexId_padsAndLowercases() {
        assertEquals("0781", UtilsLinuxOs.normalizeHexId("0x0781\n"));
        assertEquals("0781", UtilsLinuxOs.normalizeHexId("781"));
        assertEquals("0bda", UtilsLinuxOs.normalizeHexId("0BDA"));
        assertNull(UtilsLinuxOs.normalizeHexId("not-hex"));
    }

    @Test
    void parseUsbIdsFromIoreg_matchesOwningUsbDevice() {
        List<String> lines = List.of(
                "+-o  USB3.1 Hub@03100000  <class IOUSBHostDevice, id 0x100>",
                "  | {",
                "  |   \"idVendor\" = 8457",
                "  |   \"idProduct\" = 1640",
                "  | }",
                "+-o  SanDisk 3.2Gen1@03200000  <class IOUSBHostDevice, id 0x101>",
                "  | {",
                "  |   \"idVendor\" = 1921",
                "  |   \"idProduct\" = 21891",
                "  | }",
                "  +-o IOMedia  <class IOMedia, id 0x102>",
                "    | {",
                "    |   \"BSD Name\" = \"disk4\"",
                "    | }"
        );
        assertEquals(new Util.UsbIds("0781", "5583"),
                UtilsMacOs.parseUsbIdsFromIoreg(lines, "disk4"));
        assertNull(UtilsMacOs.parseUsbIdsFromIoreg(lines, "disk9"));
    }
}
