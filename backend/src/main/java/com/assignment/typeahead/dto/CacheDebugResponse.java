package com.assignment.typeahead.dto;

public class CacheDebugResponse {

    private String prefix;
    private String node;
    private String status;
    private String cacheKey;

    public CacheDebugResponse(String prefix, String node, String status, String cacheKey) {
        this.prefix = prefix;
        this.node = node;
        this.status = status;
        this.cacheKey = cacheKey;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getNode() {
        return node;
    }

    public void setNode(String node) {
        this.node = node;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCacheKey() {
        return cacheKey;
    }

    public void setCacheKey(String cacheKey) {
        this.cacheKey = cacheKey;
    }
}
