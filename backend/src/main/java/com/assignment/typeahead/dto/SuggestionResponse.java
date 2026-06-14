package com.assignment.typeahead.dto;

public class SuggestionResponse {

    private String query;
    private long score;

    public SuggestionResponse(String query, long score) {
        this.query = query;
        this.score = score;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public long getScore() {
        return score;
    }

    public void setScore(long score) {
        this.score = score;
    }
}

