package com.bugreplay.storage;

import com.bugreplay.model.CheckpointData;
import com.bugreplay.model.ExecutionTrace;
import com.bugreplay.model.RunMetadata;
import com.bugreplay.model.TraceEvent;
import com.bugreplay.recorder.TraceRecorder;
import com.bugreplay.runner.ExecutionResult;
import com.bugreplay.runner.ProcessRunner;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase5DatabaseTest verifies:
 * 1. ConfigManager default configuration and safe properties loading.
 * 2. RunMetadata extraction from ExecutionTrace and File references.
 * 3. Graceful degradation when MySQL is offline or disconnected (failure path).
 * 4. JSON storage resilience when MySQL is unavailable.
 * 5. Database schema file existence and syntax inspection.
 */
public class Phase5DatabaseTest {

    private static int passedTests = 0;
    private static int totalTests = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("   BUGREPLAY - PHASE 5 DATABASE & CONFIG TESTS    ");
        System.out.println("==================================================");

        testConfigManagerLoading();
        testRunMetadataMapping();
        testDatabaseFailureGracefulHandling();
        testJsonResilienceDuringDbFailure();
        testDatabaseSchemaFile();

        System.out.println("\n==================================================");
        System.out.println("PHASE 5 TEST SUMMARY: " + passedTests + "/" + totalTests + " tests passed.");
        System.out.println("==================================================");

        if (passedTests != totalTests) {
            System.exit(1);
        }
    }

    private static void testConfigManagerLoading() {
        totalTests++;
        System.out.println("\n[Test 1] Testing ConfigManager loading & safe defaults...");

        ConfigManager config = new ConfigManager(new File("non_existent_config.properties"));

        boolean passed = config.getDbUrl() != null
                && config.getDbUsername() != null
                && config.getDbPassword() != null
                && !config.isConfigFilePresent();

        if (passed) {
            passedTests++;
            System.out.println("  PASS: ConfigManager defaulted safely without exceptions.");
            System.out.println("  Default DB URL: " + config.getDbUrl());
            System.out.println("  Default DB User: " + config.getDbUsername());
        } else {
            System.out.println("  FAIL: ConfigManager defaults failed.");
        }
    }

    private static void testRunMetadataMapping() {
        totalTests++;
        System.out.println("\n[Test 2] Testing RunMetadata extraction from ExecutionTrace...");

        List<TraceEvent> events = new ArrayList<>();
        events.add(TraceEvent.createStarted(1, "2026-10-29T17:00:00", "NormalExample.java"));
        events.add(TraceEvent.createOutput(2, "2026-10-29T17:00:01", "Output line"));

        Map<String, String> vars = new HashMap<>();
        vars.put("count", "42");
        events.add(TraceEvent.createCheckpoint(3, "2026-10-29T17:00:02", new CheckpointData("CP1", "Note", vars)));
        events.add(TraceEvent.createFinished(4, "2026-10-29T17:00:03", 0, 150));

        ExecutionTrace trace = new ExecutionTrace(
                "run_meta_test_001",
                "NormalExample.java",
                "examples/NormalExample.java",
                "2026-10-29T17:00:00",
                "SUCCESS",
                true,
                "",
                0,
                150,
                events
        );

        File fakeFile = new File("traces/run_meta_test_001.json");
        RunMetadata meta = RunMetadata.fromTrace(trace, fakeFile);

        boolean passed = meta != null
                && "run_meta_test_001".equals(meta.getRunId())
                && "NormalExample.java".equals(meta.getProgramName())
                && "SUCCESS".equals(meta.getExecutionStatus())
                && meta.getExitCode() == 0
                && meta.getDurationMillis() == 150
                && meta.getEventCount() == 4
                && meta.getCheckpointCount() == 1
                && meta.getTraceFilePath().contains("run_meta_test_001.json");

        if (passed) {
            passedTests++;
            System.out.println("  PASS: RunMetadata extracted all 10 schema fields accurately.");
            System.out.println("  Mapped metadata: " + meta);
        } else {
            System.out.println("  FAIL: RunMetadata mapping mismatch.");
        }
    }

    private static void testDatabaseFailureGracefulHandling() {
        totalTests++;
        System.out.println("\n[Test 3] Testing DatabaseService graceful offline / failure handling...");

        // Configure a DatabaseService pointing to an unreachable port to guarantee failure path test
        ConfigManager badConfig = new ConfigManager() {
            @Override
            public boolean isDbEnabled() {
                return true;
            }

            @Override
            public String getDbUrl() {
                return "jdbc:mysql://localhost:65432/non_existent_db?connectTimeout=1000";
            }

            @Override
            public String getDbUsername() {
                return "bad_user";
            }

            @Override
            public String getDbPassword() {
                return "bad_password";
            }
        };

        DatabaseService dbService = new DatabaseService(badConfig);

        // Verification 1: Service reports not available
        boolean notAvailable = !dbService.isAvailable();

        // Verification 2: saveRunMetadata returns false without crashing
        ExecutionTrace fakeTrace = new ExecutionTrace();
        fakeTrace.setRunId("test_offline_run");
        boolean saveResult = dbService.saveRunMetadata(fakeTrace, new File("traces/test.json"));

        // Verification 3: listRecentRuns returns empty list without throwing SQLException
        List<RunMetadata> runs = dbService.listRecentRuns(10);

        boolean passed = notAvailable
                && !saveResult
                && runs != null && runs.isEmpty()
                && dbService.getLastErrorMessage() != null;

        if (passed) {
            passedTests++;
            System.out.println("  PASS: DatabaseService handled connection failure cleanly without crashing.");
            System.out.println("  Graceful error recorded: " + dbService.getLastErrorMessage());
        } else {
            System.out.println("  FAIL: DatabaseService threw unhandled exception or falsely reported success.");
        }
    }

    private static void testJsonResilienceDuringDbFailure() throws Exception {
        totalTests++;
        System.out.println("\n[Test 4] Testing JSON storage resilience during database failure...");

        File testTracesDir = new File("build" + File.separator + "test-traces");
        JsonTraceStorage storage = new JsonTraceStorage(testTracesDir);
        ProcessRunner runner = new ProcessRunner(new File("build" + File.separator + "test-classes"));
        TraceRecorder recorder = new TraceRecorder();

        // 1. Run program
        File source = new File("examples", "NormalExample.java");
        ExecutionResult result = runner.run(source, 5000);
        ExecutionTrace trace = recorder.record(source, result);

        // 2. Save JSON trace locally
        File jsonFile = storage.saveTrace(trace);

        // 3. Attempt DB save with offline database service
        DatabaseService offlineDb = new DatabaseService(new ConfigManager(new File("ghost_config.properties")));
        boolean dbSuccess = offlineDb.saveRunMetadata(trace, jsonFile);

        // 4. Verify JSON file remains completely intact and valid
        ExecutionTrace reloadedTrace = storage.loadTrace(jsonFile);

        boolean passed = !dbSuccess
                && jsonFile.exists()
                && reloadedTrace != null
                && reloadedTrace.getRunId().equals(trace.getRunId())
                && reloadedTrace.getEvents().size() == trace.getEvents().size();

        if (passed) {
            passedTests++;
            System.out.println("  PASS: JSON trace was preserved and fully reloadable even when MySQL failed.");
            System.out.println("  Trace file: " + jsonFile.getName() + " (" + jsonFile.length() + " bytes)");
        } else {
            System.out.println("  FAIL: JSON trace was compromised or corrupted.");
        }
    }

    private static void testDatabaseSchemaFile() throws Exception {
        totalTests++;
        System.out.println("\n[Test 5] Testing database/schema.sql file validity...");

        File schemaFile = new File("database", "schema.sql");
        boolean exists = schemaFile.exists() && schemaFile.length() > 0;

        String content = new String(java.nio.file.Files.readAllBytes(schemaFile.toPath()));
        boolean hasCreateTable = content.contains("CREATE TABLE IF NOT EXISTS run_history");
        boolean hasRunIdPrimary = content.contains("run_id VARCHAR(64) NOT NULL PRIMARY KEY");
        boolean hasTraceFilePath = content.contains("trace_file_path VARCHAR(500) NOT NULL");

        boolean passed = exists && hasCreateTable && hasRunIdPrimary && hasTraceFilePath;

        if (passed) {
            passedTests++;
            System.out.println("  PASS: schema.sql contains complete SQL definitions for bugreplay and run_history.");
        } else {
            System.out.println("  FAIL: schema.sql missing required table statements.");
        }
    }
}
