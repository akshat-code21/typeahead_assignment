package com.assignment.typeahead.cache;

import com.assignment.typeahead.dto.CacheDebugResponse;
import com.assignment.typeahead.dto.SuggestionResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConsistentHashRouter<T> {
    private final List<String> nodes;
    private final int virtualNodes;
    private final ConsistentHashRing ring;
    private final Map<String, CacheNode> map = new HashMap<>();

    public ConsistentHashRouter(List<String> nodes, int virtualNodes) {
        this.nodes = nodes;
        this.virtualNodes = virtualNodes;
        this.ring = new ConsistentHashRing(this.nodes, this.virtualNodes);
        for (String name : nodes) {
            map.put(name, new CacheNode(name));
        }
    }

    public List<SuggestionResponse> get(String prefix) {
        return routeToNode(prefix).get(prefix);
    }

    public void put(String prefix, List<SuggestionResponse> data,int ttlSeconds) {
        CacheNode node = routeToNode(prefix);
        node.put(prefix, data, ttlSeconds);
    }

    public void invalidate(String prefix) {
        CacheNode node = routeToNode(prefix);
        node.invalidate(prefix);
    }

    private CacheNode routeToNode(String prefix) {
        return map.get(ring.getNode(prefix));
    }

    public CacheDebugResponse getRoutingInfo(String prefix) {
        String nodeName = ring.getNode(prefix);
        CacheNode node = map.get(nodeName);
        List<SuggestionResponse> cached = node.get(prefix);
        String status = (cached != null) ? "HIT" : "MISS";
        return new CacheDebugResponse(prefix, nodeName, status, prefix);
    }
}
