package com.bugreplay.ui;

import com.bugreplay.model.CheckpointData;
import com.bugreplay.model.ExecutionTrace;
import com.bugreplay.model.TraceEvent;
import com.bugreplay.model.TraceEventType;
import com.bugreplay.recorder.TraceRecorder;
import com.bugreplay.runner.ExecutionResult;
import com.bugreplay.runner.ProcessRunner;
import com.bugreplay.storage.JsonTraceStorage;

import javax.swing.SwingUtilities;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Phase4UiTest verifies the UI components, model bindings, and SwingWorker integration.
 */
public class Phase4UiTest {

    private static int passedTests = 0;
    private static int totalTests = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("     BUGREPLAY - PHASE 4 SWING UI TEST SUITE      ");
        System.out.println("==================================================");

        testEventTableModel();
        testEventDetailsPanel();
        testMainFrameInitializationAndTraceDisplay();
        testSwingWorkerExecutionPipeline();

        System.out.println("\n==================================================");
        System.out.println("PHASE 4 TEST SUMMARY: " + passedTests + "/" + totalTests + " tests passed.");
        System.out.println("==================================================");

        if (passedTests != totalTests) {
            System.exit(1);
        }
    }

    private static void testEventTableModel() {
        totalTests++;
        System.out.println("\n[Test 1] Testing EventTableModel bindings...");

        EventTableModel model = new EventTableModel();
        List<TraceEvent> events = new ArrayList<>();
        events.add(TraceEvent.createStarted(1, "2026-10-29T17:00:00", "NormalExample.java"));
        events.add(TraceEvent.createOutput(2, "2026-10-29T17:00:01", "Line 1 output"));
        events.add(TraceEvent.createFinished(3, "2026-10-29T17:00:02", 0, 100));

        model.setEvents(events);

        boolean passed = (model.getRowCount() == 3)
                && (model.getColumnCount() == 4)
                && Integer.valueOf(1).equals(model.getValueAt(0, 0))
                && "Line 1 output".equals(model.getValueAt(1, 3))
                && TraceEventType.RUN_FINISHED.equals(model.getValueAt(2, 2));

        if (passed) {
            passedTests++;
            System.out.println("  PASS: EventTableModel correctly maps rows, columns, and event properties.");
        } else {
            System.out.println("  FAIL: EventTableModel mapping error.");
        }
    }

    private static void testEventDetailsPanel() throws InvocationTargetException, InterruptedException {
        totalTests++;
        System.out.println("\n[Test 2] Testing EventDetailsPanel display logic on EDT...");

        final boolean[] passed = {false};

        SwingUtilities.invokeAndWait(() -> {
            EventDetailsPanel panel = new EventDetailsPanel();

            // Test normal event
            TraceEvent outputEvent = TraceEvent.createOutput(2, "2026-10-29T17:00:01", "Hello from runner");
            panel.displayEvent(outputEvent);

            // Test checkpoint event with variables
            Map<String, String> vars = new HashMap<>();
            vars.put("account", "ACC-99");
            vars.put("balance", "2500.0");
            CheckpointData cp = new CheckpointData("TEST_CHECKPOINT", "Note", vars);
            TraceEvent cpEvent = TraceEvent.createCheckpoint(3, "2026-10-29T17:00:02", cp);
            panel.displayEvent(cpEvent);

            panel.clear();
            passed[0] = true;
        });

        if (passed[0]) {
            passedTests++;
            System.out.println("  PASS: EventDetailsPanel handled both standard and checkpoint events cleanly.");
        } else {
            System.out.println("  FAIL: Exception occurred while updating EventDetailsPanel.");
        }
    }

    private static void testMainFrameInitializationAndTraceDisplay() throws InvocationTargetException, InterruptedException {
        totalTests++;
        System.out.println("\n[Test 3] Testing MainFrame construction and trace population on EDT...");

        final boolean[] passed = {false};

        SwingUtilities.invokeAndWait(() -> {
            MainFrame frame = new MainFrame();

            // Create a realistic trace
            List<TraceEvent> events = new ArrayList<>();
            events.add(TraceEvent.createStarted(1, "2026-10-29T17:00:00", "NormalExample.java"));
            events.add(TraceEvent.createOutput(2, "2026-10-29T17:00:00.100", "Computed sum = 15"));
            events.add(TraceEvent.createFinished(3, "2026-10-29T17:00:00.200", 0, 200));

            ExecutionTrace trace = new ExecutionTrace(
                    "run_test_ui_123",
                    "NormalExample.java",
                    "examples/NormalExample.java",
                    "2026-10-29T17:00:00",
                    "SUCCESS",
                    true,
                    "",
                    0,
                    200,
                    events
            );

            frame.displayTrace(trace);

            boolean tableUpdated = frame.getEventTableModel().getRowCount() == 3;
            boolean traceRetained = frame.getCurrentTrace() != null;

            frame.dispose();
            passed[0] = tableUpdated && traceRetained;
        });

        if (passed[0]) {
            passedTests++;
            System.out.println("  PASS: MainFrame initialized and displayed trace without error.");
        } else {
            System.out.println("  FAIL: MainFrame did not update components correctly.");
        }
    }

    private static void testSwingWorkerExecutionPipeline() throws Exception {
        totalTests++;
        System.out.println("\n[Test 4] Testing real asynchronous execution via SwingWorker...");

        File testTracesDir = new File("build" + File.separator + "test-traces");
        ProcessRunner runner = new ProcessRunner(new File("build" + File.separator + "test-classes"));
        TraceRecorder recorder = new TraceRecorder();
        JsonTraceStorage storage = new JsonTraceStorage(testTracesDir);

        final MainFrame[] frameHolder = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            frameHolder[0] = new MainFrame(runner, recorder, storage);
            frameHolder[0].setFilePath(new File("examples", "NormalExample.java").getAbsolutePath());
        });

        MainFrame frame = frameHolder[0];

        // Trigger execution
        SwingUtilities.invokeAndWait(frame::startExecution);

        // Wait for SwingWorker to finish on background thread and update EDT
        long maxWaitMillis = 6000;
        long startTime = System.currentTimeMillis();
        boolean finished = false;

        while (System.currentTimeMillis() - startTime < maxWaitMillis) {
            Thread.sleep(200);
            if (frame.getCurrentTrace() != null) {
                finished = true;
                break;
            }
        }

        SwingUtilities.invokeAndWait(frame::dispose);

        if (finished && frame.getCurrentTrace() != null && frame.getCurrentTrace().getEvents().size() > 0) {
            passedTests++;
            System.out.println("  PASS: SwingWorker executed child process, captured real trace, and updated UI asynchronously.");
            System.out.println("  Recorded run ID: " + frame.getCurrentTrace().getRunId());
            System.out.println("  Events in table: " + frame.getCurrentTrace().getEvents().size());
        } else {
            System.out.println("  FAIL: SwingWorker execution did not complete within timeout.");
        }
    }
}
