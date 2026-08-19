package com.swingtrade.gpuhub.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Container template for GPUHUB elastic deployment.
 * See https://docs.gpuhub.com/api-reference/api-elastic-deployment
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContainerTemplate {

    @JsonProperty("dc_list")
    private List<String> dcList;

    @JsonProperty("gpu_name_set")
    private List<String> gpuNameSet;

    @JsonProperty("gpu_num")
    private Integer gpuNum;

    @JsonProperty("cuda_v_from")
    private Integer cudaVFrom;

    @JsonProperty("cuda_v_to")
    private Integer cudaVTo;

    @JsonProperty("cpu_num_from")
    private Integer cpuNumFrom;

    @JsonProperty("cpu_num_to")
    private Integer cpuNumTo;

    @JsonProperty("memory_size_from")
    private Integer memorySizeFrom;

    @JsonProperty("memory_size_to")
    private Integer memorySizeTo;

    @JsonProperty("price_from")
    private Integer priceFrom;

    @JsonProperty("price_to")
    private Integer priceTo;

    @JsonProperty("image_uuid")
    private String imageUuid;

    @JsonProperty("cmd")
    private String cmd;

    public List<String> getDcList() { return dcList; }
    public void setDcList(List<String> dcList) { this.dcList = dcList; }
    public List<String> getGpuNameSet() { return gpuNameSet; }
    public void setGpuNameSet(List<String> gpuNameSet) { this.gpuNameSet = gpuNameSet; }
    public Integer getGpuNum() { return gpuNum; }
    public void setGpuNum(Integer gpuNum) { this.gpuNum = gpuNum; }
    public Integer getCudaVFrom() { return cudaVFrom; }
    public void setCudaVFrom(Integer cudaVFrom) { this.cudaVFrom = cudaVFrom; }
    public Integer getCudaVTo() { return cudaVTo; }
    public void setCudaVTo(Integer cudaVTo) { this.cudaVTo = cudaVTo; }
    public Integer getCpuNumFrom() { return cpuNumFrom; }
    public void setCpuNumFrom(Integer cpuNumFrom) { this.cpuNumFrom = cpuNumFrom; }
    public Integer getCpuNumTo() { return cpuNumTo; }
    public void setCpuNumTo(Integer cpuNumTo) { this.cpuNumTo = cpuNumTo; }
    public Integer getMemorySizeFrom() { return memorySizeFrom; }
    public void setMemorySizeFrom(Integer memorySizeFrom) { this.memorySizeFrom = memorySizeFrom; }
    public Integer getMemorySizeTo() { return memorySizeTo; }
    public void setMemorySizeTo(Integer memorySizeTo) { this.memorySizeTo = memorySizeTo; }
    public Integer getPriceFrom() { return priceFrom; }
    public void setPriceFrom(Integer priceFrom) { this.priceFrom = priceFrom; }
    public Integer getPriceTo() { return priceTo; }
    public void setPriceTo(Integer priceTo) { this.priceTo = priceTo; }
    public String getImageUuid() { return imageUuid; }
    public void setImageUuid(String imageUuid) { this.imageUuid = imageUuid; }
    public String getCmd() { return cmd; }
    public void setCmd(String cmd) { this.cmd = cmd; }

    /**
     * Create a default container template with sensible defaults.
     * All numeric ranges are set to broad values to maximize availability.
     */
    public static ContainerTemplate defaultTemplate() {
        ContainerTemplate t = new ContainerTemplate();
        t.setGpuNum(1);
        t.setCudaVFrom(118);
        t.setCudaVTo(128);
        t.setCpuNumFrom(4);
        t.setCpuNumTo(64);
        t.setMemorySizeFrom(16);
        t.setMemorySizeTo(256);
        t.setPriceFrom(100);
        t.setPriceTo(9000);
        t.setCmd("python main.py");
        return t;
    }
}