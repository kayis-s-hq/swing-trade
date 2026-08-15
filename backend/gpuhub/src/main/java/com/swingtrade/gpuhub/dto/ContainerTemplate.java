package com.swingtrade.gpuhub.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Container template for GPU deployment.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContainerTemplate {

    @JsonProperty("dc_list")
    private List<String> dcList;

    @JsonProperty("gpu_name_set")
    private List<String> gpuNameSet;

    @JsonProperty("gpu_num")
    private Integer gpuNum;

    private String model;

    public List<String> getDcList() { return dcList; }
    public void setDcList(List<String> dcList) { this.dcList = dcList; }
    public List<String> getGpuNameSet() { return gpuNameSet; }
    public void setGpuNameSet(List<String> gpuNameSet) { this.gpuNameSet = gpuNameSet; }
    public Integer getGpuNum() { return gpuNum; }
    public void setGpuNum(Integer gpuNum) { this.gpuNum = gpuNum; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
}