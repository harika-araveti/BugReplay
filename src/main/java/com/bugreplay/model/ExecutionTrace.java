package com.bugreplay.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ExecutionTrace is the complete recorded history of a single program execution.
 *
 * It contains high-level metadata (run ID, program name, status, timings, exit code)
 * and an ordered list of all TraceEvent objects captured during the run.
 */
public class ExecutionTrace {

    public static final String CURRENT_FORMAT_VERSION = "1.0";

    private String formatVersion;
    private String runId;
    private String programName;
    private String sourcePath;
    private String recordedAt;
    private String executionStatus;
    private boolean compilationSuccess;
    private String compilationError;
    private int exitCode;
    private long durationMillis;
    private List<TraceEvent> events;

    // No-arg constructor required for Gson deserialization
    public ExecutionTrace() {
        this.formatVersion = CURRENT_FORMAT_VERSION;
        this.events = new ArrayList<>();
    }

    public ExecutionTrace(
            String runId,
            String programName,
            String sourcePath,
            String recordedAt,
            String executionStatus,
            boolean compilationSuccess,
            String compilationError,
            int exitCode,
            long durationMillis,
            List<TraceEvent> events) {
        this.formatVersion = CURRENT_FORMAT_VERSION;
        this.runId = runId;
        this.programName = programName;
        this.sourcePath = sourcePath;
        this.recordedAt = recordedAt;
        this.executionStatus = executionStatus;
        this.compilationSuccess = compilationSuccess;
        this.compilationError = compilationError;
        this.exitCode = exitCode;
        this.durationMillis = durationMillis;
        this.events = (events != null) ? new ArrayList<>(events) : new ArrayList<TraceEvent>();
    }

    public String getFormatVersion() {
        return formatVersion;
    }

    public void setFormatVersion(String formatVersion) {
        this.formatVersion = formatVersion;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getProgramName() {
        return programName;
    }

    public void setProgramName(String programName) {
        this.programName = programName;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public String getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(String recordedAt) {
        this.recordedAt = recordedAt;
    }

    public String getExecutionStatus() {
        return executionStatus;
    }

    public void setExecutionStatus(String executionStatus) {
        this.executionStatus = executionStatus;
    }

    public boolean isCompilationSuccess() {
        return compilationSuccess;
    }

    public void setCompilationSuccess(boolean compilationSuccess) {
        this.compilationSuccess = compilationSuccess;
    }

    public String getCompilationError() {
        return compilationError;
    }

    public void setCompilationError(String compilationError) {
        this.compilationError = compilationError;
    }

    public int getExitCode() {
        return exitCode;
    }

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(long durationMillis) {
        this.durationMillis = durationMillis;
    }

    public List<TraceEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public void setEvents(List<TraceEvent> events) {
        this.events = (events != null) ? new ArrayList<>(events) : new ArrayList<TraceEvent>();
    }

    public void addEvent(TraceEvent event) {
        if (this.events == null) {
            this.events = new ArrayList<>();
        }
        this.events.add(event);
    }

    /**
     * Filters and returns only the CHECKPOINT events from this trace.
     */
    public List<TraceEvent> getCheckpoints() {
        List<TraceEvent> checkpoints = new ArrayList<>();
        for (TraceEvent event : events) {
            if (event.getType() == TraceEventType.CHECKPOINT) {
                checkpoints.add(event);
            }
        }
        return checkpoints;
    }

    /**
     * Returns a specific event by its 1-indexed sequence number, or null if not found.
     */
    public TraceEvent getEventBySequence(int seq) {
        for (TraceEvent event : events) {
            if (event.getSequenceNumber() == seq) {
                return event;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "ExecutionTrace{" +
                "runId='" + runId + '\'' +
                ", programName='" + programName + '\'' +
                ", executionStatus='" + executionStatus + '\'' +
                ", exitCode=" + exitCode +
                ", durationMillis=" + durationMillis + "ms" +
                ", eventCount=" + (events != null ? events.size() : 0) +
                '}';
    }
}
