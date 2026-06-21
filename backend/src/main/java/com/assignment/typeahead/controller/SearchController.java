package com.assignment.typeahead.controller;

import com.assignment.typeahead.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class SearchController {

    @Autowired
    private SearchService searchService;

    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestBody Map<String, String> body) {
        long start = System.currentTimeMillis();
        String query = body.getOrDefault("query", "");
        String message = searchService.submitSearch(query);
        long latencyMs = System.currentTimeMillis() - start;
        return ResponseEntity.ok(Map.of(
                "message", message,
                "query", query,
                "latencyMs", latencyMs
        ));
    }
}
