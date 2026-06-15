export interface Suggestion {
  query: string;
  score: number;
}

export interface SuggestResponse {
  prefix: string;
  suggestions: Suggestion[];
  latencyMs: number;
}

export interface SearchResponse {
  message: string;
  query: string;
}

export interface TrendingResponse {
  trending: Suggestion[];
}

export interface CacheDebugResponse {
  prefix: string;
  node: string;
  status: string;
  cacheKey: string;
}

export interface BatchStatsResponse {
  bufferSize: number;
  totalFlushed: number;
  totalWritesReduced: number;
  totalIndividualWrites: number;
  lastFlushTime: string;
}

export interface CacheStatsResponse {
  hitCount: number;
  missCount: number;
  totalRequests: number;
  hitRate: string;
}
