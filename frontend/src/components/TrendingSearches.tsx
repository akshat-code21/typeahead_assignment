import { useQuery } from "@tanstack/react-query";
import { TrendingUp, Flame } from "lucide-react";
import { fetchTrending } from "@/api/typeaheadApi";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";

export function TrendingSearches() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["trending"],
    queryFn: fetchTrending,
    refetchInterval: 30_000,
  });

  if (isLoading) {
    return (
      <div className="space-y-3">
        <div className="flex items-center gap-2 text-sm font-medium text-muted-foreground">
          <TrendingUp className="h-4 w-4" />
          Trending Searches
        </div>
        <div className="flex flex-wrap gap-2">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-7 w-24 rounded-full" />
          ))}
        </div>
      </div>
    );
  }

  if (isError || !data?.trending?.length) {
    return null;
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-2 text-sm font-medium text-muted-foreground">
        <TrendingUp className="h-4 w-4" />
        Trending Searches
      </div>
      <div className="flex flex-wrap gap-2">
        {data.trending.map((item, index) => (
          <Badge
            key={item.query}
            variant={index < 3 ? "default" : "secondary"}
            className="cursor-default gap-1.5 py-1 px-3 text-xs font-normal transition-all hover:scale-105"
          >
            {index < 3 && <Flame className="h-3 w-3" />}
            {item.query}
            <span className="text-[10px] opacity-60 ml-1">
              {item.score.toLocaleString()}
            </span>
          </Badge>
        ))}
      </div>
    </div>
  );
}
