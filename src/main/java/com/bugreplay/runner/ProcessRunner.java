package com.bugreplay.runner;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * ProcessRunner handles the lifecycle of compiling and running a target Java source file:
 * 1. Resolves the JDK tools (javac and java).
 * 2. Compiles the .java file into a dedicated classes directory.
 * 3. Safely executes the compiled class in a child JVM process.
 * 4. Concurrently drains stdout and stderr using StreamGobbler threads.
 * 5. Enforces a strict execution timeout and supports manual cancellation.
 */
public class ProcessRunner {

    private final File classesDirectory;
    private volatile Process activeProcess;
    private volatile boolean cancelled = false;

    public ProcessRunner() {
        this(new File("build" + File.separator + "classes"));
    }

    public ProcessRunner(File classesDirectory) {
        this.classesDirectory = classesDirectory;
        if (!this.classesDirectory.exists()) {
            this.classesDirectory.mkdirs();
        }
    }

    /**
     * Compiles and executes a single Java source file.
     *
     * @param sourceFile     The .java source file to compile and execute.
     * @param timeoutMillis  Maximum allowed execution time in milliseconds.
     * @return ExecutionResult containing compilation errors, output lines, exit code, and timings.
     */
    public ExecutionResult run(File sourceFile, long timeoutMillis) {
        cancelled = false;

        // Step 1: Validate source file
        if (sourceFile == null || !sourceFile.exists()) {
            return ExecutionResult.forCompilationFailure(
                    "Source file not found: " + (sourceFile == null ? "null" : sourceFile.getAbsolutePath()), 0);
        }
        if (!sourceFile.getName().endsWith(".java")) {
            return ExecutionResult.forCompilationFailure(
                    "Selected file is not a .java source file: " + sourceFile.getName(), 0);
        }

        // Step 2: Determine full class name (handles package declarations if present)
        String fullyQualifiedClassName = resolveClassName(sourceFile);

        // Step 3: Compile with javac
        long compileStart = System.currentTimeMillis();
        CompilationOutcome compilationOutcome = compile(sourceFile);
        long compileEnd = System.currentTimeMillis();
        long compileDuration = compileEnd - compileStart;

        if (!compilationOutcome.isSuccess()) {
            return ExecutionResult.forCompilationFailure(compilationOutcome.getErrorMessage(), compileDuration);
        }

        // Step 4: Execute the compiled class with java
        return execute(fullyQualifiedClassName, timeoutMillis);
    }

    /**
     * Cancels the currently running process, if one is active.
     */
    public void cancel() {
        cancelled = true;
        Process current = activeProcess;
        if (current != null && current.isAlive()) {
            current.destroyForcibly();
        }
    }

    /**
     * Compiles the source file into the classes directory.
     */
    private CompilationOutcome compile(File sourceFile) {
        String javacPath = findJdkTool("javac");

        List<String> command = new ArrayList<>();
        command.add(javacPath);
        command.add("-d");
        command.add(classesDirectory.getAbsolutePath());
        command.add(sourceFile.getAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(command);

        try {
            Process process = pb.start();

            // javac sends compilation errors to its error stream
            StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream());
            StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream());

            Thread errorThread = new Thread(errorGobbler, "Javac-ErrorGobbler");
            Thread outputThread = new Thread(outputGobbler, "Javac-OutputGobbler");

            errorThread.start();
            outputThread.start();

            int exitCode = process.waitFor();
            errorThread.join(2000);
            outputThread.join(2000);

            if (exitCode != 0) {
                String errorDetails = String.join(System.lineSeparator(), errorGobbler.getLines());
                if (errorDetails.trim().isEmpty()) {
                    errorDetails = String.join(System.lineSeparator(), outputGobbler.getLines());
                }
                return new CompilationOutcome(false, "Compilation failed (exit code " + exitCode + "):\n" + errorDetails);
            }

            return new CompilationOutcome(true, "");
        } catch (IOException e) {
            return new CompilationOutcome(false, "Unable to execute javac: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CompilationOutcome(false, "Compilation was interrupted: " + e.getMessage());
        }
    }

    /**
     * Runs the compiled class in a separate JVM process with timeout and stream management.
     */
    private ExecutionResult execute(String className, long timeoutMillis) {
        String javaPath = findJdkTool("java");

        List<String> command = new ArrayList<>();
        command.add(javaPath);
        command.add("-cp");
        command.add(classesDirectory.getAbsolutePath());
        command.add(className);

        ProcessBuilder pb = new ProcessBuilder(command);

        long startTime = System.currentTimeMillis();
        boolean timedOut = false;
        int exitCode = -1;
        StreamGobbler stdoutGobbler = null;
        StreamGobbler stderrGobbler = null;

        try {
            activeProcess = pb.start();

            stdoutGobbler = new StreamGobbler(activeProcess.getInputStream());
            stderrGobbler = new StreamGobbler(activeProcess.getErrorStream());

            Thread stdoutThread = new Thread(stdoutGobbler, "Process-StdoutGobbler");
            Thread stderrThread = new Thread(stderrGobbler, "Process-StderrGobbler");

            stdoutThread.start();
            stderrThread.start();

            // Wait for process with strict timeout
            boolean finishedInTime = activeProcess.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);

            if (!finishedInTime) {
                timedOut = true;
                activeProcess.destroyForcibly();
            }

            // Wait briefly for gobbler threads to capture any remaining flushed output
            stdoutThread.join(1000);
            stderrThread.join(1000);

            exitCode = timedOut ? -1 : activeProcess.exitValue();

        } catch (IOException e) {
            stderrGobbler = new StreamGobbler(null);
            return new ExecutionResult(
                    true,
                    "",
                    -1,
                    null,
                    null,
                    startTime,
                    System.currentTimeMillis(),
                    System.currentTimeMillis() - startTime,
                    false,
                    cancelled
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (activeProcess != null) {
                activeProcess.destroyForcibly();
            }
            cancelled = true;
        } finally {
            activeProcess = null;
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        List<String> stdoutLines = (stdoutGobbler != null) ? stdoutGobbler.getLines() : new ArrayList<String>();
        List<String> stderrLines = (stderrGobbler != null) ? stderrGobbler.getLines() : new ArrayList<String>();

        if (timedOut) {
            stderrLines.add("[Process timed out after " + timeoutMillis + " ms and was terminated]");
        }
        if (cancelled) {
            stderrLines.add("[Process was cancelled by the user and terminated]");
        }

        return new ExecutionResult(
                true,
                "",
                exitCode,
                stdoutLines,
                stderrLines,
                startTime,
                endTime,
                duration,
                timedOut,
                cancelled
        );
    }

    /**
     * Resolves the package name (if declared) and combines it with the file name to produce
     * the fully qualified class name.
     */
    private String resolveClassName(File sourceFile) {
        String fileName = sourceFile.getName();
        String simpleClassName = fileName.substring(0, fileName.lastIndexOf(".java"));

        String packageName = "";
        try (BufferedReader reader = new BufferedReader(new FileReader(sourceFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("package ") && line.endsWith(";")) {
                    packageName = line.substring(8, line.length() - 1).trim();
                    break;
                }
            }
        } catch (IOException ignored) {
            // If reading fails, fallback to simple class name
        }

        if (packageName.isEmpty()) {
            return simpleClassName;
        } else {
            return packageName + "." + simpleClassName;
        }
    }

    /**
     * Finds the path to a JDK tool (javac or java).
     * First tests if tool is available on PATH. If not, inspects java.home.
     */
    private String findJdkTool(String toolName) {
        String executableName = System.getProperty("os.name").toLowerCase().contains("win")
                ? toolName + ".exe"
                : toolName;

        // Try standard PATH first
        try {
            Process testProcess = new ProcessBuilder(toolName, "-version").start();
            testProcess.waitFor(500, TimeUnit.MILLISECONDS);
            return toolName;
        } catch (Exception ignored) {
            // Not directly on PATH, check java.home directory
        }

        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            // In JDK 8: java.home is often jdk/jre, so ../bin/javac is common
            File inBin = new File(javaHome, "bin" + File.separator + executableName);
            if (inBin.exists()) {
                return inBin.getAbsolutePath();
            }
            File inParentBin = new File(javaHome, ".." + File.separator + "bin" + File.separator + executableName);
            if (inParentBin.exists()) {
                return inParentBin.getAbsolutePath();
            }
        }

        // Fallback to plain tool name
        return toolName;
    }

    /**
     * Internal helper class for compilation results.
     */
    private static class CompilationOutcome {
        private final boolean success;
        private final String errorMessage;

        public CompilationOutcome(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
