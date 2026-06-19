package com.assignment.typeahead.dto;

public class CacheDebugResponse {

    private String prefix;
    private String node;
    private String status;
    private String cacheKey;
    private Long ttlSeconds;

    public CacheDebugResponse(String prefix, String node, String status, String cacheKey, Long ttlSeconds) {
        this.prefix = prefix;
        this.node = node;
        this.status = status;
        this.cacheKey = cacheKey;
        this.ttlSeconds = ttlSeconds;
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

    public Long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(Long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }
}
