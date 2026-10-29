package com.bugreplay.model;

import java.io.File;

/**
 * RunMetadata represents the lightweight indexable metadata for an execution run.
 *
 * This information is mirrored in MySQL for fast querying and history filtering,
 * while referencing the primary JSON file on disk for full event inspection.
 */
public class RunMetadata {

    private String runId;
    private String programName;
    private String sourcePath;
    private String recordedAt;
    private String executionStatus;
    private int exitCode;
    private long durationMillis;
    private int eventCount;
    private int checkpointCount;
    private String traceFilePath;

    public RunMetadata() {
    }

    public RunMetadata(
            String runId,
            String programName,
            String sourcePath,
            String recordedAt,
            String executionStatus,
            int exitCode,
            long durationMillis,
            int eventCount,
            int checkpointCount,
            String traceFilePath) {
        this.runId = runId;
        this.programName = programName;
        this.sourcePath = sourcePath;
        this.recordedAt = recordedAt;
        this.executionStatus = executionStatus;
        this.exitCode = exitCode;
        this.durationMillis = durationMillis;
        this.eventCount = eventCount;
        this.checkpointCount = checkpointCount;
        this.traceFilePath = traceFilePath;
    }

    /**
     * Extracts metadata from an in-memory ExecutionTrace and its saved JSON File.
     */
    public static RunMetadata fromTrace(ExecutionTrace trace, File traceFile) {
        if (trace == null) return null;

        String path = (traceFile != null) ? traceFile.getAbsolutePath() : "";
        int events = (trace.getEvents() != null) ? trace.getEvents().size() : 0;
        int checkpoints = (trace.getCheckpoints() != null) ? trace.getCheckpoints().size() : 0;

        return new RunMetadata(
                trace.getRunId(),
                trace.getProgramName(),
                trace.getSourcePath(),
                trace.getRecordedAt(),
                trace.getExecutionStatus(),
                trace.getExitCode(),
                trace.getDurationMillis(),
                events,
                checkpoints,
                path
        );
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

    public int getEventCount() {
        return eventCount;
    }

    public void setEventCount(int eventCount) {
        this.eventCount = eventCount;
    }

    public int getCheckpointCount() {
        return checkpointCount;
    }

    public void setCheckpointCount(int checkpointCount) {
        this.checkpointCount = checkpointCount;
    }

    public String getTraceFilePath() {
        return traceFilePath;
    }

    public void setTraceFilePath(String traceFilePath) {
        this.traceFilePath = traceFilePath;
    }

    @Override
    public String toString() {
        return "RunMetadata{" +
                "runId='" + runId + '\'' +
                ", program='" + programName + '\'' +
                ", status='" + executionStatus + '\'' +
                ", events=" + eventCount +
                '}';
    }
}
