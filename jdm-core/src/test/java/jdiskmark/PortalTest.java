package jdiskmark;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Portal URL construction.
 * These tests are headless-safe and make no network calls.
 */
class PortalTest {

    @BeforeEach
    void resetDefaults() {
        // Restore defaults before each test so tests don't bleed into each other
        Portal.uploadProtocol = Portal.HTTP;
        Portal.uploadResourceLocator = Portal.LOCAL_UPLOAD_LOCATOR;
    }

    @Test
    void getUploadUrl_defaultConfig_returnsLocalHttpUrl() {
        String url = Portal.getUploadUrl();
        assertEquals("http://localhost:5000/api/benchmarks/upload", url);
    }

    @Test
    void getUploadUrl_productionLocatorWithHttps_returnsProductionUrl() {
        Portal.uploadResourceLocator = Portal.PRODUCTION_UPLOAD_LOCATOR;
        Portal.uploadProtocol = Portal.HTTPS;
        assertEquals("https://www.jdiskmark.net:5000/api/benchmarks/upload", Portal.getUploadUrl());
    }

    @Test
    void getUploadUrl_testLocatorWithHttps_returnsTestUrl() {
        Portal.uploadResourceLocator = Portal.TEST_UPLOAD_LOCATOR;
        Portal.uploadProtocol = Portal.HTTPS;
        assertEquals("https://test.jdiskmark.net:5000/api/benchmarks/upload", Portal.getUploadUrl());
    }

    @Test
    void getUploadUrl_customProtocolAndLocator_concatenatesBoth() {
        Portal.uploadProtocol = "ftp://";
        Portal.uploadResourceLocator = "somehost:9000/api";
        assertEquals("ftp://somehost:9000/api", Portal.getUploadUrl());
    }
}
