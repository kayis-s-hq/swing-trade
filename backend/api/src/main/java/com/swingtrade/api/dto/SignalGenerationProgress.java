package com.swingtrade.api.dto;


/**
 * Progress event emitted during streaming signal generation.
 * Emitted for each symbol: generating -> signal/skipped -> complete.
 */
public class SignalGenerationProgress {

    public enum EventType {
        STARTED,
        GENERATING,
        SENTIMENT_ANALYZING,
        SIGNAL_DONE,
        SKIPPED,
        COMPLETE
    }

    public enum Status {
        PROCESSING,
        DONE,
        SKIPPED,
        ERROR
    }

    private EventType eventType;
    private String symbol;
    private Status status;
    private String message;
    private Integer current;
    private Integer total;
    private SignalResponse signal;

    public SignalGenerationProgress() {}

    public static SignalGenerationProgress started(int total) {
        SignalGenerationProgress p = new SignalGenerationProgress();
        p.setEventType(EventType.STARTED);
        p.setStatus(Status.PROCESSING);
        p.setCurrent(0);
        p.setTotal(total);
        p.setMessage("Starting signal generation for " + total + " symbols");
        return p;
    }

    public static SignalGenerationProgress generating(String symbol, int current, int total) {
        SignalGenerationProgress p = new SignalGenerationProgress();
        p.setEventType(EventType.GENERATING);
        p.setSymbol(symbol);
        p.setStatus(Status.PROCESSING);
        p.setCurrent(current);
        p.setTotal(total);
        p.setMessage("Analyzing " + symbol);
        return p;
    }

    public static SignalGenerationProgress sentimentAnalyzing(String symbol, int current, int total) {
        SignalGenerationProgress p = new SignalGenerationProgress();
        p.setEventType(EventType.SENTIMENT_ANALYZING);
        p.setSymbol(symbol);
        p.setStatus(Status.PROCESSING);
        p.setCurrent(current);
        p.setTotal(total);
        p.setMessage("Analyzing sentiment for " + symbol);
        return p;
    }

    public static SignalGenerationProgress signalDone(String symbol, SignalResponse response, int current, int total) {
        SignalGenerationProgress p = new SignalGenerationProgress();
        p.setEventType(EventType.SIGNAL_DONE);
        p.setSymbol(symbol);
        p.setStatus(Status.DONE);
        p.setCurrent(current);
        p.setTotal(total);
        p.setSignal(response);
        p.setMessage("Signal generated for " + symbol);
        return p;
    }

    public static SignalGenerationProgress skipped(String symbol, String reason, int current, int total) {
        SignalGenerationProgress p = new SignalGenerationProgress();
        p.setEventType(EventType.SKIPPED);
        p.setSymbol(symbol);
        p.setStatus(Status.SKIPPED);
        p.setCurrent(current);
        p.setTotal(total);
        p.setMessage(reason);
        return p;
    }

    public static SignalGenerationProgress complete(int generated, int skipped, int total) {
        SignalGenerationProgress p = new SignalGenerationProgress();
        p.setEventType(EventType.COMPLETE);
        p.setStatus(Status.DONE);
        p.setCurrent(generated);
        p.setTotal(total);
        p.setMessage("Done: " + generated + " signals, " + skipped + " skipped");
        return p;
    }

    // Getters and Setters
    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Integer getCurrent() { return current; }
    public void setCurrent(Integer current) { this.current = current; }
    public Integer getTotal() { return total; }
    public void setTotal(Integer total) { this.total = total; }
    public SignalResponse getSignal() { return signal; }
    public void setSignal(SignalResponse signal) { this.signal = signal; }
}