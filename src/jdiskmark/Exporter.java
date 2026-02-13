package jdiskmark;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

public class Exporter {
    
    public enum ExportFormat {
        JSON("json"),
        YAML("yml"),
        CSV("csv");
        private final String extension;
        ExportFormat(String extension) {
            this.extension = extension;
        }
        public String getExtension() {
            return extension;
        }
    }
    
    private static final TypeReference<Map<String, Object>> MAP_TYPE = 
            new TypeReference<Map<String, Object>>() {};

    private static final Logger logger = Logger.getLogger(Exporter.class.getName());
    
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
        
        App.msg("Benchmark successfully exported to JSON file: " + filePath);
    }
    
    public static void writeBenchmark(Benchmark benchmark, String filePath, ExportFormat format) throws IOException {

        // 1. Initialize mapper
        ObjectMapper mapper;
        switch(format) {
            case YAML -> {
                mapper = new YAMLMapper();
                    // Optional: remove the "---" document start for a cleaner look
                    ((YAMLMapper) mapper).disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
            }
            case CSV -> {
                mapper = new CsvMapper();
            }
            default -> mapper = new ObjectMapper(); // JSON mapper
        }
        
        // 2. Write the file
        File fileToSave = new File(filePath);
        if (format == ExportFormat.JSON || format == ExportFormat.YAML) {
            mapper.registerModule(new JavaTimeModule());
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
            mapper.writeValue(fileToSave, benchmark);
        } else if (format == ExportFormat.CSV) {
            writeBenchmarkToCsv(mapper, benchmark, filePath);
        }
        
        // 3. After successful write:
        Object[] options = {"Open Folder", "Done"};
        int selection = JOptionPane.showOptionDialog(
            Gui.mainFrame,
            "Benchmark exported successfully to:\n" + fileToSave.getName(),
            "Export Complete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.INFORMATION_MESSAGE,
            null,
            options,
            options[1]
        );

        // 4. open native os explorer (Windows, macOS, or Linux) if indicated
        if (selection == JOptionPane.YES_OPTION) {
            try {
                java.awt.Desktop.getDesktop().open(fileToSave.getParentFile());
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Could not open folder", ex);
            }
        }
    }
    
    // Keep the CSV logic separate since it requires flattening the nested arrays
    private static void writeBenchmarkToCsv(ObjectMapper mapper, Benchmark benchmark, String filePath) throws IOException {
        mapper.registerModule(new JavaTimeModule());

        // 1. Flatten samples and inject the ioMode from the parent operation
        // Convert Sample object to a Map so we can dynamically add the "ioMode" column
        var data = benchmark.getOperations().stream()
                .flatMap(op -> op.getSamples().stream().map(s -> {
                    java.util.Map<String, Object> row = mapper.convertValue(s, MAP_TYPE);
                    row.put("ioMode", op.getIoMode()); // Injects "READ" or "WRITE"
                    return row;
                }))
                .toList();

        // 2. Define the schema to match existing columns + the new ioMode
        CsvSchema schema = CsvSchema.builder()
                .addColumn("sn")      // Sample Number
                .addColumn("ioMode")  // IO Type
                .addColumn("bw")      // Bandwidth
                .addColumn("bt")      // Bandwidth Trend
                .addColumn("la")      // Latency
                .addColumn("lt")      // Latency Trend
                .addColumn("mn")      // Bandwidth Min
                .addColumn("mx")      // Bandwidth Max
                .build().withHeader();

        // 3. Write metadata header followed by the CSV data
        try (java.io.BufferedWriter writer = new java.io.BufferedWriter(new java.io.FileWriter(filePath, StandardCharsets.UTF_8))) {
            // Benchmark Parameter Summary
            writer.write("# JDiskMark " + App.VERSION + " Benchmark Summary\n");
            writer.write("# ---------------------------\n");
            writer.write("# Date: " + benchmark.getStartTimeString() + "\n");
            writer.write("# Model: " + benchmark.driveInfo.driveModel + "\n");
            writer.write("# Profile: " + benchmark.config.profile + "\n");
            writer.write("# Type: " + benchmark.config.benchmarkType + "\n");
            writer.write("# Threads: " + benchmark.config.numThreads + "\n");
            writer.write("# Order: " + benchmark.config.blockOrder + "\n");
            writer.write("# Blocks: " + benchmark.config.numBlocks + "\n");
            writer.write("# BlockSize: " + benchmark.config.blockSize + "\n");
            writer.write("# Samples: " + benchmark.config.numSamples + "\n");
            
            // Operation Results Summary
            for (var op : benchmark.getOperations()) {
                writer.write(String.format("# %s Result: bw %.2f MB/s, lat %.2f ms, iops %s\n", 
                        op.getIoMode(), op.getBandwidth(), op.getLatency(), op.getIops()));
            }
            writer.write("# ---------------------------\n\n");
            
            // 4. Write the actual CSV data rows
            mapper.writer(schema).writeValue(writer, data);
        }
        
        App.msg("Benchmark samples exported to CSV: " + filePath);
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
    
    // used by gui to perform an export of a specified format
    public static void exportBenchmarkAction(Benchmark benchmark, ExportFormat format) {
        
        if (benchmark == null) {
            JOptionPane.showMessageDialog(Gui.mainFrame, 
                    "No benchmark data available to export.", 
                    "Export Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // 1. Prepare the default directory (~/Documents/JDiskMark)
        String userHome = System.getProperty("user.home");
        File exportDir = new File(userHome + File.separator + "Documents" + File.separator + "JDiskMark");
        if (!exportDir.exists()) {
            exportDir.mkdirs();
        }

        // 2. Generate default filename w timestamped sanitized model number
        String sanitizedModel = benchmark.driveInfo.driveModel.trim()
                                .replaceAll("\\s+", ".")
                                .replaceAll("[^a-zA-Z0-9.]", "");
        int maxModelLength = 20;
        String ext = format.getExtension();
        String truncatedModel = sanitizedModel.substring(0, Math.min(sanitizedModel.length(), maxModelLength));
        truncatedModel = truncatedModel.replaceAll("^\\.+|\\.+$", "");
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HHmm").format(new Date());
        String defaultFileName = "jdm_" + truncatedModel + "_" + timeStamp + "." + ext;

        // 3. Initialize the JFileChooser
        JFileChooser fileChooser = new JFileChooser(exportDir);
        fileChooser.setDialogTitle("Export Benchmark");
        fileChooser.setSelectedFile(new File(defaultFileName));

        // 4. Set the File Filter
        FileNameExtensionFilter filter = 
                new FileNameExtensionFilter(format.name() + " Benchmark Files (*." + ext + ")", ext);
        fileChooser.setFileFilter(filter);
        fileChooser.setAcceptAllFileFilterUsed(false); // Force format

        // 5. Show Dialog
        int userSelection = fileChooser.showSaveDialog(Gui.mainFrame);

        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            String filePath = fileToSave.getAbsolutePath();

            // Ensure the extension is present
            if (!filePath.toLowerCase().endsWith("." + ext)) {
                fileToSave = new File(filePath + "." + ext);
            }

            try {
                writeBenchmark(benchmark, fileToSave.getAbsolutePath(), format);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "error while writing export file", e);
                JOptionPane.showMessageDialog(Gui.mainFrame, 
                        "error while writing " + fileToSave.getAbsolutePath() + ". " + e.getMessage(), 
                        "Export IO Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}