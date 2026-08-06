package com.swingtrade.api.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic paginated response DTO.
 * Wraps a list of items with pagination metadata.
 */
public class PaginatedResponse<T> {

    private List<T> content;
    private Integer pageNumber;
    private Integer pageSize;
    private Long totalElements;
    private Integer totalPages;
    private Boolean first;
    private Boolean last;

    public PaginatedResponse() {
        this.content = new ArrayList<>();
    }

    public PaginatedResponse(List<T> content, Integer pageNumber, Integer pageSize, Long totalElements) {
        this();
        this.content = content;
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        this.totalElements = totalElements;
        this.totalPages = totalElements != null && pageSize != 0 ? (int) ((totalElements + pageSize - 1) / pageSize) : 0;
        this.first = pageNumber == 0 || pageNumber == null;
        this.last = totalPages != null && pageNumber >= totalPages - 1;
    }

    // Getters and Setters
    public List<T> getContent() {
        return content;
    }

    public void setContent(List<T> content) {
        this.content = content;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(Long totalElements) {
        this.totalElements = totalElements;
    }

    public Integer getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(Integer totalPages) {
        this.totalPages = totalPages;
    }

    public Boolean getFirst() {
        return first;
    }

    public void setFirst(Boolean first) {
        this.first = first;
    }

    public Boolean getLast() {
        return last;
    }

    public void setLast(Boolean last) {
        this.last = last;
    }

    /**
     * Check if pagination is available.
     */
    public boolean hasContent() {
        return content != null && !content.isEmpty();
    }
}
