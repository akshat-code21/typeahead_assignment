package com.assignment.typeahead.dto;

import java.util.List;

public class SearchResponse {

    private String prefix;
    private List<SuggestionResponse> suggestions;

    public SearchResponse(String prefix, List<SuggestionResponse> suggestions) {
        this.prefix = prefix;
        this.suggestions = suggestions;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public List<SuggestionResponse> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<SuggestionResponse> suggestions) {
        this.suggestions = suggestions;
    }
}

