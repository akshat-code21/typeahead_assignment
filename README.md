# Search Typeahead System

A full-stack search typeahead system with Redis caching, batch writes, and trending searches.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 4.1, Java 21, Maven |
| Frontend | React 19, TypeScript, Vite, shadcn/ui, TailwindCSS |
| Database | PostgreSQL (Neon) |
| Cache | Redis 7+ | 
| Data Fetching | Axios, TanStack Query |
| Dataset | AOL Query Logs (491K queries) |

---

## Quick Start

### Prerequisites
- Java 21+
- Node.js 18+ / Bun
- PostgreSQL database (or Neon account)
- **Redis 7+** (`brew install redis` on macOS)

### 1. Start Redis

```bash
redis-server
# Runs on localhost:6379 by default
```

### 2. Backend

```bash
cd backend

# Configure database in src/main/resources/application.yaml
# Update spring.datasource.url, username, password if needed

# Run
./mvnw spring-boot:run
# Backend starts at http://localhost:8080
```

### 3. Frontend

```bash
cd frontend

# Install dependencies
bun install

# Run dev server
bun dev
# Frontend starts at http://localhost:5173
```

### Dataset Loading

1. Download the AOL Query Logs from [Kaggle](https://www.kaggle.com/datasets/dineshydv/aol-user-session-collection-500k)
2. Run the aggregation script to produce `queries.csv`:
   ```bash
   python main.py --input ./raw_data --output ./backend/src/main/resources/data/queries.csv
   ```
3. Import directly via psql (faster than JPA for 491K rows):
   ```sql
   ALTER TABLE search_queries ALTER COLUMN updated_at SET DEFAULT NOW();
   \COPY search_queries(query, count) FROM 'backend/src/main/resources/data/queries.csv' WITH (FORMAT csv, HEADER true);
   ```
4. Restart backend — the DatasetLoader detects existing data and skips.

---

## Architecture

```
┌───────────────────────────────────────────────────────────────────┐
│                      Frontend (React + TS)                        │
│  ┌──────────┐  ┌────────────────┐  ┌──────────┐  ┌────────────┐  │
│  │SearchBar │  │TrendingSearches│  │StatsPanel│  │CacheDebug  │  │
│  └────┬─────┘  └───────┬────────┘  └────┬─────┘  │   Panel    │  │
│       │ debounce 300ms  │               │         └────────────┘  │
│  ┌────┴────────────────┴───────────────┴────┐                     │
│  │         Axios + TanStack Query            │                     │
│  └──────────────────┬────────────────────────┘                    │
└─────────────────────┼────────────────────────────────────────────-┘
                      │ HTTP
┌─────────────────────┼─────────────────────────────────────────────┐
│                     │        Backend (Spring Boot)                 │
│  ┌──────────────────┴──────────────────────┐                      │
│  │            REST Controllers              │                      │
│  │  /api/suggest  /api/search  /api/cache/* │                      │
│  └──────┬──────────────┬──────────┬─────────┘                     │
│         │              │          │                                │
│  ┌──────▼──────┐ ┌─────▼────┐ ┌──▼─────────────┐                 │
│  │ Suggestion  │ │  Search  │ │  CacheDebug     │                 │
│  │  Service    │ │  Service │ │  Controller     │                 │
│  └──────┬──────┘ └──┬───┬──-┘ └─────────────────┘                │
│         │           │   │                                         │
│    ┌────▼────┐       │   │    ┌──────────────┐                    │
│    │Distrib. │◄──────┘   ├───►│ BatchWrite   │                    │
│    │ Cache   │           │    │  Service     │                    │
│    │Service  │           │    └──────┬───────┘                    │
│    └────┬────┘           │           │ @Scheduled flush           │
│         │                │    ┌──────▼───────┐                    │
│  ┌──────▼──────────┐     ├───►│  Trending    │                    │
│  │   RedisTemplate  │    │    │  Service     │                    │
│  │                 │     │    └──────────────┘                    │
│  │  keys:          │     │                                        │
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
│              │    PostgreSQL (Neon)  │                             │
│              │   search_queries      │                             │
│              │   (query, count, ts)  │                             │
│              └──────────────────────┘                             │
└────────────────────────────────────────────────────────────────────┘
```

### Data Flow

**Suggestion Request:**
```
SearchBar → debounce 300ms → GET /api/suggest
  → SuggestionService
    → DistributedCacheService.get(prefix)
      → RedisTemplate.opsForValue().get("typeahead:suggest:<prefix>")
        ├─ HIT:  return cached List<SuggestionResponse>
        └─ MISS: DB query → sort by count → limit 10
                 → cache.put(prefix, results, TTL=300s)
                 → return results
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

---

## Design Choices and Trade-offs

### 1. Redis as Distributed Cache

**Design:** `DistributedCacheService` uses Spring Data Redis (`RedisTemplate`) with String key serialization and JSON value serialization. Cache keys follow the pattern `typeahead:suggest:<normalized-prefix>` with a configurable TTL (default 300s).

**Why Redis:**
- **True distribution:** Redis is a network-separated process; the application can scale horizontally and share a single cache layer, unlike an in-process `ConcurrentHashMap`.
- **Built-in TTL:** Redis natively supports key expiry — no manual eviction logic needed.
- **Atomic operations:** `GET`, `SET EX`, `DEL` are all atomic, making concurrent access safe.
- **Production-ready:** Supports clustering (Redis Cluster), replication, and persistence (RDB/AOF).

**Redis Cluster and Consistent Hashing:** In a Redis Cluster setup, Redis itself uses consistent hashing (hash slots) to distribute keys across shards — this is the production-grade approach. For this assignment, a single Redis node is used for simplicity while keeping the architecture extensible.

**Trade-offs:**
- Single Redis node is a potential single point of failure (mitigated in production with Redis Sentinel or Cluster).
- Network hop to Redis adds ~1ms latency vs in-process cache, but this is dwarfed by the DB query savings.

### 2. Cache Invalidation Strategy

**Design:** On search submission, ALL prefix keys derived from the query are invalidated (deleted from Redis). E.g., searching "iphone" deletes `typeahead:suggest:i`, `typeahead:suggest:ip`, ..., `typeahead:suggest:iphone`.

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

**Design:** `ConcurrentLinkedDeque<SearchEvent>` stores recent search events with timestamps. Scoring uses exponential decay:

```
score = allTimeCount + (Σ decayFactor^ageMinutes) × boostMultiplier
```

Default values: `decayFactor = 0.95`, `boostMultiplier = 100`, `windowMinutes = 60`.

**How recent activity affects ranking:**
- A search 1 minute ago contributes `0.95^1 = 0.95`
- A search 30 minutes ago contributes `0.95^30 ≈ 0.21`
- A search 60 minutes ago is evicted from the window

**How over-ranking is avoided:**
- Events older than `windowMinutes` (60 min) are evicted from the deque
- The decay factor ensures recent events have diminishing influence over time
- Once eviction happens, the query's ranking falls back to its all-time count

**Cache and trending:**
- Cache prefixes are invalidated on every search submission via `invalidateAllPrefixes()`
- The trending endpoint is separate from the suggestion endpoint, so trending freshness doesn't depend on cache TTL

**Trade-offs:**
- In-memory storage means trending data is lost on restart (acceptable for assignment)
- The decay factor (0.95) and window (60 min) are configurable via `application.yaml`

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
| Cache hit rate (after warmup) | `GET /api/cache/stats` → `hitRate` | 30–60% |
| Write reduction | `GET /api/batch/stats` → `totalWritesReduced` | varies |

### p95 Latency Measurement

Run this script after the backend is warm:

```bash
echo "latency_ms" > latency.csv
for i in $(seq 1 100); do
  prefix=$(echo "abcdefghij" | fold -w1 | shuf | head -c 3)
  start=$(date +%s%3N)
  curl -s "http://localhost:8080/api/suggest?q=$prefix" > /dev/null
  end=$(date +%s%3N)
  echo "$((end - start))" >> latency.csv
done

sort -n latency.csv | tail -n +2 | awk '
  { a[NR] = $1; sum += $1 }
  END {
    print "Requests: " NR;
    print "Avg:      " sum/NR " ms";
    print "p50:      " a[int(NR*0.50)] " ms";
    print "p95:      " a[int(NR*0.95)] " ms";
    print "p99:      " a[int(NR*0.99)] " ms";
  }
'
```

### Cache Debug

```bash
# Check cache status for various prefixes
for prefix in "a" "b" "go" "iph" "java" "react"; do
  echo -n "$prefix → "
  curl -s "http://localhost:8080/api/cache/debug?prefix=$prefix" | \
    python3 -c "import sys,json; d=json.load(sys.stdin); print(d['status'], '| key:', d['cacheKey'], '| TTL:', d.get('ttlSeconds', 'N/A'), 's')"
done
```

Expected output after querying those prefixes at least once:
```
a    → HIT  | key: typeahead:suggest:a    | TTL: 287 s
b    → MISS | key: typeahead:suggest:b    | TTL: N/A
go   → HIT  | key: typeahead:suggest:go   | TTL: 243 s
iph  → MISS | key: typeahead:suggest:iph  | TTL: N/A
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
│       │   ├── DistributedCacheService.java     # Redis get/put/invalidate + hit metrics
│       │   ├── BatchWriteService.java           # ConcurrentHashMap buffer + @Scheduled flush
│       │   └── TrendingService.java             # Exponential decay + window eviction
│       ├── controller/
│       │   ├── SuggestController.java           # GET /api/suggest, GET /api/trending
│       │   ├── SearchController.java            # POST /api/search
│       │   └── CacheDebugController.java        # GET /api/cache/debug, /cache/stats, /batch/stats
│       ├── dto/
│       │   ├── SuggestionResponse.java          # {query, score}
│       │   ├── SearchRequest.java               # {query}
│       │   ├── SearchResponse.java              # {message, query}
│       │   └── CacheDebugResponse.java          # {prefix, node, status, cacheKey, ttlSeconds}
│       └── loader/DatasetLoader.java            # @PostConstruct CSV → DB batch loader
├── frontend/
│   └── src/
│       ├── App.tsx                              # Main layout, lifts prefix state
│       ├── api/typeaheadApi.ts                  # Axios API client
│       ├── hooks/useDebounce.ts                 # 300ms debounce hook
│       ├── types/index.ts                       # TypeScript interfaces
│       └── components/
│           ├── SearchBar.tsx                    # Input + dropdown + keyboard nav
│           ├── SearchResult.tsx                 # "Searched" confirmation banner
│           ├── TrendingSearches.tsx             # Trending badges with auto-refresh
│           ├── StatsPanel.tsx                   # Cache & batch metrics cards (5 cards)
│           └── CacheDebugPanel.tsx              # Live HIT/MISS + cacheKey + TTL display
├── TESTING.md                                   # Testing guide with curl commands
└── README.md                                    # This file
```

---

## Screenshots

> **Search input with suggestion dropdown and keyboard navigation:**
> Type any prefix (e.g. "goo") to see the dropdown appear with up to 10 sorted suggestions.
> Use ↑↓ to navigate, Enter to select, Escape to dismiss.

> **Cache Debug Panel (live HIT/MISS):**
> Below the search box, the CacheDebugPanel shows the Redis cache key, HIT/MISS status, and remaining TTL in seconds for the current prefix.

> **Trending searches section:**
> The top 3 trending (recently + historically popular) are highlighted with a 🔥 icon.

> **System Metrics:**
> The StatsPanel shows cache hit rate, cache misses, writes reduced, buffer size, and last flush time — updated every 5 seconds.

> **Cache debug API output:**
> ```bash
> curl "http://localhost:8080/api/cache/debug?prefix=goo"
> # {"prefix":"goo","node":"redis","status":"HIT","cacheKey":"typeahead:suggest:goo","ttlSeconds":234}
> ```
