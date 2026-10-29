package com.bugreplay.ui;

import com.bugreplay.analysis.ReplayController;
import com.bugreplay.model.ExecutionTrace;
import com.bugreplay.model.TraceEvent;
import com.bugreplay.recorder.TraceRecorder;
import com.bugreplay.runner.ExecutionResult;
import com.bugreplay.runner.ProcessRunner;
import com.bugreplay.storage.DatabaseService;
import com.bugreplay.storage.JsonTraceStorage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.List;

/**
 * MainFrame is the primary Swing desktop window for BugReplay.
 *
 * It provides:
 * - Source file and built-in example selection
 * - Asynchronous execution and cancellation using SwingWorker
 * - Chronological event table with live inspection
 * - Replay navigation controls (First, Prev, Next, Last)
 * - Trace comparison dialog launcher
 * - Dedicated Standard Output and Standard Error viewing panes
 * - Detailed view of selected events and internal checkpoints
 */
public class MainFrame extends JFrame {

    private final ProcessRunner processRunner;
    private final TraceRecorder traceRecorder;
    private final JsonTraceStorage jsonTraceStorage;
    private final DatabaseService databaseService;
    private final Gson prettyGson;

    // UI Controls
    private final JTextField filePathField = new JTextField(32);
    private final JButton browseButton = new JButton("Browse...");
    private final JComboBox<String> exampleComboBox = new JComboBox<>();
    private final JSpinner timeoutSpinner = new JSpinner(new SpinnerNumberModel(5000, 500, 60000, 500));
    private final JButton runButton = new JButton("Run Program");
    private final JButton cancelButton = new JButton("Cancel");
    private final JButton historyButton = new JButton("View History...");
    private final JButton compareButton = new JButton("Compare Traces...");

    // Replay Navigation
    private final ReplayController replayController = new ReplayController();
    private final JButton firstEventButton = new JButton("|<");
    private final JButton prevEventButton = new JButton("< Prev");
    private final JLabel eventPositionLabel = new JLabel("No events loaded");
    private final JButton nextEventButton = new JButton("Next >");
    private final JButton lastEventButton = new JButton(">|");

    // Event & Details View
    private final EventTableModel eventTableModel = new EventTableModel();
    private final JTable eventTable = new JTable(eventTableModel);
    private final EventDetailsPanel detailsPanel = new EventDetailsPanel();

    // Output Tabs
    private final JTextArea stdoutArea = new JTextArea();
    private final JTextArea stderrArea = new JTextArea();
    private final JTextArea jsonTraceArea = new JTextArea();

    // Status Bar
    private final JLabel statusLabel = new JLabel("Status: Ready");
    private final JLabel durationLabel = new JLabel("Duration: -");
    private final JLabel runIdLabel = new JLabel("Run ID: -");
    private final JLabel dbStatusLabel = new JLabel("DB: Checking...");
    private final JProgressBar progressBar = new JProgressBar();

    // Active worker reference
    private SwingWorker<ExecutionTrace, Void> activeWorker;
    private ExecutionTrace currentTrace;

    public MainFrame() {
        this(new ProcessRunner(), new TraceRecorder(), new JsonTraceStorage(), new DatabaseService());
    }

    public MainFrame(ProcessRunner runner, TraceRecorder recorder, JsonTraceStorage storage) {
        this(runner, recorder, storage, new DatabaseService());
    }

    public MainFrame(ProcessRunner runner, TraceRecorder recorder, JsonTraceStorage storage, DatabaseService dbService) {
        this.processRunner = runner;
        this.traceRecorder = recorder;
        this.jsonTraceStorage = storage;
        this.databaseService = dbService;
        this.prettyGson = new GsonBuilder().setPrettyPrinting().create();

        setTitle("BugReplay — Java Execution Recording & Investigation Tool");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1150, 750);
        setMinimumSize(new Dimension(850, 550));
        setLocationRelativeTo(null);

        initUI();
        initExamples();
        initListeners();
    }

    private void initUI() {
        setLayout(new BorderLayout(5, 5));

        // 1. Top Control Panel
        add(createControlPanel(), BorderLayout.NORTH);

        // 2. Center Workspace Split
        add(createCenterSplitPane(), BorderLayout.CENTER);

        // 3. Bottom Status Bar
        add(createStatusBar(), BorderLayout.SOUTH);
    }

    private JPanel createControlPanel() {
        JPanel controlPanel = new JPanel(new GridBagLayout());
        controlPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(6, 8, 6, 8),
                BorderFactory.createTitledBorder("Execution Controls")
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 1: File selection and Built-in Examples
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        controlPanel.add(new JLabel("Source File:"), gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0;
        controlPanel.add(filePathField, gbc);

        gbc.gridx = 2; gbc.gridy = 0; gbc.weightx = 0;
        controlPanel.add(browseButton, gbc);

        gbc.gridx = 3; gbc.gridy = 0; gbc.weightx = 0;
        controlPanel.add(new JLabel("Example:"), gbc);

        gbc.gridx = 4; gbc.gridy = 0; gbc.weightx = 0;
        exampleComboBox.setPreferredSize(new Dimension(200, 24));
        controlPanel.add(exampleComboBox, gbc);

        // Row 2: Timeout, Run, Cancel, History, and Compare buttons
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actionPanel.add(new JLabel("Timeout (ms):"));
        actionPanel.add(timeoutSpinner);

        runButton.setFont(runButton.getFont().deriveFont(Font.BOLD));
        actionPanel.add(runButton);

        cancelButton.setEnabled(false);
        cancelButton.setForeground(new Color(180, 0, 0));
        actionPanel.add(cancelButton);

        actionPanel.add(historyButton);
        actionPanel.add(compareButton);

        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 5; gbc.weightx = 1.0;
        controlPanel.add(actionPanel, gbc);

        return controlPanel;
    }

    private JSplitPane createCenterSplitPane() {
        // Left side: Event table (top) + Event details (bottom)
        eventTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        eventTable.setFillsViewportHeight(true);
        eventTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        eventTable.getColumnModel().getColumn(1).setPreferredWidth(160);
        eventTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        eventTable.getColumnModel().getColumn(3).setPreferredWidth(300);

        JScrollPane tableScroll = new JScrollPane(eventTable);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Recorded Events (Chronological Order)"));

        // Replay navigation bar
        JPanel replayNavPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 2));
        replayNavPanel.setBorder(BorderFactory.createEmptyBorder(2, 4, 4, 4));
        firstEventButton.setToolTipText("Jump to First Event");
        prevEventButton.setToolTipText("Previous Event");
        nextEventButton.setToolTipText("Next Event");
        lastEventButton.setToolTipText("Jump to Last Event");
        firstEventButton.setEnabled(false);
        prevEventButton.setEnabled(false);
        nextEventButton.setEnabled(false);
        lastEventButton.setEnabled(false);

        replayNavPanel.add(new JLabel("Replay:"));
        replayNavPanel.add(firstEventButton);
        replayNavPanel.add(prevEventButton);
        replayNavPanel.add(eventPositionLabel);
        replayNavPanel.add(nextEventButton);
        replayNavPanel.add(lastEventButton);

        JPanel tableContainer = new JPanel(new BorderLayout());
        tableContainer.add(tableScroll, BorderLayout.CENTER);
        tableContainer.add(replayNavPanel, BorderLayout.SOUTH);

        JSplitPane leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableContainer, detailsPanel);
        leftSplit.setResizeWeight(0.55);

        // Right side: Output tabs (stdout, stderr, raw JSON)
        JTabbedPane tabbedPane = new JTabbedPane();

        stdoutArea.setEditable(false);
        stdoutArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        tabbedPane.addTab("Standard Output", new JScrollPane(stdoutArea));

        stderrArea.setEditable(false);
        stderrArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        stderrArea.setForeground(new Color(160, 0, 0));
        tabbedPane.addTab("Standard Error", new JScrollPane(stderrArea));

        jsonTraceArea.setEditable(false);
        jsonTraceArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        tabbedPane.addTab("Saved JSON Trace", new JScrollPane(jsonTraceArea));

        // Master horizontal split
        JSplitPane masterSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplit, tabbedPane);
        masterSplit.setResizeWeight(0.55);

        return masterSplit;
    }

    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 4));
        statusBar.setBorder(BorderFactory.createEtchedBorder());

        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
        progressBar.setPreferredSize(new Dimension(120, 16));
        progressBar.setVisible(false);

        statusBar.add(statusLabel);
        statusBar.add(durationLabel);
        statusBar.add(runIdLabel);
        statusBar.add(dbStatusLabel);
        statusBar.add(progressBar);

        updateDbStatus();

        return statusBar;
    }

    public void updateDbStatus() {
        if (databaseService != null && databaseService.isAvailable()) {
            dbStatusLabel.setText("DB: MySQL Connected");
            dbStatusLabel.setForeground(new Color(0, 120, 0));
        } else {
            dbStatusLabel.setText("DB: Offline (JSON only)");
            dbStatusLabel.setForeground(new Color(180, 80, 0));
        }
    }

    private void initExamples() {
        exampleComboBox.addItem("-- Select an Example Program --");
        exampleComboBox.addItem("NormalExample.java");
        exampleComboBox.addItem("CheckpointExample.java");
        exampleComboBox.addItem("BuggyDiscount.java");
        exampleComboBox.addItem("FixedDiscount.java");
        exampleComboBox.addItem("ExceptionExample.java");
        exampleComboBox.addItem("TimeoutExample.java");
        exampleComboBox.addItem("CompilationErrorExample.java");
    }

    private void initListeners() {
        // Browse button
        browseButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(new File("examples"));
            chooser.setDialogTitle("Select Java Source File");
            chooser.setFileFilter(new FileNameExtensionFilter("Java Source Files (*.java)", "java"));
            int result = chooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                filePathField.setText(file.getAbsolutePath());
            }
        });

        // Example dropdown selection
        exampleComboBox.addActionListener(e -> {
            String selected = (String) exampleComboBox.getSelectedItem();
            if (selected != null && !selected.startsWith("--")) {
                File exampleFile = new File("examples", selected);
                filePathField.setText(exampleFile.getAbsolutePath());
            }
        });

        // Event table row selection
        eventTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = eventTable.getSelectedRow();
                if (selectedRow >= 0) {
                    replayController.jumpTo(selectedRow);
                    TraceEvent event = replayController.getCurrentEvent();
                    detailsPanel.displayEvent(event);
                    updateReplayNavButtons();
                }
            }
        });

        // Run button
        runButton.addActionListener(e -> startExecution());

        // Cancel button
        cancelButton.addActionListener(e -> {
            if (activeWorker != null && !activeWorker.isDone()) {
                statusLabel.setText("Status: Cancelling...");
                processRunner.cancel();
                activeWorker.cancel(true);
            }
        });

        // View History button
        historyButton.addActionListener(e -> {
            HistoryDialog dialog = new HistoryDialog(this, databaseService, jsonTraceStorage);
            dialog.setVisible(true);
        });

        // Compare Traces button
        compareButton.addActionListener(e -> {
            CompareDialog dialog = new CompareDialog(this, jsonTraceStorage);
            dialog.setVisible(true);
        });

        // Replay Navigation listeners
        firstEventButton.addActionListener(e -> navigateToEvent(replayController.first()));
        prevEventButton.addActionListener(e -> navigateToEvent(replayController.previous()));
        nextEventButton.addActionListener(e -> navigateToEvent(replayController.next()));
        lastEventButton.addActionListener(e -> navigateToEvent(replayController.last()));
    }

    private void navigateToEvent(TraceEvent event) {
        if (event == null) return;
        int index = replayController.getCurrentIndex();
        if (index >= 0 && index < eventTableModel.getRowCount()) {
            eventTable.setRowSelectionInterval(index, index);
            eventTable.scrollRectToVisible(eventTable.getCellRect(index, 0, true));
            detailsPanel.displayEvent(event);
            updateReplayNavButtons();
        }
    }

    private void updateReplayNavButtons() {
        eventPositionLabel.setText(replayController.getPositionText());
        firstEventButton.setEnabled(replayController.canGoPrevious());
        prevEventButton.setEnabled(replayController.canGoPrevious());
        nextEventButton.setEnabled(replayController.canGoNext());
        lastEventButton.setEnabled(replayController.canGoNext());
    }

    /**
     * Launches the execution runner on a background SwingWorker thread
     * to keep the user interface responsive.
     */
    public void startExecution() {
        String path = filePathField.getText().trim();
        if (path.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select a .java source file to execute.",
                    "No File Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        File sourceFile = new File(path);
        if (!sourceFile.exists()) {
            JOptionPane.showMessageDialog(this, "Selected file does not exist:\n" + path,
                    "File Not Found", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int timeoutMillis = (Integer) timeoutSpinner.getValue();

        // UI state: running
        setUiRunningState(true);
        statusLabel.setText("Status: Compiling and Running...");
        statusLabel.setForeground(new Color(0, 100, 180));
        durationLabel.setText("Duration: -");
        runIdLabel.setText("Run ID: -");
        eventTableModel.clear();
        detailsPanel.clear();
        stdoutArea.setText("");
        stderrArea.setText("");
        jsonTraceArea.setText("");

        activeWorker = new SwingWorker<ExecutionTrace, Void>() {
            @Override
            protected ExecutionTrace doInBackground() throws Exception {
                // 1. Run process via ProcessRunner (compiled & executed in isolation)
                ExecutionResult result = processRunner.run(sourceFile, timeoutMillis);

                // 2. Transform into structured ExecutionTrace with monotonic sequence numbers
                ExecutionTrace trace = traceRecorder.record(sourceFile, result);

                // 3. Persist primary trace as JSON file
                File savedJsonFile = null;
                try {
                    savedJsonFile = jsonTraceStorage.saveTrace(trace);
                } catch (Exception ex) {
                    System.err.println("Warning: could not save JSON trace: " + ex.getMessage());
                }

                // 4. Mirror metadata to MySQL if database is reachable
                if (databaseService != null && savedJsonFile != null) {
                    try {
                        databaseService.saveRunMetadata(trace, savedJsonFile);
                    } catch (Exception ex) {
                        System.err.println("Note: MySQL metadata mirroring skipped: " + ex.getMessage());
                    }
                }

                return trace;
            }

            @Override
            protected void done() {
                setUiRunningState(false);
                updateDbStatus();
                if (isCancelled()) {
                    statusLabel.setText("Status: Execution Cancelled");
                    statusLabel.setForeground(new Color(180, 0, 0));
                    return;
                }

                try {
                    ExecutionTrace trace = get();
                    displayTrace(trace);
                } catch (Exception ex) {
                    statusLabel.setText("Status: Internal Error");
                    statusLabel.setForeground(new Color(180, 0, 0));
                    JOptionPane.showMessageDialog(MainFrame.this,
                            "An unexpected error occurred during execution:\n" + ex.getMessage(),
                            "Execution Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        activeWorker.execute();
    }

    /**
     * Updates the UI with the completed execution trace data.
     */
    public void displayTrace(ExecutionTrace trace) {
        this.currentTrace = trace;
        if (trace == null) return;

        // Populate event table
        eventTableModel.setEvents(trace.getEvents());
        replayController.setTrace(trace);
        updateReplayNavButtons();
        if (eventTableModel.getRowCount() > 0) {
            eventTable.setRowSelectionInterval(0, 0);
            detailsPanel.displayEvent(eventTableModel.getEventAt(0));
        }

        // Separate stdout and stderr display
        StringBuilder stdoutBuilder = new StringBuilder();
        StringBuilder stderrBuilder = new StringBuilder();

        for (TraceEvent event : trace.getEvents()) {
            if (event.getType() == com.bugreplay.model.TraceEventType.OUTPUT) {
                stdoutBuilder.append(event.getMessage()).append(System.lineSeparator());
            } else if (event.getType() == com.bugreplay.model.TraceEventType.ERROR_OUTPUT) {
                stderrBuilder.append(event.getMessage()).append(System.lineSeparator());
            }
        }

        if (!trace.isCompilationSuccess()) {
            stderrBuilder.append(trace.getCompilationError());
        }

        stdoutArea.setText(stdoutBuilder.toString());
        stdoutArea.setCaretPosition(0);

        stderrArea.setText(stderrBuilder.toString());
        stderrArea.setCaretPosition(0);

        // JSON View
        jsonTraceArea.setText(prettyGson.toJson(trace));
        jsonTraceArea.setCaretPosition(0);

        // Status update
        durationLabel.setText("Duration: " + trace.getDurationMillis() + " ms");
        runIdLabel.setText("Run ID: " + trace.getRunId());

        String status = trace.getExecutionStatus();
        if ("SUCCESS".equals(status)) {
            statusLabel.setText("Status: Completed (Exit Code " + trace.getExitCode() + ")");
            statusLabel.setForeground(new Color(0, 130, 0));
        } else if ("TIMED_OUT".equals(status)) {
            statusLabel.setText("Status: Execution Timed Out");
            statusLabel.setForeground(new Color(180, 80, 0));
        } else if ("COMPILE_ERROR".equals(status)) {
            statusLabel.setText("Status: Compilation Failed");
            statusLabel.setForeground(new Color(180, 0, 0));
        } else if ("CANCELLED".equals(status)) {
            statusLabel.setText("Status: Cancelled by User");
            statusLabel.setForeground(new Color(180, 0, 0));
        } else {
            statusLabel.setText("Status: Failed (Exit Code " + trace.getExitCode() + ")");
            statusLabel.setForeground(new Color(180, 0, 0));
        }
    }

    private void setUiRunningState(boolean running) {
        runButton.setEnabled(!running);
        cancelButton.setEnabled(running);
        browseButton.setEnabled(!running);
        exampleComboBox.setEnabled(!running);
        timeoutSpinner.setEnabled(!running);
        progressBar.setIndeterminate(running);
        progressBar.setVisible(running);
    }

    public ExecutionTrace getCurrentTrace() {
        return currentTrace;
    }

    public EventTableModel getEventTableModel() {
        return eventTableModel;
    }

    public JTable getEventTable() {
        return eventTable;
    }

    public void setFilePath(String path) {
        filePathField.setText(path);
    }
}
