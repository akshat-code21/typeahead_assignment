package com.assignment.typeahead.controller;

import com.assignment.typeahead.dto.CacheDebugResponse;
import com.assignment.typeahead.service.BatchWriteService;
import com.assignment.typeahead.service.ConsistentHashRing;
import com.assignment.typeahead.service.DistributedCacheService;
import com.assignment.typeahead.service.PerformanceMetricsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class CacheDebugController {

    @Autowired
    private DistributedCacheService cacheService;

    @Autowired
    private BatchWriteService batchWriteService;

    @Autowired
    private PerformanceMetricsService perfMetrics;

    @Autowired
    private ConsistentHashRing hashRing;

    @GetMapping("/cache/debug")
    public ResponseEntity<CacheDebugResponse> cacheDebug(@RequestParam("prefix") String prefix) {
        long start = System.currentTimeMillis();
        CacheDebugResponse info = cacheService.getRoutingInfo(prefix);
        long latencyMs = System.currentTimeMillis() - start;
        info.setLatencyMs(latencyMs);
        return ResponseEntity.ok(info);
    }

    @GetMapping("/batch/stats")
    public ResponseEntity<Map<String, Object>> batchStats() {
        return ResponseEntity.ok(Map.of(
                "bufferSize", batchWriteService.getBufferSize(),
                "totalFlushed", batchWriteService.getTotalFlushed(),
                "totalWritesReduced", batchWriteService.getWritesReduced(),
                "totalIndividualWrites", batchWriteService.getTotalIndividualWrites(),
                "lastFlushTime", String.valueOf(batchWriteService.getLastFlushTime())
        ));
    }

    @GetMapping("/cache/stats")
    public ResponseEntity<Map<String, Object>> cacheStats() {
        return ResponseEntity.ok(Map.of(
                "hitCount", cacheService.getHitCount(),
                "missCount", cacheService.getMissCount(),
                "totalRequests", cacheService.getTotalRequests(),
                "hitRate", String.format("%.2f%%", cacheService.getHitRate() * 100)
        ));
    }

    @GetMapping("/perf/stats")
    public ResponseEntity<Map<String, Object>> perfStats() {
        return ResponseEntity.ok(perfMetrics.getSnapshot());
    }

    @GetMapping("/ring/info")
    public ResponseEntity<Map<String, Object>> ringInfo() {
        return ResponseEntity.ok(hashRing.getRingInfo());
    }
}

