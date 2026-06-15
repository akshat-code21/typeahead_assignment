package com.assignment.typeahead.loader;

import com.assignment.typeahead.model.SearchQuery;
import com.assignment.typeahead.repository.SearchQueryRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@Component
public class DatasetLoader {

    private static final Logger log = LoggerFactory.getLogger(DatasetLoader.class);
    private static final int BATCH_SIZE = 5000;

    @Value("${typeahead.dataset.path:classpath:data/queries.csv}")
    private Resource datasetResource;

    @Autowired
    private SearchQueryRepository searchQueryRepository;

    @PostConstruct
    @Transactional
    public void loadData() {
        // Skip if data already loaded
        long existingCount = searchQueryRepository.count();
        if (existingCount > 0) {
            log.info("Dataset already loaded ({} rows). Skipping.", existingCount);
            return;
        }

        log.info("Loading dataset from {}...", datasetResource.getDescription());

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(datasetResource.getInputStream()))) {

            String headerLine = reader.readLine(); // skip header
            if (headerLine == null) {
                log.warn("Dataset file is empty");
                return;
            }

            List<SearchQuery> batch = new ArrayList<>(BATCH_SIZE);
            String line;
            int totalLoaded = 0;
            int skipped = 0;

            while ((line = reader.readLine()) != null) {
                try {
                    SearchQuery sq = parseLine(line);
                    if (sq != null) {
                        batch.add(sq);
                    }
                } catch (Exception e) {
                    skipped++;
                    continue;
                }

                if (batch.size() >= BATCH_SIZE) {
                    searchQueryRepository.saveAll(batch);
                    totalLoaded += batch.size();
                    batch.clear();
                    if (totalLoaded % 50000 == 0) {
                        log.info("  Loaded {} rows...", totalLoaded);
                    }
                }
            }

            // Flush remaining
            if (!batch.isEmpty()) {
                searchQueryRepository.saveAll(batch);
                totalLoaded += batch.size();
            }

            log.info("Dataset loading complete: {} rows loaded, {} skipped", totalLoaded, skipped);

        } catch (Exception e) {
            log.error("Failed to load dataset: {}", e.getMessage(), e);
        }
    }

    private SearchQuery parseLine(String line) {
        // Handle \r\n line endings
        line = line.replace("\r", "");
        String[] parts = line.split(",");
        if (parts.length < 2) {
            return null;
        }

        String queryText = parts[0].trim();
        if (queryText.isEmpty()) {
            return null;
        }

        long count = Long.parseLong(parts[1].trim());

        SearchQuery searchQuery = new SearchQuery();
        searchQuery.setQuery(queryText);
        searchQuery.setCount(count);
        return searchQuery;
    }
}
