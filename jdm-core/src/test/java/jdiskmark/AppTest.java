package jdiskmark;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Behavioral regression tests for App.
 *
 * Keep tests here only when they guard against a realistic, documented risk.
 * Avoid testing literal constant values — those add refactoring friction
 * without protecting against real bugs.
 */
class AppTest {

    /**
     * Portal sharing must ALWAYS default to false.
     * This is a documented privacy requirement: network activity must be
     * explicitly opt-in each session, never silently resumed.
     * See App.loadConfig() and the App.sharePortal field comments.
     */
    @Test
    void sharePortal_onStartup_isFalse() {
        assertFalse(App.sharePortal,
                "sharePortal must default to false — network activity requires explicit user opt-in each session");
    }
}
