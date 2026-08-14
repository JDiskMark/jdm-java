package jdiskmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Portal {

    // protocols
    static public final String HTTP = "http://";
    static public final String HTTPS = "https://";
    // resource locators
    static public final String PRODUCTION_UPLOAD_LOCATOR = "www.jdiskmark.net/api/benchmarks/upload";
    static public final String TEST_UPLOAD_LOCATOR = "test.jdiskmark.net/api/benchmarks/upload";
    static public final String LOCAL_UPLOAD_LOCATOR = "localhost:5000/api/benchmarks/upload";
    // SMART upload path (substituted into the active locator at runtime)
    static public final String SMART_UPLOAD_PATH = "/api/smart/upload";
    static public final String BENCHMARK_UPLOAD_PATH = "/api/benchmarks/upload";

    static public String uploadResourceLocator = TEST_UPLOAD_LOCATOR;
    static public String uploadProtocol = HTTPS;

    static String getUploadUrl() {
        return uploadProtocol + uploadResourceLocator;
    }

    // Helper method to check connectivity to the host
    private static boolean isHostReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            // Connect with a 2-second timeout
            socket.connect(new InetSocketAddress(host, port), 2000);
            return true;
        } catch (IOException e) {
            App.err("IO Exception " + e.getMessage());
            return false; // Host unreachable or port closed
        }
    }

    static void upload(Benchmark benchmark) {
        String uploadUrl = getUploadUrl();
        URI uploadUri = URI.create(uploadUrl);
        String host = uploadUri.getHost();
        int port = uploadUri.getPort() != -1 ? uploadUri.getPort()
                : uploadProtocol.equals(HTTPS) ? 443 : 80;

        // Pre-upload check: attempt a short socket connect to verify the host is reachable.
        // This avoids the macOS Local Network permission dialog that InetAddress.getLocalHost()
        // would trigger unnecessarily — if there is no network the socket attempt will fail here.
        try {
            if (!isHostReachable(host, port)) {
                App.err("Target host " + host + " is unreachable.");
                return;
            }
        } catch (SecurityException e) {
            // If a local firewall or security manager blocks the socket attempt
            App.err("Security Error: Connection blocked by local system - " + e.getMessage());
            return;
        }

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);

        String jsonBody;
        try {
            jsonBody = mapper.writeValueAsString(benchmark);
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uploadUrl))
                    .header("Content-Type", "application/json") // Essential for Express to see it
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201 || response.statusCode() == 200) {
                App.msg("Benchmark uploaded successfully to " + uploadUrl);
            } else {
                App.err("Error uploading to " + uploadUrl);
                App.err("Upload failed. Status: " + response.statusCode());
                App.err("Server Response: " + response.body());
            }
        } catch (IOException | InterruptedException ex) {
            App.err("Error uploading to " + uploadUrl);
            App.err("Error message: " + ex.getMessage());
            Logger.getLogger(Portal.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

    /** Returns the upload URL for SMART snapshots, derived from the active benchmark locator. */
    static String getSmartUploadUrl() {
        String locator = uploadResourceLocator.replace(BENCHMARK_UPLOAD_PATH, SMART_UPLOAD_PATH);
        return uploadProtocol + locator;
    }

    /**
     * Returns a user-navigable portal URL — just the protocol + host, no API
     * path, and no port number (except localhost where the port is meaningful).
     * Reflects whichever endpoint is currently active (production/test/localhost).
     */
    static String getPortalBrowseUrl() {
        java.net.URI uri = java.net.URI.create(getUploadUrl());
        String host = uri.getHost();
        if ("localhost".equalsIgnoreCase(host)) {
            int port = uri.getPort();
            return uploadProtocol + "localhost" + (port != -1 ? ":" + port : "");
        }
        return uploadProtocol + host;
    }

    static void uploadSmart(SmartSnapshot snap) {
        String uploadUrl = getSmartUploadUrl();
        URI uploadUri = URI.create(uploadUrl);
        String host = uploadUri.getHost();
        // Default port: 443 for HTTPS, 80 for HTTP — matches what the server actually listens on.
        int port = uploadUri.getPort() != -1 ? uploadUri.getPort()
                : uploadProtocol.equals(HTTPS) ? 443 : 80;

        // Pre-upload check: use a short socket connect to verify the host is reachable.
        // Avoids InetAddress.getLocalHost() which triggers the macOS Local Network permission
        // dialog unnecessarily — consistent with upload().
        try {
            if (!isHostReachable(host, port)) {
                App.err("Target host " + host + " is unreachable.");
                return;
            }
        } catch (SecurityException e) {
            App.err("Security Error: Connection blocked by local system - " + e.getMessage());
            return;
        }

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);

        String jsonBody;
        try {
            jsonBody = mapper.writeValueAsString(snap);
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uploadUrl))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201 || response.statusCode() == 200) {
                App.msg("SMART snapshot uploaded successfully to " + uploadUrl);
            } else {
                App.err("Error uploading SMART snapshot to " + uploadUrl);
                App.err("Upload failed. Status: " + response.statusCode());
                App.err("Server Response: " + response.body());
            }
        } catch (IOException | InterruptedException ex) {
            App.err("Error uploading SMART snapshot to " + uploadUrl);
            App.err("Error message: " + ex.getMessage());
            Logger.getLogger(Portal.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
        }
    }
}
