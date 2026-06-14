package com.assignment.typeahead.dto;

import java.util.List;

public class SearchResponse {

    private String prefix;
    private List<Suggestion> suggestions;

    public SearchResponse(String prefix, List<Suggestion> suggestions) {
        this.prefix = prefix;
        this.suggestions = suggestions;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public List<Suggestion> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<Suggestion> suggestions) {
        this.suggestions = suggestions;
    }
}

