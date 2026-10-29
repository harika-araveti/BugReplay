package com.bugreplay.ui;

import com.bugreplay.model.ExecutionTrace;
import com.bugreplay.model.RunMetadata;
import com.bugreplay.storage.DatabaseService;
import com.bugreplay.storage.JsonTraceStorage;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * HistoryDialog allows the user to browse past execution recordings.
 *
 * It seamlessly queries MySQL when connected, or falls back to reading
 * saved JSON traces from the traces/ directory when MySQL is offline.
 */
public class HistoryDialog extends JDialog {

    private final MainFrame parentFrame;
    private final DatabaseService databaseService;
    private final JsonTraceStorage jsonTraceStorage;

    private final DefaultTableModel tableModel;
    private final JTable historyTable;
    private final JLabel sourceLabel = new JLabel("Data Source: Checking...");
    private final JButton loadButton = new JButton("Load into Viewer");
    private final JButton refreshButton = new JButton("Refresh");
    private final JButton closeButton = new JButton("Close");

    private final List<RunMetadata> currentRuns = new ArrayList<>();

    public HistoryDialog(MainFrame parent, DatabaseService dbService, JsonTraceStorage storage) {
        super(parent, "Execution Recording History", true);
        this.parentFrame = parent;
        this.databaseService = dbService;
        this.jsonTraceStorage = storage;

        setSize(850, 480);
        setMinimumSize(new Dimension(650, 350));
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(8, 8));

        // 1. Top status banner
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        sourceLabel.setFont(sourceLabel.getFont().deriveFont(Font.BOLD));
        topPanel.add(sourceLabel);
        add(topPanel, BorderLayout.NORTH);

        // 2. Center Table
        String[] columns = {"Run ID", "Program", "Recorded At", "Status", "Exit", "Duration", "Events"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        historyTable = new JTable(tableModel);
        historyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyTable.setFillsViewportHeight(true);

        JScrollPane scrollPane = new JScrollPane(historyTable);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        add(scrollPane, BorderLayout.CENTER);

        // 3. Bottom controls
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        loadButton.setFont(loadButton.getFont().deriveFont(Font.BOLD));
        buttonPanel.add(refreshButton);
        buttonPanel.add(loadButton);
        buttonPanel.add(closeButton);
        add(buttonPanel, BorderLayout.SOUTH);

        // Listeners
        refreshButton.addActionListener(e -> refreshHistory());
        closeButton.addActionListener(e -> dispose());
        loadButton.addActionListener(e -> loadSelectedRun());

        // Double-click table row to load
        historyTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    loadSelectedRun();
                }
            }
        });

        refreshHistory();
    }

    public void refreshHistory() {
        tableModel.setRowCount(0);
        currentRuns.clear();

        boolean dbOk = databaseService != null && databaseService.isAvailable();

        if (dbOk) {
            sourceLabel.setText("Data Source: MySQL Database (Connected)");
            sourceLabel.setForeground(new Color(0, 120, 0));
            List<RunMetadata> dbRuns = databaseService.listRecentRuns(50);
            currentRuns.addAll(dbRuns);
        } else {
            sourceLabel.setText("Data Source: Local JSON Directory (MySQL Offline / Unreachable)");
            sourceLabel.setForeground(new Color(180, 80, 0));

            // Load directly from JSON trace files
            List<File> files = jsonTraceStorage.listTraceFiles();
            for (File file : files) {
                try {
                    ExecutionTrace trace = jsonTraceStorage.loadTrace(file);
                    currentRuns.add(RunMetadata.fromTrace(trace, file));
                } catch (Exception ignored) {
                    // Skip corrupt or unreadable files gracefully
                }
            }
        }

        for (RunMetadata meta : currentRuns) {
            tableModel.addRow(new Object[]{
                    meta.getRunId(),
                    meta.getProgramName(),
                    meta.getRecordedAt(),
                    meta.getExecutionStatus(),
                    meta.getExitCode(),
                    meta.getDurationMillis() + " ms",
                    meta.getEventCount()
            });
        }

        if (tableModel.getRowCount() > 0) {
            historyTable.setRowSelectionInterval(0, 0);
        }
    }

    private void loadSelectedRun() {
        int selectedRow = historyTable.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= currentRuns.size()) {
            JOptionPane.showMessageDialog(this, "Please select a run from the table first.",
                    "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        RunMetadata meta = currentRuns.get(selectedRow);
        try {
            ExecutionTrace trace = null;
            if (meta.getTraceFilePath() != null && !meta.getTraceFilePath().isEmpty()) {
                File file = new File(meta.getTraceFilePath());
                if (file.exists()) {
                    trace = jsonTraceStorage.loadTrace(file);
                }
            }

            if (trace == null) {
                trace = jsonTraceStorage.loadTraceByRunId(meta.getRunId());
            }

            if (trace != null && parentFrame != null) {
                parentFrame.displayTrace(trace);
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, "Could not find trace file for Run ID: " + meta.getRunId(),
                        "File Missing", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to load trace: " + ex.getMessage(),
                    "Load Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
