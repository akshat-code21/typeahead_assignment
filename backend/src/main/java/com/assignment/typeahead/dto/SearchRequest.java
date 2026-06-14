package com.assignment.typeahead.dto;

public class SearchRequest {

    private String prefix;
    private int limit;

    public SearchRequest(String prefix, int limit) {
        this.prefix = prefix;
        this.limit = limit;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }
}
