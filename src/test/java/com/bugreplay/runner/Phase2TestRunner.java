package com.bugreplay.runner;

import java.io.File;

/**
 * Phase2TestRunner executes a comprehensive test suite against ProcessRunner
 * verifying:
 * 1. Normal execution with clean exit code 0 and stdout capture.
 * 2. Compilation failure detection and diagnostic error capture.
 * 3. Runtime exception capture with non-zero exit code and stderr stack trace.
 * 4. Timeout enforcement and process termination for hanging programs.
 * 5. Non-existent file safety.
 */
public class Phase2TestRunner {

    private static int passedTests = 0;
    private static int totalTests = 0;

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   BUGREPLAY - PHASE 2 EXECUTION RUNNER TEST SUITE ");
        System.out.println("==================================================");

        ProcessRunner runner = new ProcessRunner(new File("build" + File.separator + "test-classes"));

        testNormalExecution(runner);
        testCompilationError(runner);
        testRuntimeException(runner);
        testTimeoutEnforcement(runner);
        testMissingFileHandling(runner);

        System.out.println("\n==================================================");
        System.out.println("TEST SUMMARY: " + passedTests + "/" + totalTests + " tests passed.");
        System.out.println("==================================================");

        if (passedTests != totalTests) {
            System.exit(1);
        }
    }

    private static void testNormalExecution(ProcessRunner runner) {
        totalTests++;
        System.out.println("\n[Test 1] Testing Normal Program Execution...");
        File normalFile = new File("examples" + File.separator + "NormalExample.java");

        ExecutionResult result = runner.run(normalFile, 5000);

        boolean passed = result.isCompilationSuccess()
                && result.getExitCode() == 0
                && result.isCleanExit()
                && result.getStdoutText().contains("Total sum = 15")
                && !result.isTimedOut();

        if (passed) {
            passedTests++;
            System.out.println("  PASS: Compilation succeeded, exitCode=0, stdout captured successfully.");
            System.out.println("  Duration: " + result.getDurationMillis() + " ms");
            System.out.println("  Stdout line count: " + result.getStdoutLines().size());
        } else {
            System.out.println("  FAIL: Expected exit code 0 and sum output.");
            System.out.println("  Details: " + result);
        }
    }

    private static void testCompilationError(ProcessRunner runner) {
        totalTests++;
        System.out.println("\n[Test 2] Testing Compilation Failure Handling...");
        File errorFile = new File("examples" + File.separator + "CompilationErrorExample.java");

        ExecutionResult result = runner.run(errorFile, 5000);

        boolean passed = !result.isCompilationSuccess()
                && result.getCompilationError() != null
                && result.getCompilationError().contains("Compilation failed");

        if (passed) {
            passedTests++;
            System.out.println("  PASS: Compilation error properly detected and reported.");
            System.out.println("  Captured error message:\n" + indent(result.getCompilationError(), 4));
        } else {
            System.out.println("  FAIL: Expected compilation failure.");
            System.out.println("  Details: " + result);
        }
    }

    private static void testRuntimeException(ProcessRunner runner) {
        totalTests++;
        System.out.println("\n[Test 3] Testing Runtime Exception Handling...");
        File exceptionFile = new File("examples" + File.separator + "ExceptionExample.java");

        ExecutionResult result = runner.run(exceptionFile, 5000);

        boolean passed = result.isCompilationSuccess()
                && result.getExitCode() != 0
                && result.getStderrText().contains("ArithmeticException");

        if (passed) {
            passedTests++;
            System.out.println("  PASS: Runtime exception captured with non-zero exit code (" + result.getExitCode() + ").");
            System.out.println("  Captured stderr snippet:\n" + indent(result.getStderrText(), 4));
        } else {
            System.out.println("  FAIL: Expected non-zero exit code and ArithmeticException in stderr.");
            System.out.println("  Details: " + result);
            System.out.println("  Stderr: " + result.getStderrText());
        }
    }

    private static void testTimeoutEnforcement(ProcessRunner runner) {
        totalTests++;
        System.out.println("\n[Test 4] Testing Timeout Enforcement (1500 ms limit)...");
        File timeoutFile = new File("examples" + File.separator + "TimeoutExample.java");

        long start = System.currentTimeMillis();
        ExecutionResult result = runner.run(timeoutFile, 1500);
        long elapsed = System.currentTimeMillis() - start;

        boolean passed = result.isCompilationSuccess()
                && result.isTimedOut()
                && result.getStderrText().contains("timed out")
                && elapsed >= 1400 && elapsed <= 3500;

        if (passed) {
            passedTests++;
            System.out.println("  PASS: Process timed out as expected and was forcibly terminated.");
            System.out.println("  Elapsed wall time: " + elapsed + " ms (timeout configured: 1500 ms)");
            System.out.println("  Stdout captured before termination: " + result.getStdoutLines().size() + " lines");
        } else {
            System.out.println("  FAIL: Expected timedOut=true within timeout window.");
            System.out.println("  Elapsed: " + elapsed + " ms, Details: " + result);
        }
    }

    private static void testMissingFileHandling(ProcessRunner runner) {
        totalTests++;
        System.out.println("\n[Test 5] Testing Non-Existent File Handling...");
        File ghostFile = new File("examples" + File.separator + "DoesNotExist.java");

        ExecutionResult result = runner.run(ghostFile, 5000);

        boolean passed = !result.isCompilationSuccess()
                && result.getCompilationError().contains("Source file not found");

        if (passed) {
            passedTests++;
            System.out.println("  PASS: Non-existent file handled cleanly without throwing unhandled exceptions.");
        } else {
            System.out.println("  FAIL: Expected clean failure message for missing file.");
            System.out.println("  Details: " + result);
        }
    }

    private static String indent(String text, int spaces) {
        if (text == null) return "";
        String prefix = new String(new char[spaces]).replace('\0', ' ');
        return prefix + text.replace("\n", "\n" + prefix);
    }
}
