package com.assignment.typeahead.service;

import com.assignment.typeahead.dto.SuggestionResponse;
import com.assignment.typeahead.model.SearchQuery;
import com.assignment.typeahead.repository.SearchQueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SuggestionService {

    private static final Logger log = LoggerFactory.getLogger(SuggestionService.class);
    private static final int MAX_SUGGESTIONS = 10;
    private static final int MAX_RECENT_CANDIDATES = 50;

    @Value("${typeahead.suggestions.candidate-limit:100}")
    private int candidateLimit;

    @Autowired
    private SearchQueryRepository searchQueryRepository;

    @Autowired
    private DistributedCacheService cacheService;

    @Autowired
    private PerformanceMetricsService perfMetrics;

    @Autowired
    private TrendingService trendingService;

    public List<SuggestionResponse> getSuggestions(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String normalized = prefix.trim().toLowerCase();

        List<SuggestionResponse> cached = cacheService.get(normalized);
        if (cached != null) {
            return cached;
        }

        // Cache MISS → querying DB (counts as a database read)
        perfMetrics.incrementDbReads();

        int dbCandidateLimit = Math.max(MAX_SUGGESTIONS, candidateLimit);
        List<SearchQuery> results = searchQueryRepository
                .findByQueryStartingWithIgnoreCaseOrderByCountDesc(
                        normalized, PageRequest.of(0, dbCandidateLimit));

        Map<String, Long> candidates = new LinkedHashMap<>();
        for (SearchQuery result : results) {
            candidates.put(result.getQuery(), result.getCount());
        }

        for (String recentQuery : trendingService.getRecentQueriesStartingWith(normalized, MAX_RECENT_CANDIDATES)) {
            candidates.computeIfAbsent(recentQuery, query -> {
                SearchQuery persisted = searchQueryRepository.findByQuery(query);
                return persisted == null ? 0L : persisted.getCount();
            });
        }

        List<SuggestionResponse> suggestions = candidates.entrySet().stream()
                .map(entry -> new SuggestionResponse(
                        entry.getKey(),
                        Math.round(trendingService.getScore(entry.getKey(), entry.getValue()))))
                .sorted((a, b) -> Long.compare(b.getScore(), a.getScore()))
                .limit(MAX_SUGGESTIONS)
                .collect(Collectors.toList());

        cacheService.put(normalized, suggestions);

        log.debug("Fetched {} DB candidates and returned {} suggestions for prefix '{}'",
                results.size(), suggestions.size(), normalized);
        return suggestions;
    }
}
