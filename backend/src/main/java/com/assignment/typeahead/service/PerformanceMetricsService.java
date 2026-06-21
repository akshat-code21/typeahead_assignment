package com.assignment.typeahead.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects suggest-API latency samples and database I/O counters.
 * Latency percentiles (p50, p95, p99) are computed from the most recent
 * MAX_SAMPLES observations so the numbers reflect current behaviour.
 */
@Service
public class PerformanceMetricsService {

    private static final int MAX_SAMPLES = 1000;

    /* --- latency ring buffer ------------------------------------------------ */
    private final long[] latencySamples = new long[MAX_SAMPLES];
    private int sampleIndex = 0;
    private int sampleCount = 0;
    private final Object sampleLock = new Object();

    /* --- database I/O counters ---------------------------------------------- */
    private final AtomicLong dbReadCount  = new AtomicLong(0);
    private final AtomicLong dbWriteCount = new AtomicLong(0);

    /* ======================================================================== */
    /*  Recording methods                                                       */
    /* ======================================================================== */

    /** Record one suggest-API latency sample (milliseconds). */
    public void recordLatency(long ms) {
        synchronized (sampleLock) {
            latencySamples[sampleIndex] = ms;
            sampleIndex = (sampleIndex + 1) % MAX_SAMPLES;
            if (sampleCount < MAX_SAMPLES) {
                sampleCount++;
            }
        }
    }

    /** Increment the database-read counter (called on cache MISS → DB query). */
    public void incrementDbReads() {
        dbReadCount.incrementAndGet();
    }

    /** Increment the database-write counter by {@code n} (called during batch flush). */
    public void incrementDbWrites(long n) {
        dbWriteCount.addAndGet(n);
    }

    /* ======================================================================== */
    /*  Query methods                                                           */
    /* ======================================================================== */

    public long getDbReadCount()  { return dbReadCount.get();  }
    public long getDbWriteCount() { return dbWriteCount.get(); }

    /** Total number of latency samples currently stored. */
    public int getSampleCount() {
        synchronized (sampleLock) {
            return sampleCount;
        }
    }

    /** Return the requested percentile (0-100) from the current buffer, or -1 if no data. */
    public long getPercentile(double percentile) {
        synchronized (sampleLock) {
            if (sampleCount == 0) return -1;
            long[] copy = new long[sampleCount];
            System.arraycopy(latencySamples, 0, copy, 0, sampleCount);
            Arrays.sort(copy);
            int idx = (int) Math.ceil(percentile / 100.0 * sampleCount) - 1;
            idx = Math.max(0, Math.min(idx, sampleCount - 1));
            return copy[idx];
        }
    }

    /** Convenience: returns {p50, p95, p99} as a map. */
    public Map<String, Long> getLatencyPercentiles() {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("p50", getPercentile(50));
        m.put("p95", getPercentile(95));
        m.put("p99", getPercentile(99));
        return m;
    }

    /** Full snapshot returned by the /api/perf/stats endpoint. */
    public Map<String, Object> getSnapshot() {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("latencyPercentiles", getLatencyPercentiles());
        snap.put("sampleCount", getSampleCount());
        snap.put("dbReadCount", getDbReadCount());
        snap.put("dbWriteCount", getDbWriteCount());
        return snap;
    }
}
