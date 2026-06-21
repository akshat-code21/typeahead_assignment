package com.assignment.typeahead.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Component
public class ConsistentHashRing {

    private static final Logger log = LoggerFactory.getLogger(ConsistentHashRing.class);
    private static final int NUM_NODES = 3;
    private static final int VIRTUAL_NODES_PER_NODE = 150;
    private final TreeMap<Integer, String> ring = new TreeMap<>();
    private final List<String> nodeNames = new ArrayList<>();

    @PostConstruct
    public void init() {
        for (int i = 0; i < NUM_NODES; i++) {
            String nodeName = "cache-node-" + i;
            nodeNames.add(nodeName);
            addNode(nodeName);
        }
        log.info("Consistent hash ring initialised with {} nodes ({} virtual nodes each, {} total positions)",
                NUM_NODES, VIRTUAL_NODES_PER_NODE, ring.size());
    }

    public String getNode(String key) {
        if (ring.isEmpty()) {
            throw new IllegalStateException("Hash ring is empty");
        }
        int hash = hash(key);
        Map.Entry<Integer, String> entry = ring.ceilingEntry(hash);
        if (entry == null) {
            entry = ring.firstEntry();
        }
        return entry.getValue();
    }


    public String routedKey(String baseKey) {
        String node = getNode(baseKey);
        return node + ":" + baseKey;
    }

    public int getNodeCount() {
        return nodeNames.size();
    }

    public List<String> getNodeNames() {
        return Collections.unmodifiableList(nodeNames);
    }

    public int getRingSize() {
        return ring.size();
    }

    public Map<String, Object> getRingInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("totalNodes", NUM_NODES);
        info.put("virtualNodesPerNode", VIRTUAL_NODES_PER_NODE);
        info.put("totalRingPositions", ring.size());
        info.put("nodeNames", nodeNames);
        return info;
    }

    private void addNode(String nodeName) {
        for (int v = 0; v < VIRTUAL_NODES_PER_NODE; v++) {
            String virtualLabel = nodeName + "#VN" + v;
            int position = hash(virtualLabel);
            ring.put(position, nodeName);
        }
        log.debug("Added node '{}' with {} virtual nodes", nodeName, VIRTUAL_NODES_PER_NODE);
    }

    static int hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            return ((digest[0] & 0xFF) << 24)
                    | ((digest[1] & 0xFF) << 16)
                    | ((digest[2] & 0xFF) << 8)
                    | (digest[3] & 0xFF);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }
}
