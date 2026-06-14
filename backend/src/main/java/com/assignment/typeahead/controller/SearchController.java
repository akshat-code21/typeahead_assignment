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
    public ResponseEntity<Map<String, String>> search(@RequestBody Map<String, String> body) {
        String query = body.getOrDefault("query", "");
        String message = searchService.submitSearch(query);
        return ResponseEntity.ok(Map.of(
                "message", message,
                "query", query
        ));
    }
}
