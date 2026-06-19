import { useState, useRef, useEffect, useCallback } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Search, Loader2, ArrowUp, ArrowDown, CornerDownLeft, AlertCircle } from "lucide-react";
import { Input } from "@/components/ui/input";
import { fetchSuggestions, submitSearch } from "@/api/typeaheadApi";
import { useDebounce } from "@/hooks/useDebounce";
import type { Suggestion } from "@/types";

interface SearchBarProps {
  onSearchResult?: (message: string, query: string) => void;
  onPrefixChange?: (prefix: string) => void;
}

export function SearchBar({ onSearchResult, onPrefixChange }: SearchBarProps) {
  const [inputValue, setInputValue] = useState("");
  const [selectedIndex, setSelectedIndex] = useState(-1);
  const [isOpen, setIsOpen] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLUListElement>(null);
  const queryClient = useQueryClient();
  const debouncedValue = useDebounce(inputValue, 300);

  const { data, isFetching, isError } = useQuery({
    queryKey: ["suggestions", debouncedValue],
    queryFn: () => fetchSuggestions(debouncedValue),
    enabled: debouncedValue.trim().length > 0,
    staleTime: 30_000,
  });

  const suggestions = data?.suggestions ?? [];
  const latencyMs = data?.latencyMs;

  const searchMutation = useMutation({
    mutationFn: submitSearch,
    onSuccess: (data) => {
      onSearchResult?.(data.message, data.query);
      queryClient.invalidateQueries({ queryKey: ["trending"] });
    },
  });

  const handleSubmit = useCallback(
    (query: string) => {
      if (!query.trim()) return;
      searchMutation.mutate(query.trim().toLowerCase());
      setIsOpen(false);
      setSelectedIndex(-1);
    },
    [searchMutation]
  );

  const handleSelect = useCallback(
    (suggestion: Suggestion) => {
      setInputValue(suggestion.query);
      handleSubmit(suggestion.query);
    },
    [handleSubmit]
  );

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (!isOpen || suggestions.length === 0) {
      if (e.key === "Enter") {
        handleSubmit(inputValue);
      }
      return;
    }

    switch (e.key) {
      case "ArrowDown":
        e.preventDefault();
        setSelectedIndex((prev) =>
          prev < suggestions.length - 1 ? prev + 1 : 0
        );
        break;
      case "ArrowUp":
        e.preventDefault();
        setSelectedIndex((prev) =>
          prev > 0 ? prev - 1 : suggestions.length - 1
        );
        break;
      case "Enter":
        e.preventDefault();
        if (selectedIndex >= 0) {
          handleSelect(suggestions[selectedIndex]);
        } else {
          handleSubmit(inputValue);
        }
        break;
      case "Escape":
        setIsOpen(false);
        setSelectedIndex(-1);
        break;
    }
  };

  useEffect(() => {
    if (debouncedValue.trim().length > 0 && suggestions.length > 0) {
      setIsOpen(true);
    } else {
      setIsOpen(false);
    }
    setSelectedIndex(-1);
    onPrefixChange?.(debouncedValue.trim().toLowerCase());
  }, [debouncedValue, suggestions.length, onPrefixChange]);

  useEffect(() => {
    if (selectedIndex >= 0 && listRef.current) {
      const item = listRef.current.children[selectedIndex] as HTMLElement;
      item?.scrollIntoView({ block: "nearest" });
    }
  }, [selectedIndex]);

  return (
    <div className="relative w-full max-w-2xl mx-auto">
      <div className="relative">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
        <Input
          ref={inputRef}
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          onKeyDown={handleKeyDown}
          onFocus={() => {
            if (suggestions.length > 0) setIsOpen(true);
          }}
          onBlur={() => {
            setTimeout(() => setIsOpen(false), 200);
          }}
          placeholder="Search anything..."
          className="pl-10 pr-10 h-12 text-base rounded-xl border-border/50 bg-background/80 backdrop-blur-sm focus-visible:ring-primary/30"
        />
        {isFetching && (
          <Loader2 className="absolute right-3 top-1/2 -translate-y-1/2 h-4 w-4 animate-spin text-muted-foreground" />
        )}
      </div>

      {isError && debouncedValue.trim().length > 0 && (
        <div className="flex items-center gap-2 mt-2 px-3 py-2 rounded-lg bg-destructive/10 text-destructive text-sm">
          <AlertCircle className="h-4 w-4 shrink-0" />
          Failed to fetch suggestions. Please try again.
        </div>
      )}

      {isOpen && suggestions.length > 0 && (
        <div className="absolute z-50 w-full mt-1 rounded-xl border border-border/50 bg-popover/95 backdrop-blur-md shadow-xl overflow-hidden">
          <ul ref={listRef} className="py-1 max-h-80 overflow-y-auto">
            {suggestions.map((suggestion, index) => (
              <li
                key={suggestion.query}
                onMouseDown={() => handleSelect(suggestion)}
                onMouseEnter={() => setSelectedIndex(index)}
                className={`flex items-center justify-between px-4 py-2.5 cursor-pointer transition-colors ${
                  index === selectedIndex
                    ? "bg-accent text-accent-foreground"
                    : "hover:bg-accent/50"
                }`}
              >
                <div className="flex items-center gap-3">
                  <Search className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
                  <span className="text-sm">{suggestion.query}</span>
                </div>
                <span className="text-xs text-muted-foreground tabular-nums">
                  {suggestion.score.toLocaleString()}
                </span>
              </li>
            ))}
          </ul>
          <div className="flex items-center justify-between px-4 py-2 border-t border-border/50 text-xs text-muted-foreground">
            <div className="flex items-center gap-3">
              <span className="flex items-center gap-1">
                <ArrowUp className="h-3 w-3" />
                <ArrowDown className="h-3 w-3" />
                navigate
              </span>
              <span className="flex items-center gap-1">
                <CornerDownLeft className="h-3 w-3" />
                select
              </span>
            </div>
            {latencyMs !== undefined && (
              <span>{latencyMs}ms</span>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
