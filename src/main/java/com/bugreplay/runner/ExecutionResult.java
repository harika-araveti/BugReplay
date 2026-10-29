package com.bugreplay.runner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ExecutionResult holds all observations captured during compilation and execution
 * of a target Java source file.
 *
 * It represents the raw evidence gathered at the OS process level:
 * - Did javac compile the code successfully?
 * - What was the process exit code?
 * - What lines did the process print to stdout and stderr?
 * - How long did it take to execute?
 * - Did it exceed the configured timeout or get cancelled?
 */
public class ExecutionResult {

    private final boolean compilationSuccess;
    private final String compilationError;
    private final int exitCode;
    private final List<String> stdoutLines;
    private final List<String> stderrLines;
    private final long startTimeMillis;
    private final long endTimeMillis;
    private final long durationMillis;
    private final boolean timedOut;
    private final boolean cancelled;

    public ExecutionResult(
            boolean compilationSuccess,
            String compilationError,
            int exitCode,
            List<String> stdoutLines,
            List<String> stderrLines,
            long startTimeMillis,
            long endTimeMillis,
            long durationMillis,
            boolean timedOut,
            boolean cancelled) {
        this.compilationSuccess = compilationSuccess;
        this.compilationError = compilationError;
        this.exitCode = exitCode;
        this.stdoutLines = (stdoutLines != null) ? new ArrayList<>(stdoutLines) : Collections.<String>emptyList();
        this.stderrLines = (stderrLines != null) ? new ArrayList<>(stderrLines) : Collections.<String>emptyList();
        this.startTimeMillis = startTimeMillis;
        this.endTimeMillis = endTimeMillis;
        this.durationMillis = durationMillis;
        this.timedOut = timedOut;
        this.cancelled = cancelled;
    }

    /**
     * Factory method for reporting a compilation failure early without executing.
     */
    public static ExecutionResult forCompilationFailure(String errorMessage, long durationMillis) {
        long now = System.currentTimeMillis();
        return new ExecutionResult(
                false,
                errorMessage,
                -1,
                Collections.<String>emptyList(),
                Collections.singletonList(errorMessage),
                now - durationMillis,
                now,
                durationMillis,
                false,
                false
        );
    }

    public boolean isCompilationSuccess() {
        return compilationSuccess;
    }

    public String getCompilationError() {
        return compilationError;
    }

    public int getExitCode() {
        return exitCode;
    }

    public List<String> getStdoutLines() {
        return Collections.unmodifiableList(stdoutLines);
    }

    public List<String> getStderrLines() {
        return Collections.unmodifiableList(stderrLines);
    }

    public String getStdoutText() {
        return String.join(System.lineSeparator(), stdoutLines);
    }

    public String getStderrText() {
        return String.join(System.lineSeparator(), stderrLines);
    }

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    public long getEndTimeMillis() {
        return endTimeMillis;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * An execution is considered a clean success if compilation passed,
     * it didn't time out or get cancelled, and the exit code was 0.
     */
    public boolean isCleanExit() {
        return compilationSuccess && !timedOut && !cancelled && exitCode == 0;
    }

    @Override
    public String toString() {
        return "ExecutionResult{" +
                "compilationSuccess=" + compilationSuccess +
                ", exitCode=" + exitCode +
                ", stdoutCount=" + stdoutLines.size() +
                ", stderrCount=" + stderrLines.size() +
                ", durationMillis=" + durationMillis + "ms" +
                ", timedOut=" + timedOut +
                ", cancelled=" + cancelled +
                '}';
    }
}
