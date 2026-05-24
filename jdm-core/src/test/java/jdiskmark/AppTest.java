package jdiskmark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

    /**
     * Every AppIcon enum entry must resolve to an actual classpath resource.
     * This guards against icon renames or path typos that would cause the
     * About dialog (and taskbar/title-bar) to silently display no icon.
     */
    @ParameterizedTest
    @EnumSource(App.AppIcon.class)
    void appIcon_load_isNonNull(App.AppIcon icon) {
        assertNotNull(icon.load(),
                "Icon resource not found on classpath: " + icon.resourcePath);
    }
}
