package com.assignment.typeahead.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    @Autowired
    private BatchWriteService batchWriteService;

    @Autowired
    private DistributedCacheService cacheService;

    @Autowired
    private TrendingService trendingService;

    public String submitSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            return "Empty query";
        }

        String normalized = query.trim().toLowerCase();

        batchWriteService.buffer(normalized);
        cacheService.invalidateAllPrefixes(normalized);
        trendingService.recordSearchEvent(normalized);

        log.info("Search submitted: '{}'", normalized);
        return "Searched";
    }
}
