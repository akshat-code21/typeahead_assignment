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

    @Autowired
    private ConsistentHashRing hashRing;

    @Value("${typeahead.cache.ttl-seconds:300}")
    private int ttlSeconds;

    @Value("${typeahead.cache.key-prefix:typeahead:suggest:}")
    private String keyPrefix;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);

    public List<SuggestionResponse> get(String prefix) {
        String baseKey = buildBaseKey(normalize(prefix));
        String routedKey = hashRing.routedKey(baseKey);
        try {
            Object cached = redisTemplate.opsForValue().get(routedKey);
            if (cached != null) {
                hitCount.incrementAndGet();
                log.debug("Cache HIT for prefix '{}' on node '{}'", prefix, hashRing.getNode(baseKey));
                List<SuggestionResponse> result = objectMapper.convertValue(
                        cached, new TypeReference<List<SuggestionResponse>>() {});
                return result;
            }
        } catch (Exception e) {
            log.warn("Redis GET failed for key '{}'", routedKey, e);
        }

        missCount.incrementAndGet();
        log.debug("Cache MISS for prefix '{}' on node '{}'", prefix, hashRing.getNode(baseKey));
        return null;
    }

    public void put(String prefix, List<SuggestionResponse> data) {
        String baseKey = buildBaseKey(normalize(prefix));
        String routedKey = hashRing.routedKey(baseKey);
        try {
            redisTemplate.opsForValue().set(routedKey, data, ttlSeconds, TimeUnit.SECONDS);
            log.debug("Cache PUT for prefix '{}' on node '{}' ({} suggestions, TTL={}s)",
                    prefix, hashRing.getNode(baseKey), data.size(), ttlSeconds);
        } catch (Exception e) {
            log.warn("Redis SET failed for key '{}'", routedKey, e);
        }
    }

    public void invalidate(String prefix) {
        String baseKey = buildBaseKey(normalize(prefix));
        String routedKey = hashRing.routedKey(baseKey);
        try {
            redisTemplate.delete(routedKey);
            log.debug("Cache INVALIDATE for prefix '{}' on node '{}'", prefix, hashRing.getNode(baseKey));
        } catch (Exception e) {
            log.warn("Redis DELETE failed for key '{}'", routedKey, e);
        }
    }

    public void invalidateAllPrefixes(String query) {
        String normalized = normalize(query);
        int count = 0;
        for (int i = 1; i <= normalized.length(); i++) {
            String prefix = normalized.substring(0, i);
            String baseKey = buildBaseKey(prefix);
            String routedKey = hashRing.routedKey(baseKey);
            try {
                redisTemplate.delete(routedKey);
                count++;
            } catch (Exception e) {
                log.warn("Redis DELETE failed for key '{}'", routedKey, e);
            }
        }
        log.info("Invalidated {} prefix keys for query '{}'", count, normalized);
    }

    public CacheDebugResponse getRoutingInfo(String prefix) {
        String normalized = normalize(prefix);
        String baseKey = buildBaseKey(normalized);
        String node = hashRing.getNode(baseKey);
        String routedKey = hashRing.routedKey(baseKey);
        boolean exists = false;
        Long ttl = null;
        try {
            exists = Boolean.TRUE.equals(redisTemplate.hasKey(routedKey));
            if (exists) {
                ttl = redisTemplate.getExpire(routedKey, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("Redis EXISTS/TTL check failed for key '{}'", routedKey, e);
        }
        String status = exists ? "HIT" : "MISS";
        return new CacheDebugResponse(prefix, node, status, routedKey, ttl);
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

    private String buildBaseKey(String normalizedPrefix) {
        return keyPrefix + normalizedPrefix;
    }
}

