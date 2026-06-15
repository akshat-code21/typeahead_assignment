import { useState } from "react";
import { SearchBar } from "@/components/SearchBar";
import { SearchResult } from "@/components/SearchResult";
import { TrendingSearches } from "@/components/TrendingSearches";
import { StatsPanel } from "@/components/StatsPanel";
import { Search } from "lucide-react";

export function App() {
  const [searchResult, setSearchResult] = useState<{
    message: string;
    query: string;
  } | null>(null);

  return (
    <div className="min-h-svh bg-background">
      {/* Header */}
      <div className="flex flex-col items-center justify-center pt-24 pb-8 px-4">
        <div className="flex items-center gap-3 mb-2">
          <div className="rounded-xl bg-primary/10 p-2.5">
            <Search className="h-6 w-6 text-primary" />
          </div>
          <h1 className="text-3xl font-bold tracking-tight">Typeahead</h1>
        </div>
        <p className="text-sm text-muted-foreground">
          Search with real-time suggestions
        </p>
      </div>

      {/* Search area */}
      <div className="max-w-2xl mx-auto px-4 space-y-6">
        <SearchBar
          onSearchResult={(message, query) =>
            setSearchResult({ message, query })
          }
        />

        {searchResult && (
          <SearchResult
            message={searchResult.message}
            query={searchResult.query}
          />
        )}

        <TrendingSearches />
      </div>

      {/* Stats */}
      <div className="max-w-4xl mx-auto px-4 mt-16">
        <h2 className="text-sm font-medium text-muted-foreground mb-3">
          System Metrics
        </h2>
        <StatsPanel />
      </div>
    </div>
  );
}

export default App;
