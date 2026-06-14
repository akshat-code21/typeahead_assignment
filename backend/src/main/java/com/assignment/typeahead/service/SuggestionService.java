package com.assignment.typeahead.service;

import com.assignment.typeahead.dto.SuggestionResponse;
import com.assignment.typeahead.model.SearchQuery;
import com.assignment.typeahead.repository.SearchQueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SuggestionService {

    private static final Logger log = LoggerFactory.getLogger(SuggestionService.class);
    private static final int MAX_SUGGESTIONS = 10;

    @Autowired
    private SearchQueryRepository searchQueryRepository;

    @Autowired
    private DistributedCacheService cacheService;

    public List<SuggestionResponse> getSuggestions(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String normalized = prefix.trim().toLowerCase();

        List<SuggestionResponse> cached = cacheService.get(normalized);
        if (cached != null) {
            return cached;
        }

        List<SearchQuery> results = searchQueryRepository
                .findByQueryStartingWithIgnoreCase(normalized);

        List<SuggestionResponse> suggestions = results.stream()
                .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
                .limit(MAX_SUGGESTIONS)
                .map(sq -> new SuggestionResponse(sq.getQuery(), sq.getCount()))
                .collect(Collectors.toList());

        cacheService.put(normalized, suggestions);

        log.debug("Fetched {} suggestions for prefix '{}' from DB", suggestions.size(), normalized);
        return suggestions;
    }
}
