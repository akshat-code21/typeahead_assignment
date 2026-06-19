package com.assignment.typeahead.service;

import com.assignment.typeahead.dto.CacheDebugResponse;
import com.assignment.typeahead.dto.SuggestionResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class DistributedCacheService {

    private static final Logger log = LoggerFactory.getLogger(DistributedCacheService.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${typeahead.cache.ttl-seconds:300}")
    private int ttlSeconds;

    @Value("${typeahead.cache.key-prefix:typeahead:suggest:}")
    private String keyPrefix;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);

    public List<SuggestionResponse> get(String prefix) {
        String key = buildKey(normalize(prefix));
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                hitCount.incrementAndGet();
                log.debug("Cache HIT for prefix '{}'", prefix);
                // Redis returns a LinkedHashMap list; convert back to SuggestionResponse list
                List<SuggestionResponse> result = objectMapper.convertValue(
                        cached, new TypeReference<List<SuggestionResponse>>() {});
                return result;
            }
        } catch (Exception e) {
            log.warn("Redis GET failed for key '{}': {}", key, e.getMessage());
        }

        missCount.incrementAndGet();
        log.debug("Cache MISS for prefix '{}'", prefix);
        return null;
    }

    public void put(String prefix, List<SuggestionResponse> data) {
        String key = buildKey(normalize(prefix));
        try {
            redisTemplate.opsForValue().set(key, data, ttlSeconds, TimeUnit.SECONDS);
            log.debug("Cache PUT for prefix '{}' ({} suggestions, TTL={}s)", prefix, data.size(), ttlSeconds);
        } catch (Exception e) {
            log.warn("Redis SET failed for key '{}': {}", key, e.getMessage());
        }
    }

    public void invalidate(String prefix) {
        String key = buildKey(normalize(prefix));
        try {
            redisTemplate.delete(key);
            log.debug("Cache INVALIDATE for prefix '{}'", prefix);
        } catch (Exception e) {
            log.warn("Redis DELETE failed for key '{}': {}", key, e.getMessage());
        }
    }

    public void invalidateAllPrefixes(String query) {
        String normalized = normalize(query);
        int count = 0;
        for (int i = 1; i <= normalized.length(); i++) {
            String prefix = normalized.substring(0, i);
            String key = buildKey(prefix);
            try {
                redisTemplate.delete(key);
                count++;
            } catch (Exception e) {
                log.warn("Redis DELETE failed for key '{}': {}", key, e.getMessage());
            }
        }
        log.info("Invalidated {} prefix keys for query '{}'", count, normalized);
    }

    public CacheDebugResponse getRoutingInfo(String prefix) {
        String normalized = normalize(prefix);
        String key = buildKey(normalized);
        boolean exists = false;
        Long ttl = null;
        try {
            exists = Boolean.TRUE.equals(redisTemplate.hasKey(key));
            if (exists) {
                ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("Redis EXISTS/TTL check failed for key '{}': {}", key, e.getMessage());
        }
        String status = exists ? "HIT" : "MISS";
        return new CacheDebugResponse(prefix, "redis", status, key, ttl);
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

    private String buildKey(String normalizedPrefix) {
        return keyPrefix + normalizedPrefix;
    }
}
