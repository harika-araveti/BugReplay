package com.bugreplay.ui;

import com.bugreplay.analysis.ComparisonResult;
import com.bugreplay.analysis.TraceComparator;
import com.bugreplay.model.ExecutionTrace;
import com.bugreplay.storage.JsonTraceStorage;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * CompareDialog allows side-by-side comparison of two saved execution recordings.
 *
 * It visualizes observed differences in:
 * - Exit status and completion codes
 * - Execution duration
 * - Event counts
 * - Standard output lines
 * - Checkpoint variable states
 */
public class CompareDialog extends JDialog {

    private final JsonTraceStorage jsonTraceStorage;
    private final TraceComparator traceComparator = new TraceComparator();

    private final JComboBox<String> traceACombo = new JComboBox<>();
    private final JComboBox<String> traceBCombo = new JComboBox<>();
    private final JButton compareButton = new JButton("Compare Recordings");

    private final JTextArea reportArea = new JTextArea();
    private final JTextArea diffSummaryArea = new JTextArea();
    private final JTextArea stdoutDiffArea = new JTextArea();
    private final JTextArea checkpointDiffArea = new JTextArea();

    private final List<File> traceFiles = new ArrayList<>();

    public CompareDialog(JFrame parent, JsonTraceStorage storage) {
        super(parent, "Compare Two Execution Recordings — BugReplay", true);
        this.jsonTraceStorage = storage;

        setSize(950, 620);
        setMinimumSize(new Dimension(750, 450));
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(8, 8));

        initUI();
        loadSavedTraces();
    }

    private void initUI() {
        // 1. Top Selection Panel
        JPanel selectPanel = new JPanel(new GridBagLayout());
        selectPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(6, 8, 6, 8),
                BorderFactory.createTitledBorder("Select Recordings to Compare")
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Trace A
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        selectPanel.add(new JLabel("Trace A (Baseline/Buggy):"), gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0;
        selectPanel.add(traceACombo, gbc);

        // Trace B
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        selectPanel.add(new JLabel("Trace B (Comparison/Fixed):"), gbc);

        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1.0;
        selectPanel.add(traceBCombo, gbc);

        // Compare Button
        compareButton.setFont(compareButton.getFont().deriveFont(Font.BOLD));
        gbc.gridx = 2; gbc.gridy = 0; gbc.gridheight = 2; gbc.weightx = 0;
        gbc.fill = GridBagConstraints.BOTH;
        selectPanel.add(compareButton, gbc);

        add(selectPanel, BorderLayout.NORTH);

        // 2. Center Tabs for Differences
        JTabbedPane tabbedPane = new JTabbedPane();

        // Tab 1: Full Investigation Report
        reportArea.setEditable(false);
        reportArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        reportArea.setText("Select two recordings above and click 'Compare Recordings' to view the evidence report.");
        tabbedPane.addTab("Full Investigation Report", new JScrollPane(reportArea));

        // Tab 2: Checkpoint Differences
        checkpointDiffArea.setEditable(false);
        checkpointDiffArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        tabbedPane.addTab("Checkpoint Differences", new JScrollPane(checkpointDiffArea));

        // Tab 3: Output Differences
        stdoutDiffArea.setEditable(false);
        stdoutDiffArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        tabbedPane.addTab("Stdout Differences", new JScrollPane(stdoutDiffArea));

        add(tabbedPane, BorderLayout.CENTER);

        // 3. Bottom Close Button
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        buttonPanel.add(closeButton);
        add(buttonPanel, BorderLayout.SOUTH);

        // Compare Action Listener
        compareButton.addActionListener(e -> runComparison());
    }

    private void loadSavedTraces() {
        traceFiles.clear();
        traceACombo.removeAllItems();
        traceBCombo.removeAllItems();

        List<File> files = jsonTraceStorage.listTraceFiles();
        traceFiles.addAll(files);

        if (files.isEmpty()) {
            traceACombo.addItem("(No saved traces found in traces/ directory)");
            traceBCombo.addItem("(No saved traces found in traces/ directory)");
            compareButton.setEnabled(false);
            return;
        }

        compareButton.setEnabled(true);
        for (File f : files) {
            String label = f.getName().replace(".json", "");
            traceACombo.addItem(label);
            traceBCombo.addItem(label);
        }

        // Set default selection to first two files if available
        if (files.size() >= 2) {
            traceACombo.setSelectedIndex(0);
            traceBCombo.setSelectedIndex(1);
        }
    }

    private void runComparison() {
        int indexA = traceACombo.getSelectedIndex();
        int indexB = traceBCombo.getSelectedIndex();

        if (indexA < 0 || indexB < 0 || indexA >= traceFiles.size() || indexB >= traceFiles.size()) {
            JOptionPane.showMessageDialog(this, "Please select valid trace files for both Trace A and Trace B.",
                    "Invalid Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        File fileA = traceFiles.get(indexA);
        File fileB = traceFiles.get(indexB);

        try {
            ExecutionTrace traceA = jsonTraceStorage.loadTrace(fileA);
            ExecutionTrace traceB = jsonTraceStorage.loadTrace(fileB);

            ComparisonResult result = traceComparator.compare(traceA, traceB);

            // Populate Full Report Tab
            reportArea.setText(result.generateReportText());
            reportArea.setCaretPosition(0);

            // Populate Checkpoint Tab
            StringBuilder cpSb = new StringBuilder();
            cpSb.append("=== CHECKPOINT STATE DIFFERENCES ===\n\n");
            if (result.getCheckpointDiffs().isEmpty()) {
                cpSb.append("No checkpoint discrepancies observed between Trace A and Trace B.\n");
            } else {
                for (String diff : result.getCheckpointDiffs()) {
                    cpSb.append("• ").append(diff).append("\n\n");
                }
            }
            checkpointDiffArea.setText(cpSb.toString());
            checkpointDiffArea.setCaretPosition(0);

            // Populate Stdout Tab
            StringBuilder outSb = new StringBuilder();
            outSb.append("=== STANDARD OUTPUT (STDOUT) DIFFERENCES ===\n\n");
            if (result.getStdoutDiffs().isEmpty()) {
                outSb.append("Standard output text was completely identical.\n");
            } else {
                for (String diff : result.getStdoutDiffs()) {
                    outSb.append("• ").append(diff).append("\n\n");
                }
            }
            stdoutDiffArea.setText(outSb.toString());
            stdoutDiffArea.setCaretPosition(0);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to compare traces: " + ex.getMessage(),
                    "Comparison Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
