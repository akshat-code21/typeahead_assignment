import { useQuery } from "@tanstack/react-query";
import { Activity, Database, Zap, HardDrive, Clock, Gauge, BookOpen, PenTool } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { fetchCacheStats, fetchBatchStats, fetchPerfStats } from "@/api/typeaheadApi";

export function StatsPanel() {
  const { data: cacheStats } = useQuery({
    queryKey: ["cacheStats"],
    queryFn: fetchCacheStats,
    refetchInterval: 5_000,
  });

  const { data: batchStats } = useQuery({
    queryKey: ["batchStats"],
    queryFn: fetchBatchStats,
    refetchInterval: 5_000,
  });

  const { data: perfStats } = useQuery({
    queryKey: ["perfStats"],
    queryFn: fetchPerfStats,
    refetchInterval: 5_000,
  });

  const lastFlush =
    batchStats?.lastFlushTime && batchStats.lastFlushTime !== "null"
      ? new Date(batchStats.lastFlushTime.replace(/\[.*]$/, "")).toLocaleTimeString("en-IN", { timeZone: "Asia/Kolkata" })
      : "Never";

  const fmtLatency = (v: number | undefined) =>
    v !== undefined && v >= 0 ? `${v} ms` : "—";

  const stats = [
    {
      label: "Cache Hit Rate",
      value: cacheStats?.hitRate ?? "—",
      icon: Zap,
      detail: `${cacheStats?.hitCount ?? 0} hits / ${cacheStats?.totalRequests ?? 0} total`,
    },
    {
      label: "Cache Misses",
      value: cacheStats?.missCount?.toLocaleString() ?? "—",
      icon: Activity,
      detail: "DB fallback queries",
    },
    {
      label: "P95 Latency",
      value: fmtLatency(perfStats?.latencyPercentiles?.p95),
      icon: Gauge,
      detail: `p50 ${fmtLatency(perfStats?.latencyPercentiles?.p50)} · p99 ${fmtLatency(perfStats?.latencyPercentiles?.p99)}`,
    },
    {
      label: "DB Reads",
      value: perfStats?.dbReadCount?.toLocaleString() ?? "—",
      icon: BookOpen,
      detail: "Cache-miss DB queries",
    },
    {
      label: "DB Writes",
      value: perfStats?.dbWriteCount?.toLocaleString() ?? "—",
      icon: PenTool,
      detail: "Batched flush writes",
    },
    {
      label: "Writes Reduced",
      value: batchStats?.totalWritesReduced?.toLocaleString() ?? "—",
      icon: Database,
      detail: `${batchStats?.totalIndividualWrites ?? 0} individual → ${batchStats?.totalFlushed ?? 0} batched`,
    },
    {
      label: "Buffer Size",
      value: batchStats?.bufferSize?.toLocaleString() ?? "—",
      icon: HardDrive,
      detail: "Pending flush",
    },
    {
      label: "Last Flush",
      value: lastFlush,
      icon: Clock,
      detail: "Batch write timestamp",
    },
  ];

  return (
    <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
      {stats.map((stat) => (
        <Card
          key={stat.label}
          className="border-border/50 bg-card/60 backdrop-blur-sm"
        >
          <CardHeader className="flex flex-row items-center justify-between pb-1 pt-4 px-4">
            <CardTitle className="text-xs font-medium text-muted-foreground">
              {stat.label}
            </CardTitle>
            <stat.icon className="h-3.5 w-3.5 text-muted-foreground" />
          </CardHeader>
          <CardContent className="px-4 pb-4">
            <div className="text-xl font-semibold tabular-nums">
              {stat.value}
            </div>
            <p className="text-[11px] text-muted-foreground mt-0.5">
              {stat.detail}
            </p>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}

