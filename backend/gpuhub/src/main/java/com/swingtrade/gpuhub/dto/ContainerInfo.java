package com.swingtrade.gpuhub.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Container info from POST /api/v1/dev/deployment/container/list.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContainerInfo {

    private String uuid;

    @JsonProperty("data_center")
    private String dataCenter;

    private String status;
    private double price;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("started_at")
    private String startedAt;

    @JsonProperty("stopped_at")
    private String stoppedAt;

    private ContainerInfo.Info info;

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getDataCenter() { return dataCenter; }
    public void setDataCenter(String dataCenter) { this.dataCenter = dataCenter; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }
    public String getStoppedAt() { return stoppedAt; }
    public void setStoppedAt(String stoppedAt) { this.stoppedAt = stoppedAt; }
    public Info getInfo() { return info; }
    public void setInfo(Info info) { this.info = info; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Info {

        @JsonProperty("ssh_command")
        private String sshCommand;

        @JsonProperty("root_password")
        private String rootPassword;

        private Map<String, String> service;

        public String getSshCommand() { return sshCommand; }
        public void setSshCommand(String sshCommand) { this.sshCommand = sshCommand; }
        public String getRootPassword() { return rootPassword; }
        public void setRootPassword(String rootPassword) { this.rootPassword = rootPassword; }
        public Map<String, String> getService() { return service; }
        public void setService(Map<String, String> service) { this.service = service; }
    }
}