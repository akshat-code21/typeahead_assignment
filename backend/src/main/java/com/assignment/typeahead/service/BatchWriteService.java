package com.assignment.typeahead.service;

import com.assignment.typeahead.model.SearchQuery;
import com.assignment.typeahead.repository.SearchQueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class BatchWriteService {

    private static final Logger log = LoggerFactory.getLogger(BatchWriteService.class);

    @Autowired
    private SearchQueryRepository searchQueryRepository;

    @Value("${typeahead.batch.flush-threshold:100}")
    private int flushThreshold;

    private final ConcurrentHashMap<String, AtomicLong> buffer = new ConcurrentHashMap<>();

    private final AtomicLong totalFlushed = new AtomicLong(0);
    private final AtomicLong totalIndividualWrites = new AtomicLong(0);
    private volatile LocalDateTime lastFlushTime = null;

    public void buffer(String query) {
        buffer.computeIfAbsent(query, k -> new AtomicLong(0)).incrementAndGet();
        totalIndividualWrites.incrementAndGet();

        if (buffer.size() >= flushThreshold) {
            log.info("Buffer threshold ({}) reached, triggering flush", flushThreshold);
            flush();
        }
    }

    @Scheduled(fixedDelayString = "${typeahead.batch.flush-interval-seconds:10}000")
    public void scheduledFlush() {
        if (!buffer.isEmpty()) {
            log.info("Scheduled flush triggered with {} entries in buffer", buffer.size());
            flush();
        }
    }

    @Transactional
    public synchronized void flush() {
        if (buffer.isEmpty()) {
            return;
        }

        Map<String, Long> snapshot = new HashMap<>();
        buffer.forEach((query, count) -> {
            long delta = count.getAndSet(0);
            if (delta > 0) {
                snapshot.put(query, delta);
            }
        });
        buffer.entrySet().removeIf(e -> e.getValue().get() == 0);

        int flushedCount = 0;
        for (Map.Entry<String, Long> entry : snapshot.entrySet()) {
            String query = entry.getKey();
            long delta = entry.getValue();

            SearchQuery existing = searchQueryRepository.findByQuery(query);
            if (existing != null) {
                existing.setCount(existing.getCount() + delta);
                searchQueryRepository.save(existing);
            } else {
                SearchQuery newQuery = new SearchQuery();
                newQuery.setQuery(query);
                newQuery.setCount(delta);
                searchQueryRepository.save(newQuery);
            }
            flushedCount++;
        }

        totalFlushed.addAndGet(flushedCount);
        lastFlushTime = LocalDateTime.now();
        log.info("Flushed {} entries. Total individual writes reduced: {} → {}",
                flushedCount, totalIndividualWrites.get(), totalFlushed.get());
    }


    public int getBufferSize() {
        return buffer.size();
    }

    public long getTotalFlushed() {
        return totalFlushed.get();
    }

    public long getTotalIndividualWrites() {
        return totalIndividualWrites.get();
    }

    public long getWritesReduced() {
        return totalIndividualWrites.get() - totalFlushed.get();
    }

    public LocalDateTime getLastFlushTime() {
        return lastFlushTime;
    }
}
