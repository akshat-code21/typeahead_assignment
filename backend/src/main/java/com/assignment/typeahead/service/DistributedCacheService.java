package com.assignment.typeahead.service;

import com.assignment.typeahead.cache.ConsistentHashRouter;
import com.assignment.typeahead.dto.CacheDebugResponse;
import com.assignment.typeahead.dto.SuggestionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class DistributedCacheService {

    private static final Logger log = LoggerFactory.getLogger(DistributedCacheService.class);

    @Autowired
    private ConsistentHashRouter<String> router;

    @Value("${typeahead.cache.ttl-seconds:300}")
    private int ttlSeconds;

    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);

    public List<SuggestionResponse> get(String prefix) {
        String key = normalize(prefix);
        List<SuggestionResponse> result = router.get(key);

        if (result != null) {
            hitCount.incrementAndGet();
            log.debug("Cache HIT for prefix '{}'", key);
        } else {
            missCount.incrementAndGet();
            log.debug("Cache MISS for prefix '{}'", key);
        }

        return result;
    }

    public void put(String prefix, List<SuggestionResponse> data) {
        String key = normalize(prefix);
        router.put(key, data, ttlSeconds);
        log.debug("Cache PUT for prefix '{}' ({} suggestions, TTL={}s)", key, data.size(), ttlSeconds);
    }

    public void invalidate(String prefix) {
        String key = normalize(prefix);
        router.invalidate(key);
        log.debug("Cache INVALIDATE for prefix '{}'", key);
    }

    public void invalidateAllPrefixes(String query) {
        String normalized = normalize(query);
        for (int i = 1; i <= normalized.length(); i++) {
            String prefix = normalized.substring(0, i);
            router.invalidate(prefix);
        }
        log.info("Invalidated {} prefix keys for query '{}'", normalized.length(), normalized);
    }

    public CacheDebugResponse getRoutingInfo(String prefix) {
        return router.getRoutingInfo(normalize(prefix));
    }

    public long getHitCount() {
        return hitCount.get();
    }

    public long getMissCount() {
        return missCount.get();
    }

    public long getTotalRequests() {
        return hitCount.get() + missCount.get();
    }

    public double getHitRate() {
        long total = getTotalRequests();
        return total == 0 ? 0.0 : (double) hitCount.get() / total;
    }

    private String normalize(String prefix) {
        return prefix.trim().toLowerCase();
    }
}
