package com.bugreplay.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CheckpointData holds explicit snapshot information emitted by supported example programs.
 *
 * It contains:
 * - A descriptive checkpoint name (e.g. "INIT_STATE", "AFTER_TRANSACTION")
 * - A human-readable message or note
 * - A map of variable names to their recorded values
 */
public class CheckpointData {

    private String name;
    private String message;
    private Map<String, String> variables;

    // No-arg constructor required for JSON deserialization by Gson
    public CheckpointData() {
        this.variables = new LinkedHashMap<>();
    }

    public CheckpointData(String name, String message, Map<String, String> variables) {
        this.name = (name != null) ? name : "UNNAMED_CHECKPOINT";
        this.message = (message != null) ? message : "";
        this.variables = (variables != null) ? new LinkedHashMap<>(variables) : new LinkedHashMap<String, String>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, String> getVariables() {
        return (variables != null) ? Collections.unmodifiableMap(variables) : Collections.<String, String>emptyMap();
    }

    public void setVariables(Map<String, String> variables) {
        this.variables = (variables != null) ? new LinkedHashMap<>(variables) : new LinkedHashMap<String, String>();
    }

    public void putVariable(String key, String value) {
        if (this.variables == null) {
            this.variables = new LinkedHashMap<>();
        }
        this.variables.put(key, value);
    }

    @Override
    public String toString() {
        return "CheckpointData{" +
                "name='" + name + '\'' +
                ", message='" + message + '\'' +
                ", variables=" + variables +
                '}';
    }
}
