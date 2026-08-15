package com.swingtrade.gpuhub.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Deployment info returned by list/status endpoints.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeploymentInfo {

    @JsonProperty("deployment_uuid")
    private String deploymentUuid;

    private String name;

    @JsonProperty("deployment_type")
    private String deploymentType;

    private String status;

    @JsonProperty("container_template")
    private ContainerTemplate containerTemplate;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("started_at")
    private String startedAt;

    @JsonProperty("stopped_at")
    private String stoppedAt;

    public String getDeploymentUuid() { return deploymentUuid; }
    public String getName() { return name; }
    public String getDeploymentType() { return deploymentType; }
    public String getStatus() { return status; }
    public ContainerTemplate getContainerTemplate() { return containerTemplate; }
    public String getCreatedAt() { return createdAt; }
    public String getStartedAt() { return startedAt; }
    public String getStoppedAt() { return stoppedAt; }
}