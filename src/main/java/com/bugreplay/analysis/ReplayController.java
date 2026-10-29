package com.bugreplay.analysis;

import com.bugreplay.model.ExecutionTrace;
import com.bugreplay.model.TraceEvent;

import java.util.List;

/**
 * ReplayController manages navigation state over an ExecutionTrace.
 *
 * For Version 1:
 * "Replay" means stepping forward and backward through the recorded chronological
 * event stream and inspecting the exact evidence captured at each point.
 */
public class ReplayController {

    private ExecutionTrace currentTrace;
    private int currentIndex = -1;

    public ReplayController() {
    }

    public ReplayController(ExecutionTrace trace) {
        setTrace(trace);
    }

    public void setTrace(ExecutionTrace trace) {
        this.currentTrace = trace;
        if (trace != null && trace.getEvents() != null && !trace.getEvents().isEmpty()) {
            this.currentIndex = 0;
        } else {
            this.currentIndex = -1;
        }
    }

    public boolean hasTrace() {
        return currentTrace != null && currentTrace.getEvents() != null && !currentTrace.getEvents().isEmpty();
    }

    public TraceEvent getCurrentEvent() {
        if (!hasTrace() || currentIndex < 0 || currentIndex >= currentTrace.getEvents().size()) {
            return null;
        }
        return currentTrace.getEvents().get(currentIndex);
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public int getTotalEvents() {
        return hasTrace() ? currentTrace.getEvents().size() : 0;
    }

    public boolean canGoPrevious() {
        return hasTrace() && currentIndex > 0;
    }

    public boolean canGoNext() {
        return hasTrace() && currentIndex < (currentTrace.getEvents().size() - 1);
    }

    public TraceEvent first() {
        if (hasTrace()) {
            currentIndex = 0;
            return getCurrentEvent();
        }
        return null;
    }

    public TraceEvent previous() {
        if (canGoPrevious()) {
            currentIndex--;
            return getCurrentEvent();
        }
        return getCurrentEvent();
    }

    public TraceEvent next() {
        if (canGoNext()) {
            currentIndex++;
            return getCurrentEvent();
        }
        return getCurrentEvent();
    }

    public TraceEvent last() {
        if (hasTrace()) {
            currentIndex = currentTrace.getEvents().size() - 1;
            return getCurrentEvent();
        }
        return null;
    }

    public TraceEvent jumpTo(int index) {
        if (hasTrace() && index >= 0 && index < currentTrace.getEvents().size()) {
            currentIndex = index;
            return getCurrentEvent();
        }
        return getCurrentEvent();
    }

    public String getPositionText() {
        if (!hasTrace()) {
            return "No events loaded";
        }
        return "Event " + (currentIndex + 1) + " of " + getTotalEvents();
    }
}
