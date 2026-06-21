# High-Level Design (HLD) Project Report: Typeahead Search Suggestion System

This report covers the architecture, dataset loading, APIs, design trade-offs, and performance characteristics of the implemented search typeahead system.

---

## 1. Architecture Diagram & Explanation

### Architecture Diagram

The system is a full-stack application with a React frontend, a Spring Boot backend, PostgreSQL as the source of truth, and Redis as the low-latency cache. For local development, Docker Compose starts PostgreSQL 16, Redis 7, and the backend. In production, the same backend can connect to Neon PostgreSQL and Upstash Redis through environment variables.

```mermaid
graph TD
    Client["React Frontend"] -->|"GET /api/suggest?q=prefix"| Suggest["SuggestController"]
    Client -->|"POST /api/search"| Search["SearchController"]
    Client -->|"GET /api/trending"| TrendApi["SuggestController / trending"]
    Client -->|"GET /api/cache/debug"| Debug["CacheDebugController"]

    subgraph Backend["Spring Boot Backend"]
        Suggest --> SuggestionService["SuggestionService"]
        Search --> SearchService["SearchService"]
        TrendApi --> TrendingService["TrendingService"]
        Debug --> CacheService["DistributedCacheService"]

        SuggestionService --> CacheService
        SuggestionService --> TrendingService
        SuggestionService --> Repository["SearchQueryRepository"]

        SearchService --> Batch["BatchWriteService"]
        SearchService --> CacheService
        SearchService --> TrendingService

        CacheService --> Ring["ConsistentHashRing"]
        Batch --> Repository
    end

    subgraph Cache["Redis Cache Layer"]
        Ring -->|"cache-node-0 namespace"| Redis0["Redis keys: cache-node-0:*"]
        Ring -->|"cache-node-1 namespace"| Redis1["Redis keys: cache-node-1:*"]
        Ring -->|"cache-node-2 namespace"| Redis2["Redis keys: cache-node-2:*"]
    end

    Repository --> DB[("PostgreSQL search_queries")]
```

### Component Details

1. **React Frontend:** Provides the search input, debounced suggestion dropdown, keyboard navigation, search submission, trending searches, cache debug panel, and system metrics panel.
2. **Spring Boot Backend:** Exposes REST APIs under `/api`, coordinates suggestion reads, search writes, cache routing, trending computation, and metrics.
3. **PostgreSQL:** Stores durable query-count data in the `search_queries` table. Local development uses Docker PostgreSQL; production can use Neon PostgreSQL.
4. **Redis Cache:** Stores suggestion results by prefix with TTL. Local development uses Docker Redis; production can use Upstash Redis.
5. **Consistent Hash Ring:** Routes each prefix cache key to one of three logical cache nodes (`cache-node-0`, `cache-node-1`, `cache-node-2`) with 150 virtual nodes per logical node.
6. **Suggestion Service:** Checks Redis first. On a cache miss, it asks PostgreSQL for a bounded top-N candidate set, merges recent matching searches, applies recency-aware scoring, returns 10 suggestions, and caches the result.
7. **Batch Write Service:** Buffers search-count increments in memory and flushes aggregated updates every 10 seconds or when 100 unique queries are buffered.
8. **Trending Service:** Tracks recent searches in an in-memory 60-minute window and applies exponential decay for recency-aware ranking.

### Runtime Modes

**Local development:**
```bash
docker compose up --build
```

This starts:
- Backend: `http://localhost:8080`
- PostgreSQL: `localhost:5432`, database `typeahead`, user `typeahead`, password `typeahead`
- Redis: `localhost:6379`

**Production/cloud mode:**
```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://your-neon-host/your-db?sslmode=require
SPRING_DATASOURCE_USERNAME=your-neon-user
SPRING_DATASOURCE_PASSWORD=your-neon-password
REDIS_URL=rediss://default:your-upstash-password@your-upstash-host:6379
REDIS_PASSWORD=your-upstash-password
REDIS_SSL_ENABLED=true
```

With these environment variables, the backend uses Neon PostgreSQL and Upstash Redis without code changes.

---

## 2. Dataset Source and Loading Instructions

### Dataset Source

The project uses an AOL query-log-derived CSV with approximately 491,000 query rows.

* **File location:** `backend/src/main/resources/data/queries.csv`
* **Format:** CSV with a header row:

```csv
query,count
google,32396
yahoo,13344
ebay,12949
```

Each row stores a query string and its historical popularity count.

### Loading Process

Dataset loading is automatic on backend startup:

1. `DatasetLoader` runs after Spring starts via `@PostConstruct`.
2. It checks `searchQueryRepository.count()`.
3. If the table already has rows, it skips loading to avoid duplicate imports.
4. If the table is empty, it reads `queries.csv` from the classpath.
5. It parses each row into a `SearchQuery` entity.
6. It saves rows in batches of 5,000 using `saveAll()`.

### Local Loading Instructions

For a fresh local load:

```bash
docker compose down -v
docker compose up --build
```

`docker compose down -v` removes the local PostgreSQL volume. The next backend startup sees an empty database and reloads the CSV automatically.

---

## 3. API Documentation

### Suggest API

* **Endpoint:** `GET /api/suggest?q=<prefix>`
* **Purpose:** Return up to 10 prefix-matching suggestions.
* **Behavior:** Cache-first. On miss, fetches a bounded DB-side candidate set ordered by historical count, merges recent matching searches, applies recency-aware scoring, caches the result, and returns it.

```bash
curl -s "http://localhost:8080/api/suggest?q=go"
```

```json
{
  "prefix": "go",
  "suggestions": [
    { "query": "google", "score": 32396 },
    { "query": "google.com", "score": 8139 }
  ],
  "latencyMs": 8
}
```

### Search Submit API

* **Endpoint:** `POST /api/search`
* **Purpose:** Return a dummy searched response and record the submitted query.
* **Behavior:** Adds the normalized query to the batch-write buffer, invalidates all cached prefixes for the query, and records a recent search event.

```bash
curl -s -X POST \
  -H "Content-Type: application/json" \
  -d '{"query":"google"}' \
  "http://localhost:8080/api/search"
```

```json
{
  "message": "Searched",
  "query": "google",
  "latencyMs": 4
}
```

### Trending Searches API

* **Endpoint:** `GET /api/trending`
* **Purpose:** Return the current top 10 trending searches.
* **Behavior:** Combines recent search events with persisted historical counts.

```bash
curl -s "http://localhost:8080/api/trending"
```

```json
{
  "trending": [
    { "query": "google", "score": 32496 },
    { "query": "iphone", "score": 1601 }
  ]
}
```

### Cache Debug API

* **Endpoint:** `GET /api/cache/debug?prefix=<prefix>`
* **Purpose:** Show which logical cache node owns a prefix and whether its routed Redis key exists.

```bash
curl -s "http://localhost:8080/api/cache/debug?prefix=google"
```

```json
{
  "prefix": "google",
  "node": "cache-node-0",
  "status": "HIT",
  "cacheKey": "cache-node-0:typeahead:suggest:google",
  "ttlSeconds": 118,
  "latencyMs": 2
}
```

### Cache Stats API

* **Endpoint:** `GET /api/cache/stats`
* **Purpose:** Report cache hit/miss counts and hit rate.

```json
{
  "hitCount": 2,
  "missCount": 23,
  "totalRequests": 25,
  "hitRate": "8.00%"
}
```

### Batch Stats API

* **Endpoint:** `GET /api/batch/stats`
* **Purpose:** Show pending buffered writes and write-reduction counters.

```json
{
  "bufferSize": 3,
  "totalFlushed": 13,
  "totalWritesReduced": 42,
  "totalIndividualWrites": 55,
  "lastFlushTime": "2026-06-22T02:29:28+05:30[Asia/Kolkata]"
}
```

### Performance Stats API

* **Endpoint:** `GET /api/perf/stats`
* **Purpose:** Report suggestion latency percentiles and database I/O counters.

```json
{
  "latencyPercentiles": {
    "p50": 236,
    "p95": 570,
    "p99": 1503
  },
  "sampleCount": 25,
  "dbReadCount": 23,
  "dbWriteCount": 13
}
```

### Hash Ring Info API

* **Endpoint:** `GET /api/ring/info`
* **Purpose:** Show consistent-hash-ring configuration.

```json
{
  "totalNodes": 3,
  "virtualNodesPerNode": 150,
  "totalRingPositions": 450,
  "nodeNames": ["cache-node-0", "cache-node-1", "cache-node-2"]
}
```

---

## 4. Design Choices and Trade-offs

### 1. Local-First Setup With Cloud Overrides

**Choice:** The default configuration runs locally with Docker PostgreSQL and Docker Redis. Cloud services are injected through environment variables.

**Why:** The assignment requires the system to be easy to run locally. A reviewer can start the backend dependencies without Neon or Upstash credentials.

**Trade-off:** Local Docker is not highly available. Production should use managed or clustered services such as Neon and Upstash.

### 2. PostgreSQL as the Source of Truth

**Choice:** Query-count data is stored in PostgreSQL using a `search_queries` table with indexes on `query` and `count`.

**Why:** PostgreSQL provides durable storage, uniqueness constraints, indexed reads, and simple local/prod portability.

**Trade-off:** A trie or specialized search index could serve prefix lookups faster, but PostgreSQL is simpler and sufficient for a 491k-row assignment dataset when combined with caching and bounded top-N reads.

### 3. DB-Side Top-N Suggestions

**Choice:** On cache miss, the backend calls `findByQueryStartingWithIgnoreCaseOrderByCountDesc(prefix, Pageable)` with `typeahead.suggestions.candidate-limit: 100`.

**Why:** Broad prefixes such as `a` or `g` can match thousands of rows. Fetching all matches and sorting in Java would increase memory use and hurt p95 latency. DB-side top-N keeps the read path bounded.

**Recency protection:** A newly searched query may not be in the historical top 100. To avoid losing recency effects, the backend also asks `TrendingService` for recent queries matching the prefix and merges up to 50 into the candidate set before scoring.

**Trade-off:** A low-count query that is neither in the recent window nor in the top candidate set will not be considered for that request. The candidate limit can be increased if recall is more important than latency.

### 4. Redis Cache With Consistent Hashing

**Choice:** Suggestion results are cached in Redis by normalized prefix. A custom consistent hash ring maps each prefix key to one of three logical cache-node namespaces.

**Why:** Redis reduces repeated database reads, TTL handles expiration, and consistent hashing demonstrates how cache keys can be distributed across shards.

**Trade-off:** In this assignment, the three cache nodes are logical namespaces inside one Redis instance. In production, each logical node would map to a separate Redis instance or shard.

### 5. Cache Invalidation

**Choice:** When a search is submitted, all prefixes of that query are invalidated. Searching `google` deletes cached keys for `g`, `go`, `goo`, `goog`, `googl`, and `google`.

**Why:** Suggestions and trending-sensitive rankings become fresh on the next request for those prefixes.

**Trade-off:** This lowers cache hit rate for popular prefixes after writes. A TTL-only strategy would improve hit rate but allow stale suggestions for longer.

### 6. Recency-Aware Trending

**Choice:** Recent searches are stored in a `ConcurrentLinkedDeque` for a 60-minute window. Scores use:

```text
score = allTimeCount + (sum(decayFactor ^ ageMinutes) * boostMultiplier)
```

Current defaults:
- `windowMinutes = 60`
- `decayFactor = 0.95`
- `boostMultiplier = 100`

**Why:** Historical counts keep globally popular queries stable, while recent events allow temporarily popular queries to rise.

**Trade-off:** Recent events are in memory. If the JVM restarts, recent trending state is lost and rankings fall back to PostgreSQL counts.

### 7. Batch Writes

**Choice:** Search submissions are buffered in a `ConcurrentHashMap<String, AtomicLong>` and flushed every 10 seconds or when 100 unique queries accumulate.

**Why:** Repeated searches for the same query are aggregated before database writes. For example, 1,000 searches for `iphone` can become one database update with `count += 1000`.

**Trade-off:** Buffered writes can be lost if the application crashes before the next flush. A production system would use a durable queue or write-ahead log.

---

## 5. Performance Report

### What Is Measured

The backend exposes live counters and latency percentiles:

- `/api/perf/stats`: p50, p95, p99 suggestion latency, DB read count, DB write count
- `/api/cache/stats`: cache hit/miss count and hit rate
- `/api/batch/stats`: pending buffer size and write-reduction counters
- `/api/cache/debug`: logical cache-node routing for a prefix

### Reported Benchmark Snapshot

A small benchmark was run by issuing suggestion requests and search submissions, then reading the system metrics endpoints.

| Metric | Observed Value | Source |
|--------|----------------|--------|
| Total suggestion requests tracked | 25 | `/api/perf/stats.sampleCount` |
| DB reads | 23 | `/api/perf/stats.dbReadCount` |
| DB writes | 13 | `/api/perf/stats.dbWriteCount` |
| Initial cache hit rate | 8.00% | `/api/cache/stats.hitRate` |
| Cache hit latency | 2-10 ms | `GET /api/suggest` response `latencyMs` |
| Cache miss latency | 50-600 ms | `GET /api/suggest` response `latencyMs` |

### Latency Percentiles

| Percentile | Measured Latency | Explanation |
|------------|------------------|-------------|
| P50 | 236 ms | Median suggestion latency during the benchmark. |
| P95 | 570 ms | Tail latency for cache misses and cold database reads. |
| P99 | 1503 ms | Warm-up / connection-pool tail latency. |

### Effect of DB-Side Top-N

The previous broad prefix strategy fetched every row matching a prefix and sorted in application memory. The current implementation bounds the miss path:

```text
Cache miss
  → PostgreSQL top 100 by prefix and historical count
  → merge up to 50 recent matching searches
  → compute recency-aware score
  → return top 10
```

This reduces memory pressure and makes cache-miss latency more predictable for broad prefixes such as `a`, `g`, and `go`.

### Batch Write Reduction

The batch writer tracks:

```text
totalWritesReduced = totalIndividualWrites - totalFlushed
```

If the same query appears many times before a flush, only one database write is needed for that query. This reduces database write pressure compared with synchronously updating PostgreSQL on every search submission.

### Cache Routing Evidence

The hash ring contains:

```json
{
  "totalNodes": 3,
  "virtualNodesPerNode": 150,
  "totalRingPositions": 450,
  "nodeNames": ["cache-node-0", "cache-node-1", "cache-node-2"]
}
```

The `/api/cache/debug?prefix=<prefix>` endpoint shows the owning logical node, routed Redis key, hit/miss status, TTL, and lookup latency for any prefix.
