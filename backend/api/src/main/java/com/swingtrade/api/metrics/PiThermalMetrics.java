package com.swingtrade.api.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Publishes the Pi's CPU temperature and thermal-throttle state as Prometheus
 * gauges, so sustained LLM load can be watched over time in the Grafana that
 * already scrapes this app — rather than by sampling
 * /sys/class/thermal by hand.
 *
 * <p>Both readings are best-effort: on hardware without these sysfs entries the
 * gauges simply report NaN rather than failing startup.
 */
@Component
public class PiThermalMetrics {

    private static final Logger logger = LoggerFactory.getLogger(PiThermalMetrics.class);

    private static final Path THERMAL_ZONE = Path.of("/sys/class/thermal/thermal_zone0/temp");
    private static final Path CPU_FREQ = Path.of("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq");

    public PiThermalMetrics(MeterRegistry registry) {
        Gauge.builder("swingtrade_pi_cpu_temperature_celsius", this, PiThermalMetrics::readTemperatureCelsius)
                .description("Pi CPU temperature. LLM inference on the local backend is the dominant heat source; "
                        + "sustained values approaching 80C indicate throttling risk.")
                .baseUnit("celsius")
                .strongReference(true)
                .register(registry);

        Gauge.builder("swingtrade_pi_cpu_frequency_hertz", this, PiThermalMetrics::readCpuFrequencyHertz)
                .description("Current CPU clock. A drop under sustained load is the visible symptom of "
                        + "thermal throttling.")
                .baseUnit("hertz")
                .strongReference(true)
                .register(registry);

        logger.info("Pi thermal metrics registered (temperature readable: {})",
                Double.isNaN(readTemperatureCelsius(this)) ? "no" : "yes");
    }

    private static double readTemperatureCelsius(PiThermalMetrics self) {
        return readLongFrom(THERMAL_ZONE) / 1000.0;
    }

    private static double readCpuFrequencyHertz(PiThermalMetrics self) {
        // scaling_cur_freq is reported in kHz.
        return readLongFrom(CPU_FREQ) * 1000.0;
    }

    private static double readLongFrom(Path path) {
        try {
            return Double.parseDouble(Files.readString(path).trim());
        } catch (IOException | NumberFormatException e) {
            return Double.NaN;
        }
    }
}
