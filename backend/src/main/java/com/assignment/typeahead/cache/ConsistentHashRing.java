package com.assignment.typeahead.cache;

import java.util.List;
import java.util.TreeMap;

public class ConsistentHashRing {
    private final List<String> nodeNames;
    private final int virtualNodes;
    private final TreeMap<Integer,String> map = new TreeMap<>();

    public ConsistentHashRing(List<String> nodeNames, int virtualNodes) {
        this.nodeNames = nodeNames;
        this.virtualNodes = virtualNodes;
        for (String node : nodeNames) {
            for (int i = 0; i < virtualNodes; i++) {
                map.put((node + "-v" + i).hashCode(), node);
            }
        }
    }
    public String getNode(String key) {
        Integer hash=key.hashCode();
        Integer higher=map.ceilingKey(hash);
        if(higher==null){
            higher=map.firstKey();
        }
        return map.get(higher);
    }

    public int getHashValue(String key){
        return key.hashCode();
    }

    public List<String> getNodeNames() {
        return nodeNames;
    }

    public int getVirtualNodes() {
        return virtualNodes;
    }

    public TreeMap<Integer, String> getMap() {
        return map;
    }
}
