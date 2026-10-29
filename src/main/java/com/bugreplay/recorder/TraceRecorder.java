package com.bugreplay.recorder;

import com.bugreplay.model.CheckpointData;
import com.bugreplay.model.ExecutionTrace;
import com.bugreplay.model.TraceEvent;
import com.bugreplay.model.TraceEventType;
import com.bugreplay.runner.ExecutionResult;
import com.bugreplay.runner.ProcessRunner;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * TraceRecorder transforms raw OS-level ExecutionResult data into a structured,
 * ordered ExecutionTrace with monotonically increasing sequence numbers.
 *
 * It inspects stdout lines for explicit checkpoint annotations emitted by supported
 * programs (e.g. "BUGREPLAY_CHECKPOINT:name=STATE;balance=500") and packages them
 * into structured CHECKPOINT events.
 */
public class TraceRecorder {

    public static final String CHECKPOINT_PREFIX_1 = "BUGREPLAY_CHECKPOINT:";
    public static final String CHECKPOINT_PREFIX_2 = "[BUGREPLAY_CHECKPOINT]:";

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
    private final SimpleDateFormat idDateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
    private final Random random = new Random();

    /**
     * Executes the target source file using the given runner and immediately records the trace.
     */
    public ExecutionTrace recordAndRun(ProcessRunner runner, File sourceFile, long timeoutMillis) {
        ExecutionResult result = runner.run(sourceFile, timeoutMillis);
        return record(sourceFile, result);
    }

    /**
     * Transforms an ExecutionResult into an ExecutionTrace with ordered events.
     */
    public ExecutionTrace record(File sourceFile, ExecutionResult result) {
        String programName = (sourceFile != null) ? sourceFile.getName() : "UnknownProgram";
        String sourcePath = (sourceFile != null) ? sourceFile.getAbsolutePath() : "";
        String runId = generateRunId(programName);
        String recordedAt = nowTimestamp();

        List<TraceEvent> events = new ArrayList<>();
        int sequence = 1;

        // 1. RUN_STARTED event
        String startTimeStr = formatTimestamp(result.getStartTimeMillis());
        events.add(TraceEvent.createStarted(sequence++, startTimeStr, programName));

        // 2. If compilation failed, record failure and return
        if (!result.isCompilationSuccess()) {
            String failTime = formatTimestamp(result.getEndTimeMillis());
            events.add(TraceEvent.createFailed(sequence++, failTime, result.getCompilationError(), -1));

            return new ExecutionTrace(
                    runId,
                    programName,
                    sourcePath,
                    recordedAt,
                    "COMPILE_ERROR",
                    false,
                    result.getCompilationError(),
                    -1,
                    result.getDurationMillis(),
                    events
            );
        }

        // 3. Process stdout lines in chronological order
        long stdoutBaseTime = result.getStartTimeMillis();
        for (String line : result.getStdoutLines()) {
            String eventTime = formatTimestamp(stdoutBaseTime);
            if (isCheckpointLine(line)) {
                CheckpointData checkpoint = parseCheckpoint(line);
                events.add(TraceEvent.createCheckpoint(sequence++, eventTime, checkpoint));
            } else {
                events.add(TraceEvent.createOutput(sequence++, eventTime, line));
            }
        }

        // 4. Process stderr lines
        for (String line : result.getStderrLines()) {
            String eventTime = formatTimestamp(result.getEndTimeMillis());
            events.add(TraceEvent.createErrorOutput(sequence++, eventTime, line));
        }

        // 5. Determine terminal event and status
        String finishTime = formatTimestamp(result.getEndTimeMillis());
        String status;

        if (result.isTimedOut()) {
            events.add(TraceEvent.createTimedOut(sequence++, finishTime, result.getDurationMillis()));
            status = "TIMED_OUT";
        } else if (result.isCancelled()) {
            events.add(TraceEvent.createFailed(sequence++, finishTime, "Execution cancelled by user", -1));
            status = "CANCELLED";
        } else if (result.getExitCode() == 0) {
            events.add(TraceEvent.createFinished(sequence++, finishTime, result.getExitCode(), result.getDurationMillis()));
            status = "SUCCESS";
        } else {
            events.add(TraceEvent.createFailed(sequence++, finishTime, "Process exited with non-zero status", result.getExitCode()));
            status = "RUNTIME_ERROR";
        }

        return new ExecutionTrace(
                runId,
                programName,
                sourcePath,
                recordedAt,
                status,
                true,
                "",
                result.getExitCode(),
                result.getDurationMillis(),
                events
        );
    }

    /**
     * Checks if a line contains an explicit checkpoint marker.
     */
    public boolean isCheckpointLine(String line) {
        if (line == null) return false;
        String trimmed = line.trim();
        return trimmed.startsWith(CHECKPOINT_PREFIX_1) || trimmed.startsWith(CHECKPOINT_PREFIX_2);
    }

    /**
     * Parses key-value pairs or structured data from a checkpoint line.
     * Example formats supported:
     *   BUGREPLAY_CHECKPOINT:name=AFTER_DEPOSIT;account=Alice;balance=750.0
     *   [BUGREPLAY_CHECKPOINT]:name=INIT,message=Ready,count=10
     */
    public CheckpointData parseCheckpoint(String line) {
        String trimmed = line.trim();
        String payload;
        if (trimmed.startsWith(CHECKPOINT_PREFIX_1)) {
            payload = trimmed.substring(CHECKPOINT_PREFIX_1.length()).trim();
        } else if (trimmed.startsWith(CHECKPOINT_PREFIX_2)) {
            payload = trimmed.substring(CHECKPOINT_PREFIX_2.length()).trim();
        } else {
            payload = trimmed;
        }

        String name = "CHECKPOINT";
        String message = "";
        Map<String, String> variables = new LinkedHashMap<>();

        // Support semicolon or comma delimited key=value tokens
        String[] tokens = payload.split("[;,]");
        for (String token : tokens) {
            token = token.trim();
            if (token.isEmpty()) continue;

            int equalIndex = token.indexOf('=');
            if (equalIndex > 0) {
                String key = token.substring(0, equalIndex).trim();
                String value = token.substring(equalIndex + 1).trim();

                if ("name".equalsIgnoreCase(key)) {
                    name = value;
                } else if ("message".equalsIgnoreCase(key) || "msg".equalsIgnoreCase(key)) {
                    message = value;
                } else {
                    variables.put(key, value);
                }
            } else {
                // If token without '=', treat as checkpoint name if name not yet set
                if ("CHECKPOINT".equals(name)) {
                    name = token;
                }
            }
        }

        return new CheckpointData(name, message, variables);
    }

    private String generateRunId(String programName) {
        String cleanName = programName.replace(".java", "").replaceAll("[^a-zA-Z0-9_-]", "");
        String datePart = idDateFormat.format(new Date());
        int randomPart = 1000 + random.nextInt(9000);
        return "run_" + datePart + "_" + cleanName + "_" + randomPart;
    }

    private String nowTimestamp() {
        return dateFormat.format(new Date());
    }

    private String formatTimestamp(long millis) {
        if (millis <= 0) return nowTimestamp();
        return dateFormat.format(new Date(millis));
    }
}
