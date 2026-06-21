package com.assignment.typeahead.controller;

import com.assignment.typeahead.dto.SuggestionResponse;
import com.assignment.typeahead.service.PerformanceMetricsService;
import com.assignment.typeahead.service.SuggestionService;
import com.assignment.typeahead.service.TrendingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SuggestController {

    @Autowired
    private SuggestionService suggestionService;

    @Autowired
    private TrendingService trendingService;

    @Autowired
    private PerformanceMetricsService perfMetrics;

    @GetMapping("/suggest")
    public ResponseEntity<Map<String, Object>> suggest(@RequestParam("q") String prefix) {
        long start = System.currentTimeMillis();
        List<SuggestionResponse> suggestions = suggestionService.getSuggestions(prefix);
        long latencyMs = System.currentTimeMillis() - start;

        perfMetrics.recordLatency(latencyMs);

        return ResponseEntity.ok(Map.of(
                "prefix", prefix,
                "suggestions", suggestions,
                "latencyMs", latencyMs
        ));
    }

    @GetMapping("/trending")
    public ResponseEntity<Map<String, Object>> trending() {
        List<SuggestionResponse> trending = trendingService.getTrending();
        return ResponseEntity.ok(Map.of("trending", trending));
    }
}

