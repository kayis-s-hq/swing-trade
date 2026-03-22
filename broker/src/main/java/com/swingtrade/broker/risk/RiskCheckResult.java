package com.swingtrade.broker.risk;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of a risk check operation.
 * Contains success status and any warnings or errors.
 */
public class RiskCheckResult {

    /**
     * Whether the risk check passed
     */
    private boolean passed;

    /**
     * Risk check messages (warnings, errors, info)
     */
    private List<String> messages;

    /**
     * Risk check type that was performed
     */
    private String checkType;

    public RiskCheckResult() {
        this.passed = true;
        this.messages = new ArrayList<>();
    }

    public RiskCheckResult(boolean passed) {
        this.passed = passed;
        this.messages = new ArrayList<>();
    }

    public RiskCheckResult(boolean passed, String message) {
        this.passed = passed;
        this.messages = new ArrayList<>();
        if (message != null && !message.isEmpty()) {
            this.messages.add(message);
        }
    }

    // Getters and Setters

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public List<String> getMessages() {
        return messages;
    }

    public void setMessages(List<String> messages) {
        this.messages = messages;
    }

    public String getCheckType() {
        return checkType;
    }

    public void setCheckType(String checkType) {
        this.checkType = checkType;
    }

    // Builder methods for fluent API

    public RiskCheckResult addMessage(String message) {
        this.messages.add(message);
        return this;
    }

    public RiskCheckResult addWarning(String message) {
        this.passed = false;
        this.messages.add("WARNING: " + message);
        return this;
    }

    public RiskCheckResult addError(String message) {
        this.passed = false;
        this.messages.add("ERROR: " + message);
        return this;
    }

    public RiskCheckResult addInfo(String message) {
        this.messages.add("INFO: " + message);
        return this;
    }

    /**
     * Check if there are any warnings
     */
    public boolean hasWarnings() {
        return messages.stream().anyMatch(m -> m.startsWith("WARNING:"));
    }

    /**
     * Check if there are any errors
     */
    public boolean hasErrors() {
        return messages.stream().anyMatch(m -> m.startsWith("ERROR:"));
    }

    @Override
    public String toString() {
        return "RiskCheckResult{" +
                "passed=" + passed +
                ", messages=" + messages +
                ", checkType='" + checkType + '\'' +
                '}';
    }
}
