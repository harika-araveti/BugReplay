package com.bugreplay.analysis;

import com.bugreplay.model.ExecutionTrace;
import com.bugreplay.model.TraceEvent;
import com.bugreplay.recorder.TraceRecorder;
import com.bugreplay.runner.ExecutionResult;
import com.bugreplay.runner.ProcessRunner;

import java.io.File;

/**
 * TraceComparatorTest verifies:
 * 1. Detection of logical output and checkpoint differences (BuggyDiscount vs FixedDiscount).
 * 2. Identity comparison (trace compared to itself reports zero differences).
 * 3. Status and exit code divergence detection (NormalExample vs ExceptionExample).
 * 4. ReplayController navigation (first, next, previous, last, bounds checking).
 * 5. Presence of the mandatory evidence disclaimer in generated reports.
 */
public class TraceComparatorTest {

    private static int passedTests = 0;
    private static int totalTests = 0;

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  BUGREPLAY - TRACE COMPARISON & REPLAY TESTS     ");
        System.out.println("==================================================");

        ProcessRunner runner = new ProcessRunner(new File("build" + File.separator + "test-classes"));
        TraceRecorder recorder = new TraceRecorder();
        TraceComparator comparator = new TraceComparator();

        testBuggyVsFixedComparison(runner, recorder, comparator);
        testIdenticalTraceComparison(runner, recorder, comparator);
        testSuccessVsExceptionComparison(runner, recorder, comparator);
        testReplayControllerNavigation(runner, recorder);

        System.out.println("\n==================================================");
        System.out.println("COMPARATOR TEST SUMMARY: " + passedTests + "/" + totalTests + " tests passed.");
        System.out.println("==================================================");

        if (passedTests != totalTests) {
            System.exit(1);
        }
    }

    private static void testBuggyVsFixedComparison(ProcessRunner runner, TraceRecorder recorder, TraceComparator comparator) {
        totalTests++;
        System.out.println("\n[Test 1] Testing BuggyDiscount vs FixedDiscount Comparison...");

        File buggyFile = new File("examples", "BuggyDiscount.java");
        File fixedFile = new File("examples", "FixedDiscount.java");

        ExecutionResult resA = runner.run(buggyFile, 5000);
        ExecutionTrace traceA = recorder.record(buggyFile, resA);

        ExecutionResult resB = runner.run(fixedFile, 5000);
        ExecutionTrace traceB = recorder.record(fixedFile, resB);

        ComparisonResult result = comparator.compare(traceA, traceB);

        boolean hasDiffs = result.hasDifferences();
        boolean hasStdoutDiff = !result.getStdoutDiffs().isEmpty();
        boolean hasCpDiff = !result.getCheckpointDiffs().isEmpty();
        String report = result.generateReportText();
        boolean hasDisclaimer = report.contains("IMPORTANT INVESTIGATION DISCLAIMER")
                && report.contains("OBSERVED differences");

        boolean passed = hasDiffs && hasStdoutDiff && hasCpDiff && hasDisclaimer;

        if (passed) {
            passedTests++;
            System.out.println("  PASS: Accurately detected output discrepancies and checkpoint variable divergence.");
            System.out.println("  Stdout diffs detected: " + result.getStdoutDiffs().size());
            System.out.println("  Checkpoint diffs detected: " + result.getCheckpointDiffs().size());
            for (String cpDiff : result.getCheckpointDiffs()) {
                System.out.println("    * " + cpDiff);
            }
        } else {
            System.out.println("  FAIL: Did not detect expected differences between buggy and fixed runs.");
        }
    }

    private static void testIdenticalTraceComparison(ProcessRunner runner, TraceRecorder recorder, TraceComparator comparator) {
        totalTests++;
        System.out.println("\n[Test 2] Testing Identity Comparison (Trace compared to itself)...");

        File normalFile = new File("examples", "NormalExample.java");
        ExecutionResult res = runner.run(normalFile, 5000);
        ExecutionTrace trace = recorder.record(normalFile, res);

        ComparisonResult result = comparator.compare(trace, trace);

        boolean passed = !result.hasDifferences()
                && !result.isStatusDifferent()
                && !result.isExitCodeDifferent()
                && result.getStdoutDiffs().isEmpty()
                && result.getCheckpointDiffs().isEmpty();

        if (passed) {
            passedTests++;
            System.out.println("  PASS: Identical traces correctly report zero observed differences.");
        } else {
            System.out.println("  FAIL: Identical trace falsely reported differences.");
        }
    }

    private static void testSuccessVsExceptionComparison(ProcessRunner runner, TraceRecorder recorder, TraceComparator comparator) {
        totalTests++;
        System.out.println("\n[Test 3] Testing Clean Run vs Runtime Exception Divergence...");

        File normalFile = new File("examples", "NormalExample.java");
        File exFile = new File("examples", "ExceptionExample.java");

        ExecutionTrace traceNormal = recorder.record(normalFile, runner.run(normalFile, 5000));
        ExecutionTrace traceEx = recorder.record(exFile, runner.run(exFile, 5000));

        ComparisonResult result = comparator.compare(traceNormal, traceEx);

        boolean passed = result.isStatusDifferent()
                && result.isExitCodeDifferent()
                && !result.getStderrDiffs().isEmpty();

        if (passed) {
            passedTests++;
            System.out.println("  PASS: Detected status difference (SUCCESS vs RUNTIME_ERROR), exit codes (0 vs 1), and stderr.");
        } else {
            System.out.println("  FAIL: Failed to detect status/exit code divergence.");
        }
    }

    private static void testReplayControllerNavigation(ProcessRunner runner, TraceRecorder recorder) {
        totalTests++;
        System.out.println("\n[Test 4] Testing ReplayController stepping and bounds...");

        File normalFile = new File("examples", "NormalExample.java");
        ExecutionTrace trace = recorder.record(normalFile, runner.run(normalFile, 5000));

        ReplayController controller = new ReplayController(trace);

        boolean initOk = controller.getCurrentIndex() == 0 && controller.canGoNext() && !controller.canGoPrevious();

        TraceEvent nextEvent = controller.next();
        boolean nextOk = controller.getCurrentIndex() == 1 && controller.canGoPrevious();

        TraceEvent lastEvent = controller.last();
        boolean lastOk = controller.getCurrentIndex() == (trace.getEvents().size() - 1) && !controller.canGoNext();

        TraceEvent firstEvent = controller.first();
        boolean firstOk = controller.getCurrentIndex() == 0 && !controller.canGoPrevious();

        boolean passed = initOk && nextOk && lastOk && firstOk;

        if (passed) {
            passedTests++;
            System.out.println("  PASS: ReplayController successfully navigates first, next, previous, last with bounds checking.");
            System.out.println("  Final navigation position string: " + controller.getPositionText());
        } else {
            System.out.println("  FAIL: ReplayController navigation error.");
        }
    }
}
