package jdiskmark;

import org.metricus.jdm.util.Util;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Unit tests for Util.shuffledIndices(). */
class UtilTest {

    @Test
    void shuffledIndices_containsEachIndexExactlyOnce() {
        int[] sorted = Util.shuffledIndices(128).clone();
        Arrays.sort(sorted);
        for (int i = 0; i < sorted.length; i++) {
            assertEquals(i, sorted[i]);
        }
    }

    @Test
    void shuffledIndices_isNotInOrder() {
        // a 128 element shuffle matching the identity order is practically impossible
        int[] indices = Util.shuffledIndices(128);
        boolean inOrder = true;
        for (int i = 0; i < indices.length; i++) {
            if (indices[i] != i) {
                inOrder = false;
                break;
            }
        }
        assertFalse(inOrder);
    }

    @Test
    void shuffledIndices_handlesSmallCounts() {
        assertEquals(0, Util.shuffledIndices(0).length);
        assertArrayEquals(new int[]{0}, Util.shuffledIndices(1));
    }
}
