package jdiskmark;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    
    static public final String JDM_UPLOAD_ENDPOINT = "http://www.jdiskmark.net:5000/api/benchmarks/upload";
    static public final String LOCAL_UPLOAD_ENDPOINT = "http://localhost:5000/api/benchmarks/upload";
    static public final String UPLOAD_URL = LOCAL_UPLOAD_ENDPOINT;
    
    // cur dev api port
    static public int UPLOAD_PORT = 5000;
    
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
        
        App.msg("starting upload to " + UPLOAD_URL);
        
        // 1. Extract host and port from your URI string
        URI uploadUri = URI.create(UPLOAD_URL);
        String host = uploadUri.getHost();
        int port = uploadUri.getPort() != -1 ? uploadUri.getPort() : 80;

        // 2. Pre-upload checks
        try {
            if (InetAddress.getLocalHost() == null) {
                System.err.println("No local network connection detected.");
                return;
            }
            if (!isHostReachable(host, port)) {
                System.err.println("Target host " + host + " is unreachable.");
                return;
            }
        } catch (UnknownHostException e) {
            System.err.println("Connectivity check failed: " + e.getMessage());
            return;
        } catch (SecurityException e) {
            // If a local firewall or security manager blocks the socket attempt
            System.err.println("Security Error: Connection blocked by local system - " + e.getMessage());
            return;
        }
        
        ObjectMapper mapper = new ObjectMapper();
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
                .uri(URI.create(UPLOAD_URL))
                .header("Content-Type", "application/json") // Essential for Express to see it
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 201 || response.statusCode() == 200) {
                App.msg("Benchmark uploaded successfully!");
            } else {
                System.err.println("Upload failed. Status: " + response.statusCode());
                System.err.println("Server Response: " + response.body());
            }
        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(Portal.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        App.msg("done uploading to " + UPLOAD_URL);
    }
}
