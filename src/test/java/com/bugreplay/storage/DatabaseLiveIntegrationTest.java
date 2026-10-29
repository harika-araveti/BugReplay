package com.bugreplay.storage;

import com.bugreplay.model.ExecutionTrace;
import com.bugreplay.model.RunMetadata;
import com.bugreplay.recorder.TraceRecorder;
import com.bugreplay.runner.ExecutionResult;
import com.bugreplay.runner.ProcessRunner;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

/**
 * DatabaseLiveIntegrationTest verifies the live MySQL database integration:
 * 1. Checks that ConfigManager reads credentials from config.properties.
 * 2. Tests JDBC driver availability and active database connection.
 * 3. Verifies that execution metadata is written to MySQL without touching JSON traces.
 * 4. Verifies listRecentRuns and getRunById query methods.
 * 5. Verifies physical persistence in the bugreplay.run_history table.
 */
public class DatabaseLiveIntegrationTest {

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("   BUGREPLAY - LIVE MYSQL INTEGRATION VERIFICATION ");
        System.out.println("==================================================");

        // 1. ConfigManager verification
        ConfigManager config = new ConfigManager();
        if (!config.isConfigFilePresent()) {
            throw new RuntimeException("FAIL: config.properties not detected!");
        }
        if (!config.isDbEnabled()) {
            throw new RuntimeException("FAIL: db.enabled is not true!");
        }
        System.out.println("[Check 1] ConfigManager loaded config.properties successfully.");
        System.out.println("  DB URL: " + config.getDbUrl());
        System.out.println("  DB User: " + config.getDbUsername());

        // 2. DatabaseService connectivity
        DatabaseService dbService = new DatabaseService(config);
        if (!dbService.isAvailable()) {
            throw new RuntimeException("FAIL: DatabaseService connection failed: " + dbService.getLastErrorMessage());
        }
        System.out.println("[Check 2] DatabaseService connected to MySQL successfully! (DB: MySQL Connected)");

        // 3. Run a test program to generate a real trace
        File source = new File("examples", "NormalExample.java");
        ProcessRunner runner = new ProcessRunner();
        TraceRecorder recorder = new TraceRecorder();
        JsonTraceStorage storage = new JsonTraceStorage(new File("traces"));

        ExecutionResult result = runner.run(source, 5000);
        ExecutionTrace trace = recorder.record(source, result);
        File jsonFile = storage.saveTrace(trace);
        System.out.println("[Check 3] Primary JSON trace saved to: " + jsonFile.getName() + " (" + jsonFile.length() + " bytes)");

        // 4. Save metadata to MySQL
        boolean saved = dbService.saveRunMetadata(trace, jsonFile);
        if (!saved) {
            throw new RuntimeException("FAIL: saveRunMetadata returned false. Error: " + dbService.getLastErrorMessage());
        }
        System.out.println("[Check 4] Metadata successfully mirrored to MySQL run_history table!");

        // 5. Query recent runs
        List<RunMetadata> recent = dbService.listRecentRuns(10);
        if (recent.isEmpty()) {
            throw new RuntimeException("FAIL: listRecentRuns returned empty list.");
        }
        boolean found = false;
        for (RunMetadata meta : recent) {
            if (meta.getRunId().equals(trace.getRunId())) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new RuntimeException("FAIL: Saved runId " + trace.getRunId() + " not found in recent runs.");
        }
        System.out.println("[Check 5] Verified listRecentRuns contains runId: " + trace.getRunId());

        // 6. Query by ID
        RunMetadata fetched = dbService.getRunById(trace.getRunId());
        if (fetched == null) {
            throw new RuntimeException("FAIL: getRunById returned null.");
        }
        if (!"NormalExample.java".equals(fetched.getProgramName()) || fetched.getEventCount() <= 0) {
            throw new RuntimeException("FAIL: Fetched metadata contents do not match trace.");
        }
        System.out.println("[Check 6] Verified getRunById: " + fetched);

        // 7. Direct JDBC verification to confirm table row count
        try (Connection conn = dbService.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM run_history WHERE run_id = '" + trace.getRunId() + "'")) {
            if (rs.next()) {
                int count = rs.getInt("cnt");
                if (count != 1) {
                    throw new RuntimeException("FAIL: Expected 1 row in run_history, found " + count);
                }
            }
        }
        System.out.println("[Check 7] Direct SQL verification confirmed exactly 1 row in MySQL run_history.");

        System.out.println("\n==================================================");
        System.out.println("ALL 7 MYSQL LIVE INTEGRATION CHECKS PASSED (100%)");
        System.out.println("==================================================");
    }
}
