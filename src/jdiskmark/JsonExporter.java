package jdiskmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.File;
import java.io.IOException;

public class JsonExporter {

    /**
     * Serializes a Benchmark object to a JSON file.
     * @param benchmark The Benchmark object to serialize.
     * @param filePath The path to the output JSON file.
     * @throws IOException If an error occurs during file writing.
     */
    public static void writeBenchmarkToJson(Benchmark benchmark, String filePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        // Register the module for Java 8 Date/Time types (LocalDateTime, Duration)
        mapper.registerModule(new JavaTimeModule());
        // Configure for pretty printing
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        // Do not fail when properties are missing
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        // Write the object to the file
        mapper.writeValue(new File(filePath), benchmark);
        App.msg("\n\nBenchmark successfully written to JSON file: " + filePath);
    }
    
    /**
     * Serializes a Benchmark object to a JSON String.
     * @param benchmark The Benchmark object to serialize.
     * @return The JSON string representation.
     * @throws IOException 
     */
    public static String convertBenchmarkToJsonString(Benchmark benchmark) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        return mapper.writeValueAsString(benchmark);
    }
}