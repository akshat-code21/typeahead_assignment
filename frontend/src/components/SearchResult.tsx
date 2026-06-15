import { CheckCircle2 } from "lucide-react";

interface SearchResultProps {
  message: string;
  query: string;
}

export function SearchResult({ message, query }: SearchResultProps) {
  return (
    <div className="flex items-center gap-3 rounded-xl border border-border/50 bg-card/80 backdrop-blur-sm p-4 animate-in fade-in slide-in-from-top-2 duration-300">
      <CheckCircle2 className="h-5 w-5 text-green-500 shrink-0" />
      <div className="text-sm">
        <span className="text-muted-foreground">{message} for </span>
        <span className="font-medium text-foreground">"{query}"</span>
      </div>
    </div>
  );
}
