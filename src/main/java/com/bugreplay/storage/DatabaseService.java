package com.bugreplay.storage;

import com.bugreplay.model.ExecutionTrace;
import com.bugreplay.model.RunMetadata;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * DatabaseService manages the JDBC connection to MySQL and persists run metadata.
 *
 * Design highlights:
 * - Uses PreparedStatements to prevent SQL injection and ensure type safety.
 * - Graceful degradation: If MySQL is offline, unreachable, or unconfigured,
 *   all operations fail safely without throwing unhandled exceptions to the UI.
 *   The primary JSON trace files remain unaffected.
 * - Never falsely reports success when database insertion fails.
 */
public class DatabaseService {

    private static final String MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver";

    private final ConfigManager configManager;
    private boolean driverLoaded = false;
    private String lastErrorMessage = "";
    private boolean available = false;

    public DatabaseService() {
        this(new ConfigManager());
    }

    public DatabaseService(ConfigManager configManager) {
        this.configManager = configManager;
        try {
            Class.forName(MYSQL_DRIVER);
            driverLoaded = true;
        } catch (ClassNotFoundException e) {
            driverLoaded = false;
            lastErrorMessage = "MySQL JDBC driver (" + MYSQL_DRIVER + ") not found in classpath.";
        }
        testConnection();
    }

    /**
     * Checks if the database is enabled and actively reachable.
     */
    public synchronized boolean testConnection() {
        if (!configManager.isDbEnabled()) {
            available = false;
            lastErrorMessage = "Database integration is disabled in configuration (db.enabled=false).";
            return false;
        }
        if (!driverLoaded) {
            available = false;
            return false;
        }

        try (Connection conn = getConnection()) {
            if (conn != null && !conn.isClosed()) {
                available = true;
                lastErrorMessage = "";
                return true;
            }
        } catch (SQLException e) {
            available = false;
            lastErrorMessage = e.getMessage();
        }
        return false;
    }

    public boolean isAvailable() {
        return available;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    /**
     * Obtains a new database connection using credentials from ConfigManager.
     */
    public Connection getConnection() throws SQLException {
        DriverManager.setLoginTimeout(3); // 3-second timeout to avoid UI lag
        return DriverManager.getConnection(
                configManager.getDbUrl(),
                configManager.getDbUsername(),
                configManager.getDbPassword()
        );
    }

    /**
     * Saves run metadata into the run_history table using a PreparedStatement.
     *
     * @param trace     The execution trace.
     * @param traceFile The JSON file on disk.
     * @return true if successfully saved to MySQL, false otherwise.
     */
    public boolean saveRunMetadata(ExecutionTrace trace, File traceFile) {
        if (trace == null) return false;
        if (!available) {
            // Re-test in case database came online
            if (!testConnection()) {
                return false;
            }
        }

        RunMetadata meta = RunMetadata.fromTrace(trace, traceFile);

        String sql = "INSERT INTO run_history " +
                "(run_id, program_name, source_path, recorded_at, execution_status, exit_code, duration_ms, event_count, checkpoint_count, trace_file_path) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "execution_status = VALUES(execution_status), " +
                "exit_code = VALUES(exit_code), " +
                "duration_ms = VALUES(duration_ms), " +
                "event_count = VALUES(event_count), " +
                "checkpoint_count = VALUES(checkpoint_count)";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, meta.getRunId());
            stmt.setString(2, meta.getProgramName());
            stmt.setString(3, meta.getSourcePath());
            stmt.setString(4, meta.getRecordedAt());
            stmt.setString(5, meta.getExecutionStatus());
            stmt.setInt(6, meta.getExitCode());
            stmt.setLong(7, meta.getDurationMillis());
            stmt.setInt(8, meta.getEventCount());
            stmt.setInt(9, meta.getCheckpointCount());
            stmt.setString(10, meta.getTraceFilePath());

            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
            lastErrorMessage = "SQL Error while saving metadata: " + e.getMessage();
            System.err.println("[DatabaseService] " + lastErrorMessage);
            return false;
        }
    }

    /**
     * Retrieves the most recent runs recorded in the database.
     */
    public List<RunMetadata> listRecentRuns(int limit) {
        if (!available && !testConnection()) {
            return Collections.emptyList();
        }

        String sql = "SELECT run_id, program_name, source_path, recorded_at, execution_status, " +
                "exit_code, duration_ms, event_count, checkpoint_count, trace_file_path " +
                "FROM run_history ORDER BY recorded_at DESC LIMIT ?";

        List<RunMetadata> results = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, Math.max(limit, 1));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    RunMetadata meta = new RunMetadata(
                            rs.getString("run_id"),
                            rs.getString("program_name"),
                            rs.getString("source_path"),
                            rs.getString("recorded_at"),
                            rs.getString("execution_status"),
                            rs.getInt("exit_code"),
                            rs.getLong("duration_ms"),
                            rs.getInt("event_count"),
                            rs.getInt("checkpoint_count"),
                            rs.getString("trace_file_path")
                    );
                    results.add(meta);
                }
            }
        } catch (SQLException e) {
            lastErrorMessage = "Error querying run history: " + e.getMessage();
            System.err.println("[DatabaseService] " + lastErrorMessage);
        }
        return results;
    }

    /**
     * Queries a specific run by its run ID.
     */
    public RunMetadata getRunById(String runId) {
        if (!available && !testConnection()) {
            return null;
        }

        String sql = "SELECT run_id, program_name, source_path, recorded_at, execution_status, " +
                "exit_code, duration_ms, event_count, checkpoint_count, trace_file_path " +
                "FROM run_history WHERE run_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, runId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new RunMetadata(
                            rs.getString("run_id"),
                            rs.getString("program_name"),
                            rs.getString("source_path"),
                            rs.getString("recorded_at"),
                            rs.getString("execution_status"),
                            rs.getInt("exit_code"),
                            rs.getLong("duration_ms"),
                            rs.getInt("event_count"),
                            rs.getInt("checkpoint_count"),
                            rs.getString("trace_file_path")
                    );
                }
            }
        } catch (SQLException e) {
            lastErrorMessage = "Error querying run by ID: " + e.getMessage();
            System.err.println("[DatabaseService] " + lastErrorMessage);
        }
        return null;
    }
}
