package com.assignment.typeahead.loader;

import com.assignment.typeahead.model.SearchQuery;
import com.assignment.typeahead.repository.SearchQueryRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class DatasetLoader {

    @Value("${typeahead.dataset.path:classpath:data/queries.csv}")
    private Resource datasetResource;

    @Autowired
    private SearchQueryRepository searchQueryRepository;

    @PostConstruct
    @Transactional
    public void loadData() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(datasetResource.getInputStream()))) {
            List<SearchQuery> queries = reader.lines()
                    .skip(1)
                    .map(this::parseLine)
                    .collect(Collectors.toList());

            searchQueryRepository.saveAll(queries);
            System.out.println("Loaded " + queries.size() + " queries from dataset.");
        } catch (Exception e) {
            System.err.println("Failed to load dataset: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private SearchQuery parseLine(String line) {
        String[] parts = line.split(",");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid line format: " + line);
        }

        String queryText = parts[0].trim();
        long count = Long.parseLong(parts[1].trim());

        SearchQuery searchQuery = new SearchQuery();
        searchQuery.setQuery(queryText);
        searchQuery.setCount(count);
        return searchQuery;
    }
}
