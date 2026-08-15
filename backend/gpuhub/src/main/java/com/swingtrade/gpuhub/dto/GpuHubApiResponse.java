package com.swingtrade.gpuhub.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Generic wrapper for all GPUHUB API responses.
 * Format: { code: "Success"|"Error", data: {...}, msg: "", request_id: "..." }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GpuHubApiResponse<T> {

    private String code;
    private T data;
    private String msg;
    @JsonProperty("request_id")
    private String requestId;

    public String getCode() { return code; }
    public T getData() { return data; }
    public String getMsg() { return msg; }
    public String getRequestId() { return requestId; }

    public boolean isSuccess() {
        return "Success".equals(code);
    }
}