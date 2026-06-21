import { CheckCircle2, Clock } from "lucide-react";

interface SearchResultProps {
  message: string;
  query: string;
  latencyMs?: number;
}

export function SearchResult({ message, query, latencyMs }: SearchResultProps) {
  return (
    <div className="flex items-center justify-between gap-3 rounded-xl border border-border/50 bg-card/80 backdrop-blur-sm p-4 animate-in fade-in slide-in-from-top-2 duration-300">
      <div className="flex items-center gap-3">
        <CheckCircle2 className="h-5 w-5 text-green-500 shrink-0" />
        <div className="text-sm">
          <span className="text-muted-foreground">{message} for </span>
          <span className="font-medium text-foreground">"{query}"</span>
        </div>
      </div>
      {latencyMs !== undefined && (
        <div className="flex items-center gap-1.5 text-xs text-muted-foreground bg-muted/50 px-2 py-1 rounded-md">
          <Clock className="h-3 w-3" />
          <span>{latencyMs}ms</span>
        </div>
      )}
    </div>
  );
}
