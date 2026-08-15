package com.swingtrade.gpuhub.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for POST /api/v1/dev/deployment.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateDeploymentRequest {

    private String name;
    @JsonProperty("deployment_type")
    private String deploymentType; // "ReplicaSet"
    @JsonProperty("replica_num")
    private Integer replicaNum;
    @JsonProperty("reuse_container")
    private Boolean reuseContainer;
    @JsonProperty("reuse_container_scope")
    private String reuseContainerScope;
    @JsonProperty("container_template")
    private ContainerTemplate containerTemplate;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDeploymentType() { return deploymentType; }
    public void setDeploymentType(String deploymentType) { this.deploymentType = deploymentType; }
    public Integer getReplicaNum() { return replicaNum; }
    public void setReplicaNum(Integer replicaNum) { this.replicaNum = replicaNum; }
    public Boolean getReuseContainer() { return reuseContainer; }
    public void setReuseContainer(Boolean reuseContainer) { this.reuseContainer = reuseContainer; }
    public String getReuseContainerScope() { return reuseContainerScope; }
    public void setReuseContainerScope(String reuseContainerScope) { this.reuseContainerScope = reuseContainerScope; }
    public ContainerTemplate getContainerTemplate() { return containerTemplate; }
    public void setContainerTemplate(ContainerTemplate containerTemplate) { this.containerTemplate = containerTemplate; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final CreateDeploymentRequest req = new CreateDeploymentRequest();

        public Builder name(String name) { req.name = name; return this; }
        public Builder deploymentType(String type) { req.deploymentType = type; return this; }
        public Builder replicaNum(int n) { req.replicaNum = n; return this; }
        public Builder reuseContainer(boolean reuse) { req.reuseContainer = reuse; return this; }
        public Builder reuseContainerScope(String scope) { req.reuseContainerScope = scope; return this; }
        public Builder containerTemplate(ContainerTemplate template) { req.containerTemplate = template; return this; }
        public CreateDeploymentRequest build() { return req; }
    }
}