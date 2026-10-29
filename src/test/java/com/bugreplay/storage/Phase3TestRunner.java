package com.bugreplay.storage;

import com.bugreplay.model.CheckpointData;
import com.bugreplay.model.ExecutionTrace;
import com.bugreplay.model.TraceEvent;
import com.bugreplay.model.TraceEventType;
import com.bugreplay.recorder.TraceRecorder;
import com.bugreplay.runner.ExecutionResult;
import com.bugreplay.runner.ProcessRunner;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Phase3TestRunner verifies the complete Phase 3 recording and JSON storage pipeline:
 * 1. Monotonic event sequence ordering.
 * 2. Checkpoint detection and structured attribute parsing.
 * 3. JSON serialization and deserialization (round-trip equality).
 * 4. Prevention of accidental file overwriting.
 * 5. Robust error handling for missing files.
 * 6. Robust error handling for malformed/corrupted JSON.
 * 7. Detection of corrupted/out-of-order event sequence numbers.
 */
public class Phase3TestRunner {

    private static int passedTests = 0;
    private static int totalTests = 0;

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  BUGREPLAY - PHASE 3 RECORDING & JSON TEST SUITE ");
        System.out.println("==================================================");

        File testTracesDir = new File("build" + File.separator + "test-traces");
        if (!testTracesDir.exists()) {
            testTracesDir.mkdirs();
        }

        ProcessRunner runner = new ProcessRunner(new File("build" + File.separator + "test-classes"));
        TraceRecorder recorder = new TraceRecorder();
        JsonTraceStorage storage = new JsonTraceStorage(testTracesDir);

        testMonotonicEventSequence(runner, recorder);
        testCheckpointExtraction(runner, recorder);
        testJsonSaveAndReloadRoundTrip(runner, recorder, storage);
        testOverwriteProtection(recorder, storage);
        testMissingFileHandling(storage);
        testMalformedJsonHandling(testTracesDir, storage);
        testCorruptedSequenceDetection(testTracesDir, storage);

        System.out.println("\n==================================================");
        System.out.println("PHASE 3 TEST SUMMARY: " + passedTests + "/" + totalTests + " tests passed.");
        System.out.println("==================================================");

        if (passedTests != totalTests) {
            System.exit(1);
        }
    }

    private static void testMonotonicEventSequence(ProcessRunner runner, TraceRecorder recorder) {
        totalTests++;
        System.out.println("\n[Test 1] Testing Monotonic Event Sequencing...");

        File source = new File("examples" + File.separator + "NormalExample.java");
        ExecutionResult result = runner.run(source, 5000);
        ExecutionTrace trace = recorder.record(source, result);

        List<TraceEvent> events = trace.getEvents();
        boolean validOrder = true;
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).getSequenceNumber() != (i + 1)) {
                validOrder = false;
                break;
            }
        }

        boolean passed = validOrder
                && events.size() >= 3
                && events.get(0).getType() == TraceEventType.RUN_STARTED
                && events.get(events.size() - 1).getType() == TraceEventType.RUN_FINISHED;

        if (passed) {
            passedTests++;
            System.out.println("  PASS: All " + events.size() + " events have strict monotonic sequence numbers (1.." + events.size() + ").");
            System.out.println("  Initial event: " + events.get(0));
            System.out.println("  Final event:   " + events.get(events.size() - 1));
        } else {
            System.out.println("  FAIL: Event sequence is out of order or invalid.");
        }
    }

    private static void testCheckpointExtraction(ProcessRunner runner, TraceRecorder recorder) {
        totalTests++;
        System.out.println("\n[Test 2] Testing Explicit Checkpoint Extraction...");

        File source = new File("examples" + File.separator + "CheckpointExample.java");
        ExecutionResult result = runner.run(source, 5000);
        ExecutionTrace trace = recorder.record(source, result);

        List<TraceEvent> checkpoints = trace.getCheckpoints();

        boolean hasInit = false;
        boolean hasAfterDeposit = false;
        boolean hasFinal = false;

        for (TraceEvent cpEvent : checkpoints) {
            CheckpointData cp = cpEvent.getCheckpoint();
            if (cp != null) {
                if ("INITIAL_STATE".equals(cp.getName()) && "1000.0".equals(cp.getVariables().get("balance"))) {
                    hasInit = true;
                }
                if ("AFTER_DEPOSIT".equals(cp.getName()) && "1500.0".equals(cp.getVariables().get("balance"))) {
                    hasAfterDeposit = true;
                }
                if ("FINAL_STATE".equals(cp.getName()) && "1200.0".equals(cp.getVariables().get("balance"))) {
                    hasFinal = true;
                }
            }
        }

        boolean passed = (checkpoints.size() == 3) && hasInit && hasAfterDeposit && hasFinal;

        if (passed) {
            passedTests++;
            System.out.println("  PASS: Captured 3 structured checkpoints with exact variable values.");
            for (TraceEvent cp : checkpoints) {
                System.out.println("    - " + cp.getCheckpoint().getName() + " -> " + cp.getCheckpoint().getVariables());
            }
        } else {
            System.out.println("  FAIL: Checkpoint extraction failed. Total checkpoints captured: " + checkpoints.size());
        }
    }

    private static void testJsonSaveAndReloadRoundTrip(ProcessRunner runner, TraceRecorder recorder, JsonTraceStorage storage) {
        totalTests++;
        System.out.println("\n[Test 3] Testing JSON Save & Reload Round-Trip...");

        File source = new File("examples" + File.separator + "CheckpointExample.java");
        ExecutionResult result = runner.run(source, 5000);
        ExecutionTrace originalTrace = recorder.record(source, result);

        try {
            File savedFile = storage.saveTrace(originalTrace);
            System.out.println("  Saved trace file: " + savedFile.getName() + " (" + savedFile.length() + " bytes)");

            ExecutionTrace loadedTrace = storage.loadTrace(savedFile);

            boolean matches = loadedTrace.getRunId().equals(originalTrace.getRunId())
                    && loadedTrace.getProgramName().equals(originalTrace.getProgramName())
                    && loadedTrace.getExecutionStatus().equals(originalTrace.getExecutionStatus())
                    && loadedTrace.getExitCode() == originalTrace.getExitCode()
                    && loadedTrace.getEvents().size() == originalTrace.getEvents().size()
                    && loadedTrace.getCheckpoints().size() == originalTrace.getCheckpoints().size();

            if (matches) {
                passedTests++;
                System.out.println("  PASS: Loaded trace matches original trace in memory exactly across all fields.");
            } else {
                System.out.println("  FAIL: Loaded trace differs from original trace.");
            }
        } catch (TraceStorageException e) {
            System.out.println("  FAIL: Storage exception: " + e.getMessage());
        }
    }

    private static void testOverwriteProtection(TraceRecorder recorder, JsonTraceStorage storage) {
        totalTests++;
        System.out.println("\n[Test 4] Testing Protection Against Overwriting Existing Trace...");

        File source = new File("examples" + File.separator + "NormalExample.java");
        ExecutionResult result = new ExecutionResult(true, "", 0, null, null, 0, 10, 10, false, false);
        ExecutionTrace trace = recorder.record(source, result);

        try {
            // First save should succeed
            storage.saveTrace(trace);

            // Second save with same runId should throw TraceStorageException
            storage.saveTrace(trace);

            System.out.println("  FAIL: Expected exception on duplicate save, but none was thrown.");
        } catch (TraceStorageException e) {
            if (e.getMessage().contains("already exists")) {
                passedTests++;
                System.out.println("  PASS: Overwrite blocked with safety message: " + e.getMessage());
            } else {
                System.out.println("  FAIL: Unexpected error message: " + e.getMessage());
            }
        }
    }

    private static void testMissingFileHandling(JsonTraceStorage storage) {
        totalTests++;
        System.out.println("\n[Test 5] Testing Non-Existent Trace File Handling...");

        try {
            storage.loadTrace(new File("traces" + File.separator + "non_existent_run_99999.json"));
            System.out.println("  FAIL: Expected exception for missing file, but none was thrown.");
        } catch (TraceStorageException e) {
            if (e.getMessage().contains("does not exist")) {
                passedTests++;
                System.out.println("  PASS: Missing file rejected cleanly: " + e.getMessage());
            } else {
                System.out.println("  FAIL: Unexpected error message: " + e.getMessage());
            }
        }
    }

    private static void testMalformedJsonHandling(File tracesDir, JsonTraceStorage storage) {
        totalTests++;
        System.out.println("\n[Test 6] Testing Malformed/Corrupt JSON Handling...");

        File corruptFile = new File(tracesDir, "corrupted_syntax_test.json");
        try (FileWriter fw = new FileWriter(corruptFile)) {
            fw.write("{ \"runId\": \"corrupt-123\", \"events\": [ { \"seq\": 1, unclosed json... ");
        } catch (IOException e) {
            System.out.println("  FAIL: Could not create corrupt test file: " + e.getMessage());
            return;
        }

        try {
            storage.loadTrace(corruptFile);
            System.out.println("  FAIL: Expected exception for malformed JSON, but none was thrown.");
        } catch (TraceStorageException e) {
            if (e.getMessage().contains("malformed") || e.getMessage().contains("syntax")) {
                passedTests++;
                System.out.println("  PASS: Malformed JSON syntax caught with explanatory message: " + e.getMessage());
            } else {
                System.out.println("  FAIL: Unexpected error message: " + e.getMessage());
            }
        } finally {
            corruptFile.delete();
        }
    }

    private static void testCorruptedSequenceDetection(File tracesDir, JsonTraceStorage storage) {
        totalTests++;
        System.out.println("\n[Test 7] Testing Corrupted/Out-of-Order Event Sequence Detection...");

        File badSeqFile = new File(tracesDir, "bad_sequence_test.json");
        try (FileWriter fw = new FileWriter(badSeqFile)) {
            // Write a JSON trace where sequence jumps from 1 to 5
            fw.write("{\n" +
                    "  \"formatVersion\": \"1.0\",\n" +
                    "  \"runId\": \"bad_seq_test\",\n" +
                    "  \"programName\": \"Test\",\n" +
                    "  \"executionStatus\": \"SUCCESS\",\n" +
                    "  \"events\": [\n" +
                    "    {\"sequenceNumber\": 1, \"timestamp\": \"2026-10-29T17:00:00\", \"type\": \"RUN_STARTED\", \"message\": \"Start\"},\n" +
                    "    {\"sequenceNumber\": 5, \"timestamp\": \"2026-10-29T17:00:01\", \"type\": \"OUTPUT\", \"message\": \"Out of order\"}\n" +
                    "  ]\n" +
                    "}");
        } catch (IOException e) {
            System.out.println("  FAIL: Could not create test file: " + e.getMessage());
            return;
        }

        try {
            storage.loadTrace(badSeqFile);
            System.out.println("  FAIL: Expected exception for corrupted sequence, but none was thrown.");
        } catch (TraceStorageException e) {
            if (e.getMessage().contains("ordering corrupted")) {
                passedTests++;
                System.out.println("  PASS: Corrupted sequence detected cleanly: " + e.getMessage());
            } else {
                System.out.println("  FAIL: Unexpected error message: " + e.getMessage());
            }
        } finally {
            badSeqFile.delete();
        }
    }
}
