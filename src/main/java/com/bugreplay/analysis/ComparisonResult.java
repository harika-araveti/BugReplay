package com.bugreplay.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ComparisonResult encapsulates observed differences between two recorded execution traces.
 *
 * Academic Note:
 * This class records observed evidence (status, exit code, stdout, stderr, checkpoints).
 * It clearly separates empirical differences from inferred root causes.
 */
public class ComparisonResult {

    private final String traceAId;
    private final String traceBId;
    private final String programAName;
    private final String programBName;

    private boolean statusDifferent;
    private String statusDiffDescription;

    private boolean exitCodeDifferent;
    private String exitCodeDiffDescription;

    private long durationDiffMillis;
    private String durationDiffDescription;

    private boolean eventCountDifferent;
    private String eventCountDiffDescription;

    private final List<String> stdoutDiffs = new ArrayList<>();
    private final List<String> stderrDiffs = new ArrayList<>();
    private final List<String> checkpointDiffs = new ArrayList<>();

    public ComparisonResult(String traceAId, String traceBId, String programAName, String programBName) {
        this.traceAId = traceAId;
        this.traceBId = traceBId;
        this.programAName = programAName;
        this.programBName = programBName;
    }

    public boolean hasDifferences() {
        return statusDifferent
                || exitCodeDifferent
                || eventCountDifferent
                || !stdoutDiffs.isEmpty()
                || !stderrDiffs.isEmpty()
                || !checkpointDiffs.isEmpty();
    }

    public String generateReportText() {
        StringBuilder sb = new StringBuilder();
        sb.append("=======================================================================\n");
        sb.append("                 BUGREPLAY — TRACE COMPARISON REPORT                   \n");
        sb.append("=======================================================================\n");
        sb.append("Trace A: ").append(traceAId).append(" (").append(programAName).append(")\n");
        sb.append("Trace B: ").append(traceBId).append(" (").append(programBName).append(")\n");
        sb.append("=======================================================================\n\n");

        sb.append("1. HIGH-LEVEL EXECUTION COMPARISON:\n");
        sb.append("   - Status:     ").append(statusDiffDescription != null ? statusDiffDescription : "Identical").append("\n");
        sb.append("   - Exit Code:  ").append(exitCodeDiffDescription != null ? exitCodeDiffDescription : "Identical").append("\n");
        sb.append("   - Duration:   ").append(durationDiffDescription != null ? durationDiffDescription : "Identical").append("\n");
        sb.append("   - Event Count:").append(eventCountDiffDescription != null ? eventCountDiffDescription : "Identical").append("\n\n");

        sb.append("2. OBSERVED CHECKPOINT DIFFERENCES:\n");
        if (checkpointDiffs.isEmpty()) {
            sb.append("   (No checkpoint differences observed or no matching checkpoints found)\n");
        } else {
            for (String diff : checkpointDiffs) {
                sb.append("   * ").append(diff).append("\n");
            }
        }
        sb.append("\n");

        sb.append("3. OBSERVED STANDARD OUTPUT (STDOUT) DIFFERENCES:\n");
        if (stdoutDiffs.isEmpty()) {
            sb.append("   (Standard output text is identical)\n");
        } else {
            for (String diff : stdoutDiffs) {
                sb.append("   * ").append(diff).append("\n");
            }
        }
        sb.append("\n");

        sb.append("4. OBSERVED ERROR OUTPUT (STDERR) DIFFERENCES:\n");
        if (stderrDiffs.isEmpty()) {
            sb.append("   (No error stream differences observed)\n");
        } else {
            for (String diff : stderrDiffs) {
                sb.append("   * ").append(diff).append("\n");
            }
        }
        sb.append("\n");

        sb.append("-----------------------------------------------------------------------\n");
        sb.append("IMPORTANT INVESTIGATION DISCLAIMER:\n");
        sb.append("The items above represent OBSERVED differences between two execution runs.\n");
        sb.append("They highlight where recorded behavior diverged, but do not claim to automatically\n");
        sb.append("prove the exact underlying root cause of a defect without developer inspection.\n");
        sb.append("=======================================================================\n");

        return sb.toString();
    }

    public String getTraceAId() {
        return traceAId;
    }

    public String getTraceBId() {
        return traceBId;
    }

    public String getProgramAName() {
        return programAName;
    }

    public String getProgramBName() {
        return programBName;
    }

    public boolean isStatusDifferent() {
        return statusDifferent;
    }

    public void setStatusDiff(String description) {
        this.statusDifferent = true;
        this.statusDiffDescription = description;
    }

    public boolean isExitCodeDifferent() {
        return exitCodeDifferent;
    }

    public void setExitCodeDiff(String description) {
        this.exitCodeDifferent = true;
        this.exitCodeDiffDescription = description;
    }

    public void setDurationDiff(long diffMillis, String description) {
        this.durationDiffMillis = diffMillis;
        this.durationDiffDescription = description;
    }

    public void setEventCountDiff(String description) {
        this.eventCountDifferent = true;
        this.eventCountDiffDescription = description;
    }

    public void addStdoutDiff(String diff) {
        this.stdoutDiffs.add(diff);
    }

    public void addStderrDiff(String diff) {
        this.stderrDiffs.add(diff);
    }

    public void addCheckpointDiff(String diff) {
        this.checkpointDiffs.add(diff);
    }

    public List<String> getStdoutDiffs() {
        return Collections.unmodifiableList(stdoutDiffs);
    }

    public List<String> getStderrDiffs() {
        return Collections.unmodifiableList(stderrDiffs);
    }

    public List<String> getCheckpointDiffs() {
        return Collections.unmodifiableList(checkpointDiffs);
    }
}
