package com.bugreplay.runner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * StreamGobbler is a background worker that reads an InputStream (like stdout or stderr)
 * line by line as soon as the child process produces output.
 *
 * Why is this necessary?
 * When an operating system runs a child process, it allocates a limited internal buffer
 * (often 4 KB on Windows) for the process's standard output and error streams.
 * If the child process fills this buffer and the parent Java application does not read it,
 * the child process hangs indefinitely waiting for buffer space. This is a classic
 * "process stream deadlock".
 *
 * StreamGobbler solves this by actively reading the stream on its own independent thread.
 */
public class StreamGobbler implements Runnable {

    private final InputStream inputStream;
    private final List<String> lines;
    private volatile boolean isFinished = false;

    public StreamGobbler(InputStream inputStream) {
        this.inputStream = inputStream;
        // Use a thread-safe list so that reading lines from another thread is safe
        this.lines = Collections.synchronizedList(new ArrayList<String>());
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            // If the process was terminated forcibly (e.g., timeout or cancel),
            // the stream might close abruptly. We catch this safely without crashing.
            lines.add("[Stream closed: " + e.getMessage() + "]");
        } finally {
            isFinished = true;
        }
    }

    /**
     * Returns a snapshot copy of the lines captured so far.
     */
    public List<String> getLines() {
        synchronized (lines) {
            return new ArrayList<>(lines);
        }
    }

    /**
     * Checks if the stream has been fully read and closed.
     */
    public boolean isFinished() {
        return isFinished;
    }
}
