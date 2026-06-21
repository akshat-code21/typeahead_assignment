# Search Typeahead System

A full-stack search typeahead system with Redis caching, batch writes, and trending searches.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 4.1, Java 21, Maven |
| Frontend | React 19, TypeScript, Vite, shadcn/ui, TailwindCSS |
| Database | PostgreSQL locally, Neon PostgreSQL in production |
| Cache | Redis 7+ locally, Upstash Redis in production |
| Data Fetching | Axios, TanStack Query |
| Dataset | AOL Query Logs (491K queries) |

---

## Quick Start

### Option A: Run Backend + PostgreSQL + Redis via Docker Compose (Easiest)

If you have Docker installed, this starts a fully local backend stack:

```bash
docker compose up --build
```

The backend starts at `http://localhost:8080` and connects to:

| Service | Local URL / Port | Credentials |
|---------|------------------|-------------|
| PostgreSQL | `localhost:5432` | database `typeahead`, user `typeahead`, password `typeahead` |
| Redis | `localhost:6379` | no password |

The first backend startup loads `backend/src/main/resources/data/queries.csv` into the local PostgreSQL volume. Later restarts reuse the same Docker volume and skip loading if rows already exist.

To reset the local database and reload the dataset from scratch:

```bash
docker compose down -v
docker compose up --build
```

### Production / Cloud Overrides

For production, keep the same application image and provide cloud environment variables. These override the local defaults:

```bash
export SPRING_DATASOURCE_URL="jdbc:postgresql://your-neon-host/your-db?sslmode=require"
export SPRING_DATASOURCE_USERNAME="your-neon-user"
export SPRING_DATASOURCE_PASSWORD="your-neon-password"

export REDIS_URL="rediss://default:your-upstash-password@your-upstash-host:6379"
export REDIS_PASSWORD="your-upstash-password"
export REDIS_SSL_ENABLED=true
```

With those variables set, the backend connects to Neon PostgreSQL and Upstash Redis instead of the local defaults.

---

### Option B: Run Services Individually (Local Dev)

#### 1. Start Redis
Ensure you have Redis installed (`brew install redis` on macOS) and run:
```bash
redis-server
# Runs on localhost:6379 by default
```

#### 2. Start PostgreSQL
Run PostgreSQL locally and create a database/user matching the backend defaults:

```bash
createdb typeahead
createuser typeahead
psql -d typeahead -c "ALTER USER typeahead WITH PASSWORD 'typeahead';"
psql -d typeahead -c "GRANT ALL PRIVILEGES ON DATABASE typeahead TO typeahead;"
```

Or start just the local Docker dependencies:

```bash
docker compose up postgres redis
```

#### 3. Start Backend
```bash
cd backend
./mvnw spring-boot:run
# Backend starts at http://localhost:8080
```

By default, the backend uses:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/typeahead
SPRING_DATASOURCE_USERNAME=typeahead
SPRING_DATASOURCE_PASSWORD=typeahead
REDIS_URL=redis://localhost:6379
```

---

### 4. Start Frontend (Runs on Host)

Regardless of how you started the backend, run the React frontend from the `frontend/` directory:

```bash
cd frontend
bun install   # or npm install
bun dev       # or npm run dev
# Frontend starts at http://localhost:5173
```

---

## Architecture

```
┌───────────────────────────────────────────────────────────────────┐
│                      Frontend (React + TS)                        │
│  ┌──────────┐  ┌────────────────┐  ┌──────────┐  ┌────────────┐   │
│  │SearchBar │  │TrendingSearches│  │StatsPanel│  │CacheDebug  │   │
│  └────┬─────┘  └───────┬────────┘  └────┬─────┘  │   Panel    │   │
│       │ debounce 300ms │                │        └────────────┘   │
│  ┌────┴────────────────┴────────────────┴────┐                    │
│  │         Axios + TanStack Query            │                    │
│  └──────────────────┬────────────────────────┘                    │
└─────────────────────┼─────────────────────────────────────────────┘
                      │ HTTP
┌─────────────────────┼─────────────────────────────────────────────┐
│                     │        Backend (Spring Boot)                │
│  ┌──────────────────┴───────────────────────┐                     │
│  │            REST Controllers              │                     │
│  │  /api/suggest  /api/search  /api/cache/* │                     │
│  │  /api/perf/*   /api/ring/*               │                     │
│  └──────┬──────────────┬──────────┬─────────┘                     │
│         │              │          │                               │
│  ┌──────▼──────┐ ┌─────▼────┐ ┌───▼─────────────┐                 │
│  │ Suggestion  │ │  Search  │ │  CacheDebug     │                 │
│  │  Service    │ │  Service │ │  Controller     │                 │
│  └──────┬──────┘ └──┬────┬──┘ └─────────────────┘                 │
│         │           │    │                                        │
│    ┌────▼────┐      │    │    ┌──────────────┐                    │
│    │Distrib. │◄─────┘    ├───►│ BatchWrite   │                    │
│    │ Cache   │           │    │  Service     │                    │
│    │Service  │           │    └──────┬───────┘                    │
│    └────┬────┘           │           │ @Scheduled flush           │
│         │                │    ┌──────▼───────┐                    │
│  ┌──────▼──────────┐     ├───►│  Trending    │                    │
│  │ ConsistentHash  │     │    │  Service     │                    │
│  │   Ring          │     │    └──────────────┘                    │
│  │  3 nodes ×      │     │                                        │
│  │  150 virtual    │     │    ┌──────────────┐                    │
│  └──────┬──────────┘     │    │ Performance  │                    │
│         │ route key      │    │ Metrics Svc  │                    │
│  ┌──────▼──────────┐     │    │ (p95, DB I/O)│                    │
│  │   RedisTemplate │     │    └──────────────┘                    │
│  │  keys:          │     │                                        │
│  │  cache-node-X:  │     │                                        │
│  │  typeahead:     │     │                                        │
│  │  suggest:<pfx>  │     │                                        │
│  └─────────────────┘     │                                        │
│         │                │                                        │
│  ┌──────▼──────────┐     │                                        │
│  │  Redis Server   │     │                                        │
│  │  (localhost:    │     │                                        │
│  │    6379)        │     │                                        │
│  └─────────────────┘     │                                        │
│                          │                                        │
│              ┌───────────▼──────────┐                             │
│              │    PostgreSQL (Neon) │                             │
│              │   search_queries     │                             │
│              │   (query, count, ts) │                             │
│              └──────────────────────┘                             │
└───────────────────────────────────────────────────────────────────┘
```

### Data Flow

**Suggestion Request:**
```
SearchBar → debounce 300ms → GET /api/suggest
  → SuggestionService
    → DistributedCacheService.get(prefix)
      → ConsistentHashRing.getNode(baseKey)  → determines owning node
      → RedisTemplate.opsForValue().get("cache-node-X:typeahead:suggest:<prefix>")
        ├─ HIT:  return cached List<SuggestionResponse>
        └─ MISS: DB query (dbReadCount++) → sort by count → limit 10
                 → cache.put(prefix, results, TTL=300s)
                 → return results
  → PerformanceMetricsService.recordLatency(latencyMs)
```

**Search Submission:**
```
SearchBar → POST /api/search
  → SearchService
    ├─ BatchWriteService.buffer(query)          [in-memory accumulation]
    ├─ CacheService.invalidateAllPrefixes(query) [Redis DEL for each prefix]
    └─ TrendingService.recordSearchEvent(query)  [in-memory deque]
```

**Batch Flush:**
```
@Scheduled(every 10s) → BatchWriteService.flush()
  → snapshot ConcurrentHashMap → upsert to PostgreSQL (1 write per unique query)
  → PerformanceMetricsService.incrementDbWrites(flushedCount)
```

---

## API Documentation

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| GET | `/api/suggest?q=<prefix>` | Fetch suggestions | Query param `q` | `{ prefix, suggestions: [{query, score}], latencyMs }` |
| POST | `/api/search` | Submit search | `{ "query": "..." }` | `{ "message": "Searched", "query": "..." }` |
| GET | `/api/trending` | Get trending searches | — | `{ trending: [{query, score}] }` |
| GET | `/api/cache/debug?prefix=<prefix>` | Cache debug info | Query param `prefix` | `{ prefix, node, status, cacheKey, ttlSeconds }` |
| GET | `/api/cache/stats` | Cache hit/miss metrics | — | `{ hitCount, missCount, totalRequests, hitRate }` |
| GET | `/api/batch/stats` | Batch write metrics | — | `{ bufferSize, totalFlushed, totalWritesReduced, ... }` |
| GET | `/api/perf/stats` | P95 latency & DB I/O | — | `{ latencyPercentiles: {p50, p95, p99}, dbReadCount, dbWriteCount }` |
| GET | `/api/ring/info` | Hash ring config | — | `{ totalNodes, virtualNodesPerNode, totalRingPositions, nodeNames }` |

---

## Design Choices and Trade-offs

### 1. Redis as Distributed Cache with Consistent Hashing

**Design:** `DistributedCacheService` uses Spring Data Redis (`RedisTemplate`) with String key serialization and JSON value serialization. A `ConsistentHashRing` routes each cache key to one of **3 logical cache nodes** using **150 virtual nodes** per physical node (450 total ring positions). Cache keys follow the pattern `cache-node-X:typeahead:suggest:<normalized-prefix>` with a configurable TTL (default 300s).

**Consistent Hashing Implementation:**
- `ConsistentHashRing` uses MD5 hashing to map both virtual-node labels and cache keys to positions on a 32-bit integer ring.
- For a given prefix, the ring finds the closest node position clockwise from the key's hash.
- Each logical node's keys are namespaced with the node name (e.g., `cache-node-0:`, `cache-node-1:`, `cache-node-2:`) to demonstrate distribution.
- In production, each logical node would map to a separate Redis instance/shard. For this assignment, all nodes share a single Redis instance but use distinct key prefixes.
- The `/api/cache/debug` endpoint shows exactly which node owns each prefix and the full routed key.
- The `/api/ring/info` endpoint exposes the ring configuration.

**Why Redis:**
- **True distribution:** Redis is a network-separated process; the application can scale horizontally and share a single cache layer, unlike an in-process `ConcurrentHashMap`.
- **Built-in TTL:** Redis natively supports key expiry — no manual eviction logic needed.
- **Atomic operations:** `GET`, `SET EX`, `DEL` are all atomic, making concurrent access safe.
- **Production-ready:** Supports clustering (Redis Cluster), replication, and persistence (RDB/AOF).

**Trade-offs:**
- Single Redis instance is a potential single point of failure (mitigated in production with Redis Sentinel or Cluster).
- The consistent hash ring adds a negligible computation cost (~0.01ms per key) but enables transparent horizontal scaling.
- Network hop to Redis adds ~1ms latency vs in-process cache, but this is dwarfed by the DB query savings.

### 2. Cache Invalidation Strategy

**Design:** On search submission, ALL prefix keys derived from the query are invalidated (deleted from Redis). E.g., searching "iphone" deletes `cache-node-X:typeahead:suggest:i`, `cache-node-Y:typeahead:suggest:ip`, ..., `cache-node-Z:typeahead:suggest:iphone` (where each prefix is routed through the consistent hash ring to its owning node).

**Why:** Ensures that newly popular queries are reflected in suggestions immediately after a search is submitted.

**Trade-off:** Aggressive invalidation — a single search deletes N Redis keys (where N = query length). Lowers hit rate but guarantees freshness. Alternative: rely on TTL-only expiry for higher hit rate but slightly stale results.

### 3. TTL-Based Cache Expiry

**Design:** Each Redis key is set with `SET key value EX <ttlSeconds>`. Redis handles expiry automatically.

**Why:** Zero application-level eviction code needed. Redis's active expiry + lazy expiry ensures memory is reclaimed efficiently.

### 4. Batch Write Buffer

**Design:** `ConcurrentHashMap<String, AtomicLong>` — each key is a query, value is the accumulated delta count. Flushed every 10 seconds or when 100 unique queries accumulate.

**Why:** If 1000 users search "iphone" in 10 seconds, instead of 1000 DB writes, we do 1 DB write with `count += 1000`. This reduces write pressure significantly.

**Trade-off: Data loss on crash.** If the app crashes before a flush, buffered counts are lost. For this assignment, this is acceptable. In production, you'd persist the buffer to a WAL (write-ahead log) or use a message queue like Kafka.

**Failure scenario:**
- Buffer has: `{"iphone": 50, "macbook": 10}`
- App crashes → 50 "iphone" searches and 10 "macbook" searches are lost
- Impact: Counts are slightly lower than reality. Suggestions are slightly stale.
- Mitigation: Reduce flush interval (e.g., 2s) to limit the loss window.

### 5. Trending Searches (Recency-Aware Ranking)

To incorporate recency, suggestions and trending lists are ranked using a combined score of historical popularity and recent search activity.

#### Scoring Formula
$$score = allTimeCount + \left(\sum decayFactor^{ageMinutes}\right) \times boostMultiplier$$
*Default values: `decayFactor = 0.95`, `boostMultiplier = 100`, `windowMinutes = 60`.*

---

#### 1. How recent searches are tracked
* **In-Memory Deque:** Recent search events are recorded in a thread-safe `ConcurrentLinkedDeque<SearchEvent>` in `TrendingService`.
* **Event Metadata:** Each `SearchEvent` stores the search query string and the `Instant` timestamp when the query was submitted.

#### 2. How recent activity affects ranking
* **Recency Boost:** When a query is searched, it enters the sliding window. Its score receives a `boostMultiplier` (100) multiplied by a decaying weight based on its age.
  - A search **1 minute ago** adds: $0.95^{1} \times 100 = 95.0$ to the score.
  - A search **30 minutes ago** adds: $0.95^{30} \times 100 \approx 21.46$ to the score.
* **Re-ranking Suggestions & Trending:** Both `/api/suggest?q=...` and `/api/trending` retrieve the current raw count from the database and combine it with the decayed boost score to sort suggestions.

#### 3. How the system avoids permanently over-ranking queries
* **Sliding Window Eviction:** During every new search event or get request, events older than `windowMinutes` (60 minutes) are evicted from the deque using `evictOldEvents()`.
* **Decay Function:** The exponential decay factor ($0.95^{age}$) ensures that the boost contribution of a spike in traffic diminishes rapidly minute-by-minute.
* **Automatic Fallback:** Once a search event is evicted from the sliding window, its boost goes to `0`, and its ranking automatically falls back to its historical all-time DB count.

#### 4. How the cache is updated/invalidated when rankings change
* **Cache Invalidation:** When a search is submitted, `DistributedCacheService.invalidateAllPrefixes(query)` is triggered. It computes all prefixes of the query and invalidates their cache entries across the Redis cluster.
* **Freshness Propagation:** The next request for `/api/suggest?q=<prefix>` experiences a cache miss, queries the database, applies the latest recency boost, and populates the Redis cache with the updated ranking.
* **Separation of Concerns:** The `/api/trending` endpoint does not cache results because it reads directly from JVM memory and Neon DB metadata. This ensures that the trending widget displays real-time popularity shifts without waiting for suggestion TTLs to expire.

#### 5. Trade-offs: Freshness vs. Latency vs. Complexity
* **Freshness vs. Latency:** Cache invalidation on submission guarantees absolute freshness of the suggestions. However, it increases suggestion latency on the next keypress because of cache misses (50–600ms DB read vs. <10ms cache hit). 
* **In-Memory Tracking vs. Persistence:** Storing recent search events in JVM memory (`ConcurrentLinkedDeque`) keeps write/read latency ultra-low (avoiding constant DB write/read operations for trending). The trade-off is volatility: trending state is lost when the backend server restarts.
* **Implementation Complexity:** Using a simple in-memory sliding window and on-the-fly score calculation is significantly simpler than managing complex database/Redis streaming architectures, while fully satisfying the HLD requirements of a real-time typeahead system.

### 6. Database Choice (PostgreSQL)

**Why:** Reliable, supports indexes for prefix queries (`LIKE 'prefix%'`), hosted on Neon for easy remote access.

**Trade-off:** For prefix queries on 491K rows, a trie data structure would be faster (O(prefix length) vs O(log N + matches)). But PostgreSQL with a B-tree index on `query` column is fast enough at this scale and much simpler to maintain.

---

## Performance Report

### Measured Latency

After running the app and performing ~50 searches:

| Metric | Endpoint | Measured |
|--------|----------|----------|
| Suggestion latency (cache HIT) | `GET /api/suggest` → `latencyMs` | 2–10 ms |
| Suggestion latency (cache MISS) | `GET /api/suggest` → `latencyMs` | 50–600 ms |
| P95 latency | `GET /api/perf/stats` → `latencyPercentiles.p95` | varies (from last 1000 samples) |
| P50 latency | `GET /api/perf/stats` → `latencyPercentiles.p50` | varies |
| Cache hit rate (after warmup) | `GET /api/cache/stats` → `hitRate` | 30–60% |
| DB reads (cache misses) | `GET /api/perf/stats` → `dbReadCount` | increments per cache-miss query |
| DB writes (batch flushes) | `GET /api/perf/stats` → `dbWriteCount` | increments per flush entry |
| Write reduction | `GET /api/batch/stats` → `totalWritesReduced` | varies |

### Latency Percentile Tracking

`PerformanceMetricsService` collects the last 1000 suggest-API latency samples in a thread-safe circular buffer. Percentiles (p50, p95, p99) are computed on demand by sorting the buffer. The `StatsPanel` in the frontend displays the P95 latency prominently, with p50 and p99 in the detail line.

### Database Read/Write Counts

- **DB Reads:** Incremented by `SuggestionService` each time a cache miss triggers a database query.
- **DB Writes:** Incremented by `BatchWriteService` each time a batch flush writes entries to PostgreSQL.

Both counters are tracked via `PerformanceMetricsService` and exposed through `GET /api/perf/stats`.

### Cache Debug with Consistent Hashing

```bash
# Check cache status and node routing for various prefixes
for prefix in "a" "app" "google" "iphone" "java" "react"; do
  echo -n "$prefix → "
  curl -s "http://localhost:8080/api/cache/debug?prefix=$prefix" | \
    python3 -c "import sys,json; d=json.load(sys.stdin); print(d['node'], '|', d['status'], '| key:', d['cacheKey'], '| TTL:', d.get('ttlSeconds', 'N/A'), 's')"
done
```

Expected output (node assignment determined by consistent hash ring):
```
a      → cache-node-1 | HIT  | key: cache-node-1:typeahead:suggest:a      | TTL: 287 s
app    → cache-node-1 | HIT  | key: cache-node-1:typeahead:suggest:app    | TTL: 243 s
google → cache-node-0 | MISS | key: cache-node-0:typeahead:suggest:google | TTL: N/A
iphone → cache-node-0 | HIT  | key: cache-node-0:typeahead:suggest:iphone | TTL: 180 s
java   → cache-node-2 | MISS | key: cache-node-2:typeahead:suggest:java   | TTL: N/A
react  → cache-node-2 | HIT  | key: cache-node-2:typeahead:suggest:react  | TTL: 120 s
```

### Hash Ring Info

```bash
curl -s http://localhost:8080/api/ring/info | python3 -m json.tool
```

```json
{
    "totalNodes": 3,
    "virtualNodesPerNode": 150,
    "totalRingPositions": 450,
    "nodeNames": ["cache-node-0", "cache-node-1", "cache-node-2"]
}
```

---

## Project Structure

```
assignment/
├── backend/
│   └── src/main/java/com/assignment/typeahead/
│       ├── TypeaheadApplication.java             # Entry point
│       ├── config/
│       │   ├── AppConfig.java                   # CORS, @EnableScheduling
│       │   └── RedisConfig.java                 # RedisTemplate bean (String key, JSON value)
│       ├── model/SearchQuery.java               # JPA entity (query, count, updated_at)
│       ├── repository/SearchQueryRepository.java # JPA repository (prefix query, top-10)
│       ├── service/
│       │   ├── SuggestionService.java           # Cache-first → DB fallback → sort → limit 10
│       │   ├── SearchService.java               # Buffer + invalidate + record trending
│       │   ├── DistributedCacheService.java     # Redis get/put/invalidate via hash ring
│       │   ├── ConsistentHashRing.java          # MD5 hash ring (3 nodes × 150 virtual)
│       │   ├── PerformanceMetricsService.java   # P95 latency + DB read/write counters
│       │   ├── BatchWriteService.java           # ConcurrentHashMap buffer + @Scheduled flush
│       │   └── TrendingService.java             # Exponential decay + window eviction
│       ├── controller/
│       │   ├── SuggestController.java           # GET /api/suggest, GET /api/trending
│       │   ├── SearchController.java            # POST /api/search
│       │   └── CacheDebugController.java        # GET /api/cache/debug, /stats, /perf, /ring
│       ├── dto/
│       │   ├── SuggestionResponse.java          # {query, score}
│       │   ├── SearchRequest.java               # {query}
│       │   ├── SearchResponse.java              # {message, query}
│       │   └── CacheDebugResponse.java          # {prefix, node, status, cacheKey, ttlSeconds}
│       └── loader/DatasetLoader.java            # @PostConstruct CSV → DB batch loader
├── frontend/
│   └── src/
│       ├── App.tsx                              # Main layout, lifts prefix state
│       ├── api/typeaheadApi.ts                  # Axios API client (+ fetchPerfStats)
│       ├── hooks/useDebounce.ts                 # 300ms debounce hook
│       ├── types/index.ts                       # TypeScript interfaces (+ PerfStatsResponse)
│       └── components/
│           ├── SearchBar.tsx                    # Input + dropdown + keyboard nav
│           ├── SearchResult.tsx                 # "Searched" confirmation banner
│           ├── TrendingSearches.tsx             # Trending badges with auto-refresh
│           ├── StatsPanel.tsx                   # 8-card metrics (P95, DB R/W, cache, batch)
│           └── CacheDebugPanel.tsx              # Live HIT/MISS + node + cacheKey + TTL
├── TESTING.md                                   # Testing guide with curl commands
└── README.md                                    # This file
```

---

## Screenshots

> **Search input with suggestion dropdown and keyboard navigation:**
> Type any prefix (e.g. "goo") to see the dropdown appear with up to 10 sorted suggestions.
> Use ↑↓ to navigate, Enter to select, Escape to dismiss.
>
> ![Search input with suggestion dropdown](screenshots/suggest_dropdown.png)

> **Cache Debug Panel (live HIT/MISS) & Trending Searches:**
> Below the search box, the CacheDebugPanel shows the Redis cache key, cache node, HIT/MISS status, and remaining TTL in seconds for the current prefix. The trending searches (recently + historically popular) are highlighted below it.
>
> ![Cache Debug Panel and Trending Searches](screenshots/cache_debug_trending.png)

> **System Metrics & Search Confirmation:**
> The StatsPanel shows 8 metric cards — cache hit rate, cache misses, P95 latency (with p50/p99), DB reads, DB writes, writes reduced, buffer size, and last flush time (IST) — updated every 5 seconds. A confirmation banner also displays when a search is successfully submitted.
>
> ![System Metrics and Search Confirmation](screenshots/system_metrics_panel.png)
