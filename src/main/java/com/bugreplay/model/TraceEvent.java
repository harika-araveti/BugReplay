package com.bugreplay.model;

/**
 * TraceEvent represents a single, discrete event that occurred during a program execution.
 *
 * Each event is strictly ordered by a monotonically increasing sequence number (1, 2, 3...).
 * It carries an exact timestamp, an event type, a readable message or line, and optional
 * structured checkpoint data.
 */
public class TraceEvent {

    private int sequenceNumber;
    private String timestamp;
    private TraceEventType type;
    private String message;
    private CheckpointData checkpoint;

    // No-arg constructor required for Gson deserialization
    public TraceEvent() {
    }

    public TraceEvent(int sequenceNumber, String timestamp, TraceEventType type, String message, CheckpointData checkpoint) {
        this.sequenceNumber = sequenceNumber;
        this.timestamp = timestamp;
        this.type = type;
        this.message = (message != null) ? message : "";
        this.checkpoint = checkpoint;
    }

    public static TraceEvent createStarted(int seq, String timestamp, String programName) {
        return new TraceEvent(seq, timestamp, TraceEventType.RUN_STARTED, "Execution started for: " + programName, null);
    }

    public static TraceEvent createOutput(int seq, String timestamp, String line) {
        return new TraceEvent(seq, timestamp, TraceEventType.OUTPUT, line, null);
    }

    public static TraceEvent createErrorOutput(int seq, String timestamp, String line) {
        return new TraceEvent(seq, timestamp, TraceEventType.ERROR_OUTPUT, line, null);
    }

    public static TraceEvent createCheckpoint(int seq, String timestamp, CheckpointData checkpoint) {
        String msg = (checkpoint != null) ? "Checkpoint reached: " + checkpoint.getName() : "Checkpoint reached";
        return new TraceEvent(seq, timestamp, TraceEventType.CHECKPOINT, msg, checkpoint);
    }

    public static TraceEvent createFinished(int seq, String timestamp, int exitCode, long durationMs) {
        return new TraceEvent(seq, timestamp, TraceEventType.RUN_FINISHED,
                "Process finished normally (exit code " + exitCode + ", duration " + durationMs + " ms)", null);
    }

    public static TraceEvent createFailed(int seq, String timestamp, String errorMessage, int exitCode) {
        return new TraceEvent(seq, timestamp, TraceEventType.RUN_FAILED,
                "Execution failed: " + errorMessage + " (exit code " + exitCode + ")", null);
    }

    public static TraceEvent createTimedOut(int seq, String timestamp, long timeoutMs) {
        return new TraceEvent(seq, timestamp, TraceEventType.RUN_TIMED_OUT,
                "Execution timed out after " + timeoutMs + " ms and was terminated", null);
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public TraceEventType getType() {
        return type;
    }

    public void setType(TraceEventType type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public CheckpointData getCheckpoint() {
        return checkpoint;
    }

    public void setCheckpoint(CheckpointData checkpoint) {
        this.checkpoint = checkpoint;
    }

    @Override
    public String toString() {
        return String.format("[%03d] %s (%s): %s", sequenceNumber, timestamp, type, message);
    }
}
