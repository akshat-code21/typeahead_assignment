import { useQuery } from "@tanstack/react-query";
import { Database, CheckCircle2, XCircle, Timer } from "lucide-react";
import { fetchCacheDebug } from "@/api/typeaheadApi";

interface CacheDebugPanelProps {
  prefix: string;
}

export function CacheDebugPanel({ prefix }: CacheDebugPanelProps) {
  const { data, isFetching } = useQuery({
    queryKey: ["cacheDebug", prefix],
    queryFn: () => fetchCacheDebug(prefix),
    enabled: prefix.trim().length > 0,
    staleTime: 0, // always fresh — we want live hit/miss status
  });

  if (!prefix.trim() || !data) return null;

  const isHit = data.status === "HIT";

  return (
    <div className="rounded-xl border border-border/40 bg-card/50 backdrop-blur-sm px-4 py-3 flex flex-wrap items-center gap-x-5 gap-y-2 text-xs text-muted-foreground">
      <div className="flex items-center gap-1.5 font-medium text-foreground">
        <Database className="h-3.5 w-3.5 text-primary" />
        Cache Debug
      </div>

      <div className="flex items-center gap-1.5">
        {isHit ? (
          <CheckCircle2 className="h-3.5 w-3.5 text-green-500" />
        ) : (
          <XCircle className="h-3.5 w-3.5 text-red-400" />
        )}
        <span
          className={
            isHit ? "text-green-500 font-semibold" : "text-red-400 font-semibold"
          }
        >
          {data.status}
        </span>
      </div>

      <div className="flex items-center gap-1">
        <span className="opacity-60">key:</span>
        <code className="font-mono text-[11px] bg-muted/60 px-1.5 py-0.5 rounded">
          {data.cacheKey}
        </code>
      </div>

      {data.ttlSeconds !== null && data.ttlSeconds !== undefined && (
        <div className="flex items-center gap-1">
          <Timer className="h-3 w-3" />
          <span>TTL: {data.ttlSeconds}s</span>
        </div>
      )}

      {isFetching && (
        <span className="opacity-40 italic">refreshing…</span>
      )}
    </div>
  );
}
