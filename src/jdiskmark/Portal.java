package jdiskmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Portal {
    
    static public final String PRODUCTION_UPLOAD_ENDPOINT = "http://www.jdiskmark.net:5000/api/benchmarks/upload";
    static public final String TEST_UPLOAD_ENDPOINT = "https://test.jdiskmark.net/:5000/api/benchmarks/upload";
    static public final String LOCAL_UPLOAD_ENDPOINT = "http://localhost:5000/api/benchmarks/upload";
    static public final int UPLOAD_PORT = 5000; // cur dev api port
    
    static public String uploadUrl = LOCAL_UPLOAD_ENDPOINT;
    
    // Helper method to check connectivity to the host
    private static boolean isHostReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            // Connect with a 2-second timeout
            socket.connect(new InetSocketAddress(host, port), 2000);
            return true;
        } catch (IOException e) {
            return false; // Host unreachable or port closed
        }
    }
    
    static public void upload(Benchmark benchmark) {
        
        App.msg("starting upload to " + uploadUrl);
        
        // Extract host and port from URI string
        URI uploadUri = URI.create(uploadUrl);
        String host = uploadUri.getHost();
        int port = uploadUri.getPort() != -1 ? uploadUri.getPort() : 80;

        // Pre-upload checks
        try {
            if (InetAddress.getLocalHost() == null) {
                App.err("No local network connection detected.");
                return;
            }
            if (!isHostReachable(host, port)) {
                App.err("Target host " + host + " is unreachable.");
                return;
            }
        } catch (UnknownHostException e) {
            App.err("Connectivity check failed: " + e.getMessage());
            return;
        } catch (SecurityException e) {
            // If a local firewall or security manager blocks the socket attempt
            App.err("Security Error: Connection blocked by local system - " + e.getMessage());
            return;
        }
        
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        String jsonBody;
        try {
            jsonBody = mapper.writeValueAsString(benchmark);
            
            // Write to a local file for debugging
            // This creates/overwrites "debug_benchmark.json" in your project root
            java.nio.file.Files.writeString(
                java.nio.file.Path.of("debug-benchmark.json"), 
                jsonBody
            );
            App.msg("Debug file written to: " + java.nio.file.Paths.get("debug_benchmark.json").toAbsolutePath());
            
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .header("Content-Type", "application/json") // Essential for Express to see it
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 201 || response.statusCode() == 200) {
                App.msg("Benchmark uploaded successfully!");
            } else {
                App.err("Upload failed. Status: " + response.statusCode());
                App.err("Server Response: " + response.body());
            }
        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(Portal.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        App.msg("done uploading to " + uploadUrl);
    }
}
