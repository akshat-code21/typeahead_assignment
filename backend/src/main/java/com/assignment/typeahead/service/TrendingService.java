package com.assignment.typeahead.service;

import com.assignment.typeahead.dto.SuggestionResponse;
import com.assignment.typeahead.model.SearchQuery;
import com.assignment.typeahead.repository.SearchQueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

@Service
public class TrendingService {

    private static final Logger log = LoggerFactory.getLogger(TrendingService.class);
    private static final int MAX_TRENDING = 10;

    @Autowired
    private SearchQueryRepository searchQueryRepository;

    @Value("${typeahead.trending.window-minutes:60}")
    private int windowMinutes;

    @Value("${typeahead.trending.decay-factor:0.95}")
    private double decayFactor;

    private final ConcurrentLinkedDeque<SearchEvent> recentEvents = new ConcurrentLinkedDeque<>();

    public void recordSearchEvent(String query) {
        recentEvents.addLast(new SearchEvent(query, Instant.now()));
        evictOldEvents();
    }

    public List<SuggestionResponse> getTrending() {
        evictOldEvents();

        Instant now = Instant.now();

        Map<String, Double> recentScores = new HashMap<>();
        for (SearchEvent event : recentEvents) {
            double ageMinutes = (now.toEpochMilli() - event.timestamp.toEpochMilli()) / 60_000.0;
            double decayedWeight = Math.pow(decayFactor, ageMinutes);
            recentScores.merge(event.query, decayedWeight, Double::sum);
        }

        Set<String> recentQueries = recentScores.keySet();
        Map<String, Long> allTimeCounts = new HashMap<>();
        for (String query : recentQueries) {
            SearchQuery sq = searchQueryRepository.findByQuery(query);
            if (sq != null) {
                allTimeCounts.put(query, sq.getCount());
            }
        }

        double boostMultiplier = 100.0;
        Map<String, Double> combinedScores = new HashMap<>();
        for (String query : recentQueries) {
            long allTime = allTimeCounts.getOrDefault(query, 0L);
            double recent = recentScores.getOrDefault(query, 0.0);
            combinedScores.put(query, allTime + (recent * boostMultiplier));
        }

        List<SuggestionResponse> trending = combinedScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(MAX_TRENDING)
                .map(e -> new SuggestionResponse(e.getKey(), Math.round(e.getValue())))
                .collect(Collectors.toList());

        if (trending.size() < MAX_TRENDING) {
            Set<String> alreadyIncluded = trending.stream()
                    .map(SuggestionResponse::getQuery)
                    .collect(Collectors.toSet());

            List<SearchQuery> topAllTime = searchQueryRepository.findTop10ByOrderByCountDesc().stream()
                    .filter(sq -> !alreadyIncluded.contains(sq.getQuery()))
                    .limit(MAX_TRENDING - trending.size())
                    .collect(Collectors.toList());

            for (SearchQuery sq : topAllTime) {
                trending.add(new SuggestionResponse(sq.getQuery(), sq.getCount()));
            }
        }

        log.debug("Returning {} trending queries ({} from recent events)", trending.size(), recentScores.size());
        return trending;
    }

    public double getScore(String query, long allTimeCount) {
        evictOldEvents();
        Instant now = Instant.now();
        double recentWeight = 0.0;
        for (SearchEvent event : recentEvents) {
            if (event.query.equals(query)) {
                double ageMinutes = (now.toEpochMilli() - event.timestamp.toEpochMilli()) / 60_000.0;
                recentWeight += Math.pow(decayFactor, ageMinutes);
            }
        }
        double boostMultiplier = 100.0;
        return allTimeCount + (recentWeight * boostMultiplier);
    }

    private void evictOldEvents() {
        Instant cutoff = Instant.now().minusSeconds(windowMinutes * 60L);
        while (!recentEvents.isEmpty() && recentEvents.peekFirst().timestamp.isBefore(cutoff)) {
            recentEvents.pollFirst();
        }
    }

    private static class SearchEvent {
        final String query;
        final Instant timestamp;

        SearchEvent(String query, Instant timestamp) {
            this.query = query;
            this.timestamp = timestamp;
        }
    }
}
