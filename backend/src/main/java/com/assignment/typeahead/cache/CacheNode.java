package com.assignment.typeahead.cache;

import com.assignment.typeahead.dto.SuggestionResponse;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class CacheNode {
    private final String name;
    private final ConcurrentHashMap<String,CacheEntry> map = new ConcurrentHashMap<>();

    public CacheNode(String name) {
        this.name = name;
    }

    static class CacheEntry{
        private List<SuggestionResponse> data;
        private Instant expiresAt;

        public CacheEntry(List<SuggestionResponse> data, Instant expiresAt) {
            this.data = data;
            this.expiresAt = expiresAt;
        }

        public List<SuggestionResponse> getData() {
            return data;
        }

        public void setData(List<SuggestionResponse> data) {
            this.data = data;
        }

        public Instant getExpiresAt() {
            return expiresAt;
        }

        public void setExpiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
        }
    }

    public List<SuggestionResponse> get(String key){
        CacheEntry entry = map.get(key);
        if (entry == null) return null;
        if (Instant.now().isAfter(entry.getExpiresAt())) {
            map.remove(key);
            return null;
        }
        return entry.getData();
    }

    public void put(String key,List<SuggestionResponse> data,int ttlSeconds){
        CacheEntry entry = new CacheEntry(data,Instant.now().plusSeconds(ttlSeconds));
        map.put(key,entry);
    }

    public void invalidate(String key){
        map.remove(key);
    }

    public int size(){
        return map.size();
    }
}
