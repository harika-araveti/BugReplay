package com.bugreplay.model;

/**
 * TraceEventType defines the discrete types of execution events that BugReplay can record.
 * Every event in an execution trace is labeled with one of these types.
 */
public enum TraceEventType {
    /**
     * Emitted when compilation finishes and the target process is launched.
     */
    RUN_STARTED,

    /**
     * Emitted when a line of normal output is received on standard output (stdout).
     */
    OUTPUT,

    /**
     * Emitted when a line of error output or stack trace is received on standard error (stderr).
     */
    ERROR_OUTPUT,

    /**
     * Emitted when a supported program publishes an explicit internal state checkpoint.
     */
    CHECKPOINT,

    /**
     * Emitted when the process terminates normally with exit code 0.
     */
    RUN_FINISHED,

    /**
     * Emitted when the process exits with a non-zero code or compilation fails.
     */
    RUN_FAILED,

    /**
     * Emitted when the process exceeds its configured time limit and is forcibly terminated.
     */
    RUN_TIMED_OUT
}
