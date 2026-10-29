package com.bugreplay.ui;

import com.bugreplay.model.CheckpointData;
import com.bugreplay.model.TraceEvent;
import com.bugreplay.model.TraceEventType;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Map;

/**
 * EventDetailsPanel displays deep details of the currently selected TraceEvent:
 * - Event metadata (sequence number, timestamp, type)
 * - Full text payload in a monospaced text area
 * - Structured checkpoint variable table when the event is a CHECKPOINT
 */
public class EventDetailsPanel extends JPanel {

    private final JLabel seqLabel = new JLabel("Seq: -");
    private final JLabel timeLabel = new JLabel("Time: -");
    private final JLabel typeLabel = new JLabel("Type: -");
    private final JTextArea messageArea = new JTextArea(4, 30);
    private final DefaultTableModel checkpointTableModel;
    private final JTable checkpointTable;
    private final JPanel checkpointContainer;

    public EventDetailsPanel() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder("Selected Event Details"));

        // Header panel for metadata
        JPanel headerPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 6, 2, 6);
        gbc.anchor = GridBagConstraints.WEST;

        seqLabel.setFont(seqLabel.getFont().deriveFont(Font.BOLD));
        typeLabel.setFont(typeLabel.getFont().deriveFont(Font.BOLD));

        gbc.gridx = 0; gbc.gridy = 0; headerPanel.add(seqLabel, gbc);
        gbc.gridx = 1; gbc.gridy = 0; headerPanel.add(typeLabel, gbc);
        gbc.gridx = 2; gbc.gridy = 0; headerPanel.add(timeLabel, gbc);
        gbc.gridx = 3; gbc.gridy = 0; gbc.weightx = 1.0;
        headerPanel.add(new JLabel(""), gbc); // spacer

        // Message text area
        messageArea.setEditable(false);
        messageArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        JScrollPane messageScroll = new JScrollPane(messageArea);
        messageScroll.setBorder(BorderFactory.createTitledBorder("Event Payload / Message"));

        // Checkpoint table setup
        checkpointTableModel = new DefaultTableModel(new String[]{"Variable Name", "Recorded Value"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        checkpointTable = new JTable(checkpointTableModel);
        checkpointTable.setFillsViewportHeight(true);
        JScrollPane checkpointScroll = new JScrollPane(checkpointTable);

        checkpointContainer = new JPanel(new BorderLayout());
        checkpointContainer.setBorder(BorderFactory.createTitledBorder("Checkpoint Internal State"));
        checkpointContainer.add(checkpointScroll, BorderLayout.CENTER);
        checkpointContainer.setVisible(false);

        // Split message and checkpoint if needed
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, messageScroll, checkpointContainer);
        splitPane.setResizeWeight(0.5);

        add(headerPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);

        clear();
    }

    /**
     * Updates the panel to display the selected event's details.
     */
    public void displayEvent(TraceEvent event) {
        if (event == null) {
            clear();
            return;
        }

        seqLabel.setText("Sequence: #" + event.getSequenceNumber());
        timeLabel.setText("Time: " + event.getTimestamp());
        typeLabel.setText("Type: " + event.getType());

        // Color badge for type
        if (event.getType() == TraceEventType.ERROR_OUTPUT || event.getType() == TraceEventType.RUN_FAILED) {
            typeLabel.setForeground(new Color(180, 0, 0));
        } else if (event.getType() == TraceEventType.CHECKPOINT) {
            typeLabel.setForeground(new Color(0, 100, 180));
        } else if (event.getType() == TraceEventType.RUN_FINISHED) {
            typeLabel.setForeground(new Color(0, 130, 0));
        } else {
            typeLabel.setForeground(Color.DARK_GRAY);
        }

        messageArea.setText(event.getMessage());
        messageArea.setCaretPosition(0);

        // Handle checkpoint variables
        checkpointTableModel.setRowCount(0);
        if (event.getType() == TraceEventType.CHECKPOINT && event.getCheckpoint() != null) {
            CheckpointData cp = event.getCheckpoint();
            checkpointContainer.setBorder(BorderFactory.createTitledBorder("Checkpoint: " + cp.getName()));

            Map<String, String> vars = cp.getVariables();
            if (vars != null && !vars.isEmpty()) {
                for (Map.Entry<String, String> entry : vars.entrySet()) {
                    checkpointTableModel.addRow(new Object[]{entry.getKey(), entry.getValue()});
                }
            } else {
                checkpointTableModel.addRow(new Object[]{"(No variables recorded)", ""});
            }
            checkpointContainer.setVisible(true);
        } else {
            checkpointContainer.setVisible(false);
        }

        revalidate();
        repaint();
    }

    /**
     * Clears the display to an empty initial state.
     */
    public void clear() {
        seqLabel.setText("Sequence: -");
        timeLabel.setText("Time: -");
        typeLabel.setText("Type: -");
        typeLabel.setForeground(Color.DARK_GRAY);
        messageArea.setText("Select an event from the table above to view full details.");
        checkpointTableModel.setRowCount(0);
        checkpointContainer.setVisible(false);
    }
}
