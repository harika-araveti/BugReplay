package com.bugreplay.storage;

import com.bugreplay.model.ExecutionTrace;
import com.bugreplay.model.TraceEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * JsonTraceStorage handles persistent storage of ExecutionTrace objects as formatted JSON files.
 *
 * Responsibilities:
 * - Serialize ExecutionTrace to indented, readable JSON using Gson.
 * - Enforce non-destructive saves (never silently overwrite a previous trace).
 * - Validate integrity upon loading (correct format version, non-null fields, monotonically increasing sequence numbers).
 * - Provide clear error explanations when a file is missing, corrupt, or malformed.
 */
public class JsonTraceStorage {

    private final File traceDirectory;
    private final Gson gson;

    public JsonTraceStorage() {
        this(new File("traces"));
    }

    public JsonTraceStorage(File traceDirectory) {
        this.traceDirectory = traceDirectory;
        if (!this.traceDirectory.exists()) {
            this.traceDirectory.mkdirs();
        }
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
    }

    /**
     * Saves an ExecutionTrace to disk as a JSON file.
     *
     * @param trace The trace to save.
     * @return The File where the trace was written.
     * @throws TraceStorageException If saving fails or the file already exists.
     */
    public File saveTrace(ExecutionTrace trace) throws TraceStorageException {
        if (trace == null) {
            throw new TraceStorageException("Cannot save null execution trace.");
        }
        if (trace.getRunId() == null || trace.getRunId().trim().isEmpty()) {
            throw new TraceStorageException("Trace must have a valid non-empty runId.");
        }

        if (!traceDirectory.exists()) {
            traceDirectory.mkdirs();
        }

        String safeFileName = sanitizeFileName(trace.getRunId()) + ".json";
        File targetFile = new File(traceDirectory, safeFileName);

        // Constraint: Do not silently overwrite a previous trace
        if (targetFile.exists()) {
            throw new TraceStorageException("Trace file already exists: " + targetFile.getAbsolutePath()
                    + ". Silent overwriting is disabled to protect recorded evidence.");
        }

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(targetFile), StandardCharsets.UTF_8))) {
            gson.toJson(trace, writer);
            writer.flush();
            return targetFile;
        } catch (IOException e) {
            throw new TraceStorageException("Failed to write trace JSON file: " + targetFile.getName(), e);
        }
    }

    /**
     * Loads, parses, and validates an ExecutionTrace from a JSON file.
     *
     * @param file The JSON trace file.
     * @return The deserialized ExecutionTrace.
     * @throws TraceStorageException If the file cannot be read, is malformed, or fails validation.
     */
    public ExecutionTrace loadTrace(File file) throws TraceStorageException {
        if (file == null || !file.exists()) {
            throw new TraceStorageException("Trace file does not exist: " + (file == null ? "null" : file.getPath()));
        }
        if (!file.isFile() || !file.canRead()) {
            throw new TraceStorageException("Trace file is not readable: " + file.getAbsolutePath());
        }

        ExecutionTrace trace;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            trace = gson.fromJson(reader, ExecutionTrace.class);
        } catch (JsonParseException e) {
            throw new TraceStorageException("Trace file is malformed or contains invalid JSON syntax (" + file.getName() + "): " + e.getMessage(), e);
        } catch (IOException e) {
            throw new TraceStorageException("I/O error while reading trace file: " + file.getName(), e);
        }

        // Validate structure and integrity
        validateTrace(trace, file.getName());

        return trace;
    }

    /**
     * Loads a trace by its run ID.
     */
    public ExecutionTrace loadTraceByRunId(String runId) throws TraceStorageException {
        File targetFile = new File(traceDirectory, sanitizeFileName(runId) + ".json");
        return loadTrace(targetFile);
    }

    /**
     * Lists all saved .json trace files in the traces directory, ordered newest to oldest.
     */
    public List<File> listTraceFiles() {
        if (!traceDirectory.exists() || !traceDirectory.isDirectory()) {
            return Collections.emptyList();
        }

        File[] files = traceDirectory.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }

        List<File> list = new ArrayList<>(Arrays.asList(files));
        // Sort descending by last modified time (most recent first)
        Collections.sort(list, new Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                return Long.compare(f2.lastModified(), f1.lastModified());
            }
        });
        return list;
    }

    public File getTraceDirectory() {
        return traceDirectory;
    }

    /**
     * Verifies that the loaded trace conforms to integrity rules:
     * - Required metadata fields are populated.
     * - Trace format version is supported.
     * - Events are strictly ordered by sequence number (1, 2, 3...) without gaps or reversal.
     */
    private void validateTrace(ExecutionTrace trace, String fileName) throws TraceStorageException {
        if (trace == null) {
            throw new TraceStorageException("Parsed trace object is null from file: " + fileName);
        }
        if (trace.getRunId() == null || trace.getRunId().trim().isEmpty()) {
            throw new TraceStorageException("Trace in " + fileName + " is missing required 'runId'.");
        }
        if (trace.getEvents() == null) {
            throw new TraceStorageException("Trace in " + fileName + " contains null events list.");
        }

        // Verify format version
        if (trace.getFormatVersion() == null || !ExecutionTrace.CURRENT_FORMAT_VERSION.equals(trace.getFormatVersion())) {
            throw new TraceStorageException("Unsupported or missing trace format version: "
                    + trace.getFormatVersion() + " in " + fileName + " (expected: " + ExecutionTrace.CURRENT_FORMAT_VERSION + ")");
        }

        // Verify monotonic event ordering
        int expectedSeq = 1;
        for (TraceEvent event : trace.getEvents()) {
            if (event == null) {
                throw new TraceStorageException("Trace contains a null event in sequence at position " + expectedSeq);
            }
            if (event.getSequenceNumber() != expectedSeq) {
                throw new TraceStorageException("Trace event ordering corrupted in " + fileName
                        + ": expected sequence #" + expectedSeq + " but found #" + event.getSequenceNumber());
            }
            if (event.getType() == null) {
                throw new TraceStorageException("Trace event #" + expectedSeq + " has null event type.");
            }
            expectedSeq++;
        }
    }

    private String sanitizeFileName(String input) {
        return input.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
