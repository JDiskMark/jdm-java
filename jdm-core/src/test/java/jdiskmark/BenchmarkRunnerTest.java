package jdiskmark;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BenchmarkRunner static utilities.
 * Tests divideIntoRanges() — pure logic, no I/O required.
 */
class BenchmarkRunnerTest {

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
}
