import axios from "axios";
import type {
  SuggestResponse,
  SearchResponse,
  TrendingResponse,
  CacheDebugResponse,
  BatchStatsResponse,
  CacheStatsResponse,
} from "@/types";

const api = axios.create({
  baseURL: "http://localhost:8080/api",
  timeout: 10000,
});

export async function fetchSuggestions(
  prefix: string
): Promise<SuggestResponse> {
  const { data } = await api.get<SuggestResponse>("/suggest", {
    params: { q: prefix },
  });
  return data;
}

export async function submitSearch(query: string): Promise<SearchResponse> {
  const { data } = await api.post<SearchResponse>("/search", { query });
  return data;
}

export async function fetchTrending(): Promise<TrendingResponse> {
  const { data } = await api.get<TrendingResponse>("/trending");
  return data;
}

export async function fetchCacheDebug(
  prefix: string
): Promise<CacheDebugResponse> {
  const { data } = await api.get<CacheDebugResponse>("/cache/debug", {
    params: { prefix },
  });
  return data;
}

export async function fetchBatchStats(): Promise<BatchStatsResponse> {
  const { data } = await api.get<BatchStatsResponse>("/batch/stats");
  return data;
}

export async function fetchCacheStats(): Promise<CacheStatsResponse> {
  const { data } = await api.get<CacheStatsResponse>("/cache/stats");
  return data;
}
