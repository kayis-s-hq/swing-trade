package com.swingtrade.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Best-effort backoff before starting CPU-bound local LLM inference: if the Pi's
 * CPU is already above the configured threshold (e.g. from a prior analysis run),
 * wait for it to cool rather than stacking another multi-minute llama.cpp
 * generation on top of an already-hot system.
 *
 * Reads the standard Linux thermal sysfs interface directly (millidegrees C),
 * which works without special permissions unlike vcgencmd. If it can't be read
 * (e.g. running somewhere other than this Pi), this is a no-op.
 */
@Component
public class PiThermalGuard {

    private static final Logger logger = LoggerFactory.getLogger(PiThermalGuard.class);
    private static final Path THERMAL_ZONE = Path.of("/sys/class/thermal/thermal_zone0/temp");

    private final double maxCelsius;
    private final long pollIntervalMs;
    private final long maxWaitMs;

    public PiThermalGuard(
            @Value("${pi.thermal.max-celsius:78}") double maxCelsius,
            @Value("${pi.thermal.poll-interval-ms:5000}") long pollIntervalMs,
            @Value("${pi.thermal.max-wait-ms:60000}") long maxWaitMs) {
        this.maxCelsius = maxCelsius;
        this.pollIntervalMs = pollIntervalMs;
        this.maxWaitMs = maxWaitMs;
    }

    /**
     * Blocks (polling at pollIntervalMs, up to maxWaitMs total) while the CPU is
     * above maxCelsius. Gives up and proceeds anyway once maxWaitMs is exceeded,
     * so a persistently hot Pi (or a misread) never blocks an analysis forever.
     */
    public void awaitSafeTemperature() {
        long waited = 0;
        Double temp = readTemperatureCelsius();
        while (temp != null && temp > maxCelsius && waited < maxWaitMs) {
            logger.warn("CPU temperature {}C exceeds {}C threshold, delaying LLM inference ({}ms waited so far)",
                    temp, maxCelsius, waited);
            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            waited += pollIntervalMs;
            temp = readTemperatureCelsius();
        }
    }

    private Double readTemperatureCelsius() {
        try {
            String raw = Files.readString(THERMAL_ZONE).trim();
            return Double.parseDouble(raw) / 1000.0;
        } catch (IOException | NumberFormatException e) {
            return null;
        }
    }
}
