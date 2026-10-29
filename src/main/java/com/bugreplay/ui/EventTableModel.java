package com.bugreplay.ui;

import com.bugreplay.model.TraceEvent;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * EventTableModel provides a tabular view of the recorded TraceEvent objects
 * in chronological sequence order.
 */
public class EventTableModel extends AbstractTableModel {

    private static final String[] COLUMN_NAMES = {"Seq", "Timestamp", "Type", "Event Summary"};
    private final List<TraceEvent> events = new ArrayList<>();

    @Override
    public int getRowCount() {
        return events.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= events.size()) {
            return null;
        }

        TraceEvent event = events.get(rowIndex);
        switch (columnIndex) {
            case 0:
                return event.getSequenceNumber();
            case 1:
                return event.getTimestamp();
            case 2:
                return event.getType();
            case 3:
                return event.getMessage();
            default:
                return null;
        }
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        switch (columnIndex) {
            case 0:
                return Integer.class;
            case 1:
                return String.class;
            case 2:
                return Object.class;
            case 3:
                return String.class;
            default:
                return Object.class;
        }
    }

    /**
     * Sets the events to display and notifies the table view.
     */
    public void setEvents(List<TraceEvent> newEvents) {
        events.clear();
        if (newEvents != null) {
            events.addAll(newEvents);
        }
        fireTableDataChanged();
    }

    /**
     * Clears all events from the table.
     */
    public void clear() {
        events.clear();
        fireTableDataChanged();
    }

    /**
     * Returns the TraceEvent at the given row index.
     */
    public TraceEvent getEventAt(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < events.size()) {
            return events.get(rowIndex);
        }
        return null;
    }

    public List<TraceEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }
}
