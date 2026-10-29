package com.bugreplay.analysis;

import com.bugreplay.model.CheckpointData;
import com.bugreplay.model.ExecutionTrace;
import com.bugreplay.model.TraceEvent;
import com.bugreplay.model.TraceEventType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * TraceComparator performs objective, evidence-based comparisons between two ExecutionTraces.
 *
 * It inspects:
 * - Execution status (SUCCESS, RUNTIME_ERROR, TIMED_OUT, etc.)
 * - Process exit codes
 * - Wall-clock execution durations
 * - Total event counts
 * - Output line discrepancies (stdout)
 * - Error output discrepancies (stderr)
 * - Internal checkpoint variable differences
 */
public class TraceComparator {

    /**
     * Compares two execution traces and returns a structured ComparisonResult.
     */
    public ComparisonResult compare(ExecutionTrace traceA, ExecutionTrace traceB) {
        if (traceA == null || traceB == null) {
            throw new IllegalArgumentException("Both traces must be non-null for comparison.");
        }

        ComparisonResult result = new ComparisonResult(
                traceA.getRunId(),
                traceB.getRunId(),
                traceA.getProgramName(),
                traceB.getProgramName()
        );

        // 1. Status comparison
        if (!Objects.equals(traceA.getExecutionStatus(), traceB.getExecutionStatus())) {
            result.setStatusDiff("Trace A was [" + traceA.getExecutionStatus() + "] but Trace B was [" + traceB.getExecutionStatus() + "]");
        }

        // 2. Exit code comparison
        if (traceA.getExitCode() != traceB.getExitCode()) {
            result.setExitCodeDiff("Trace A exited with code " + traceA.getExitCode() + " while Trace B exited with code " + traceB.getExitCode());
        }

        // 3. Duration comparison
        long diffDuration = traceB.getDurationMillis() - traceA.getDurationMillis();
        if (Math.abs(diffDuration) > 10) {
            result.setDurationDiff(diffDuration, "Trace A took " + traceA.getDurationMillis() + " ms vs Trace B took " + traceB.getDurationMillis() + " ms (diff: " + diffDuration + " ms)");
        }

        // 4. Event count comparison
        int countA = traceA.getEvents() != null ? traceA.getEvents().size() : 0;
        int countB = traceB.getEvents() != null ? traceB.getEvents().size() : 0;
        if (countA != countB) {
            result.setEventCountDiff("Trace A recorded " + countA + " events, while Trace B recorded " + countB + " events");
        }

        // 5. Standard output line-by-line comparison
        compareOutputLines(traceA, traceB, result);

        // 6. Standard error comparison
        compareErrorLines(traceA, traceB, result);

        // 7. Explicit Checkpoints comparison
        compareCheckpoints(traceA, traceB, result);

        return result;
    }

    private void compareOutputLines(ExecutionTrace a, ExecutionTrace b, ComparisonResult result) {
        List<String> outA = extractLinesByType(a, TraceEventType.OUTPUT);
        List<String> outB = extractLinesByType(b, TraceEventType.OUTPUT);

        int max = Math.max(outA.size(), outB.size());
        for (int i = 0; i < max; i++) {
            String lineA = (i < outA.size()) ? outA.get(i) : null;
            String lineB = (i < outB.size()) ? outB.get(i) : null;

            if (lineA == null) {
                result.addStdoutDiff("[Line " + (i + 1) + "] Present in Trace B only: \"" + lineB + "\"");
            } else if (lineB == null) {
                result.addStdoutDiff("[Line " + (i + 1) + "] Present in Trace A only: \"" + lineA + "\"");
            } else if (!lineA.equals(lineB)) {
                result.addStdoutDiff("[Line " + (i + 1) + "] Diverged:\n" +
                        "       Trace A: \"" + lineA + "\"\n" +
                        "       Trace B: \"" + lineB + "\"");
            }
        }
    }

    private void compareErrorLines(ExecutionTrace a, ExecutionTrace b, ComparisonResult result) {
        List<String> errA = extractLinesByType(a, TraceEventType.ERROR_OUTPUT);
        List<String> errB = extractLinesByType(b, TraceEventType.ERROR_OUTPUT);

        if (errA.isEmpty() && !errB.isEmpty()) {
            result.addStderrDiff("Trace B produced " + errB.size() + " error line(s), whereas Trace A had no stderr output.");
        } else if (!errA.isEmpty() && errB.isEmpty()) {
            result.addStderrDiff("Trace A produced " + errA.size() + " error line(s), whereas Trace B had no stderr output.");
        } else if (!errA.equals(errB)) {
            result.addStderrDiff("Stderr output differed between Trace A (" + errA.size() + " lines) and Trace B (" + errB.size() + " lines).");
        }
    }

    private void compareCheckpoints(ExecutionTrace a, ExecutionTrace b, ComparisonResult result) {
        Map<String, CheckpointData> cpMapA = extractCheckpointsByName(a);
        Map<String, CheckpointData> cpMapB = extractCheckpointsByName(b);

        // Compare all checkpoints found in A
        for (Map.Entry<String, CheckpointData> entryA : cpMapA.entrySet()) {
            String cpName = entryA.getKey();
            CheckpointData dataA = entryA.getValue();

            if (!cpMapB.containsKey(cpName)) {
                result.addCheckpointDiff("Checkpoint [" + cpName + "] was recorded in Trace A but was MISSING in Trace B.");
                continue;
            }

            CheckpointData dataB = cpMapB.get(cpName);
            Map<String, String> varsA = dataA.getVariables();
            Map<String, String> varsB = dataB.getVariables();

            for (Map.Entry<String, String> varEntryA : varsA.entrySet()) {
                String varName = varEntryA.getKey();
                String valA = varEntryA.getValue();
                String valB = varsB.get(varName);

                if (valB == null) {
                    result.addCheckpointDiff("Checkpoint [" + cpName + "] variable '" + varName + "': '" + valA + "' in Trace A, not found in Trace B.");
                } else if (!valA.equals(valB)) {
                    result.addCheckpointDiff("Checkpoint [" + cpName + "] variable '" + varName + "': '" + valA + "' in Trace A vs '" + valB + "' in Trace B.");
                }
            }

            for (String varNameB : varsB.keySet()) {
                if (!varsA.containsKey(varNameB)) {
                    result.addCheckpointDiff("Checkpoint [" + cpName + "] variable '" + varNameB + "' was recorded only in Trace B ('" + varsB.get(varNameB) + "').");
                }
            }
        }

        // Check for checkpoints present only in B
        for (String cpNameB : cpMapB.keySet()) {
            if (!cpMapA.containsKey(cpNameB)) {
                result.addCheckpointDiff("Checkpoint [" + cpNameB + "] was recorded in Trace B but was MISSING in Trace A.");
            }
        }
    }

    private List<String> extractLinesByType(ExecutionTrace trace, TraceEventType type) {
        List<String> list = new ArrayList<>();
        if (trace.getEvents() != null) {
            for (TraceEvent e : trace.getEvents()) {
                if (e.getType() == type) {
                    list.add(e.getMessage());
                }
            }
        }
        return list;
    }

    private Map<String, CheckpointData> extractCheckpointsByName(ExecutionTrace trace) {
        Map<String, CheckpointData> map = new LinkedHashMap<>();
        if (trace.getEvents() != null) {
            for (TraceEvent e : trace.getEvents()) {
                if (e.getType() == TraceEventType.CHECKPOINT && e.getCheckpoint() != null) {
                    map.put(e.getCheckpoint().getName(), e.getCheckpoint());
                }
            }
        }
        return map;
    }
}
