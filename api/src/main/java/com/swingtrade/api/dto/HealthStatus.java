package com.swingtrade.api.dto;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Response DTO for health check endpoints.
 * Provides system health status and component information.
 */
public class HealthStatus {

    private String status;
    private LocalDateTime timestamp;
    private Map<String, ComponentStatus> components;
    private SystemInfo systemInfo;

    public HealthStatus() {
        this.status = "UP";
        this.timestamp = LocalDateTime.now();
        this.components = new HashMap<>();
    }

    // Getters and Setters
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, ComponentStatus> getComponents() {
        return components;
    }

    public void setComponents(Map<String, ComponentStatus> components) {
        this.components = components;
    }

    public SystemInfo getSystemInfo() {
        return systemInfo;
    }

    public void setSystemInfo(SystemInfo systemInfo) {
        this.systemInfo = systemInfo;
    }

    /**
     * Add a component status to the health check.
     */
    public void addComponent(String name, ComponentStatus status) {
        this.components.put(name, status);
        // Update overall status based on components
        updateOverallStatus();
    }

    /**
     * Update the overall status based on component statuses.
     */
    private void updateOverallStatus() {
        if (components.values().stream().anyMatch(c -> c.getStatus().equals("DOWN"))) {
            this.status = "DOWN";
        } else if (components.values().stream().anyMatch(c -> c.getStatus().equals("DEGRADED"))) {
            this.status = "DEGRADED";
        } else {
            this.status = "UP";
        }
    }

    /**
     * Component status information.
     */
    public static class ComponentStatus {
        private String name;
        private String status;
        private String description;
        private Long responseTimeMs;
        private Map<String, Object> details;

        public ComponentStatus() {
            this.details = new HashMap<>();
        }

        public ComponentStatus(String name, String status) {
            this.name = name;
            this.status = status;
            this.details = new HashMap<>();
        }

        public ComponentStatus(String name, String status, String description) {
            this(name, status);
            this.description = description;
        }

        // Getters and Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Long getResponseTimeMs() {
            return responseTimeMs;
        }

        public void setResponseTimeMs(Long responseTimeMs) {
            this.responseTimeMs = responseTimeMs;
        }

        public Map<String, Object> getDetails() {
            return details;
        }

        public void setDetails(Map<String, Object> details) {
            this.details = details;
        }

        public void addDetail(String key, Object value) {
            this.details.put(key, value);
        }
    }

    /**
     * System information.
     */
    public static class SystemInfo {
        private String javaVersion;
        private String osName;
        private String osVersion;
        private String architecture;
        private Integer availableProcessors;
        private Long freeMemoryMB;
        private Long maxMemoryMB;
        private Long totalMemoryMB;

        public SystemInfo() {
        }

        // Static factory method to create from system properties
        public static SystemInfo fromSystem() {
            SystemInfo info = new SystemInfo();
            info.setJavaVersion(System.getProperty("java.version", "unknown"));
            info.setOsName(System.getProperty("os.name", "unknown"));
            info.setOsVersion(System.getProperty("os.version", "unknown"));
            info.setArchitecture(System.getProperty("os.arch", "unknown"));
            info.setAvailableProcessors(Runtime.getRuntime().availableProcessors());

            Runtime runtime = Runtime.getRuntime();
            info.setFreeMemoryMB(runtime.freeMemory() / (1024 * 1024));
            info.setMaxMemoryMB(runtime.maxMemory() / (1024 * 1024));
            info.setTotalMemoryMB(runtime.totalMemory() / (1024 * 1024));

            return info;
        }

        // Getters and Setters
        public String getJavaVersion() {
            return javaVersion;
        }

        public void setJavaVersion(String javaVersion) {
            this.javaVersion = javaVersion;
        }

        public String getOsName() {
            return osName;
        }

        public void setOsName(String osName) {
            this.osName = osName;
        }

        public String getOsVersion() {
            return osVersion;
        }

        public void setOsVersion(String osVersion) {
            this.osVersion = osVersion;
        }

        public String getArchitecture() {
            return architecture;
        }

        public void setArchitecture(String architecture) {
            this.architecture = architecture;
        }

        public Integer getAvailableProcessors() {
            return availableProcessors;
        }

        public void setAvailableProcessors(Integer availableProcessors) {
            this.availableProcessors = availableProcessors;
        }

        public Long getFreeMemoryMB() {
            return freeMemoryMB;
        }

        public void setFreeMemoryMB(Long freeMemoryMB) {
            this.freeMemoryMB = freeMemoryMB;
        }

        public Long getMaxMemoryMB() {
            return maxMemoryMB;
        }

        public void setMaxMemoryMB(Long maxMemoryMB) {
            this.maxMemoryMB = maxMemoryMB;
        }

        public Long getTotalMemoryMB() {
            return totalMemoryMB;
        }

        public void setTotalMemoryMB(Long totalMemoryMB) {
            this.totalMemoryMB = totalMemoryMB;
        }
    }
}
