package com.swingtrade.gpuhub.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response body for successful POST /api/v1/dev/deployment.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateDeploymentResponse {

    @JsonProperty("deployment_uuid")
    private String deploymentUuid;

    public String getDeploymentUuid() { return deploymentUuid; }
}