package com.bugreplay.storage;

/**
 * TraceStorageException indicates a problem during saving, loading, or validating
 * a JSON execution trace file (e.g. file not found, malformed JSON, out-of-order sequence).
 */
public class TraceStorageException extends Exception {

    public TraceStorageException(String message) {
        super(message);
    }

    public TraceStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
