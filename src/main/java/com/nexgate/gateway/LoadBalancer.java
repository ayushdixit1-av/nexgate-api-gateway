package com.nexgate.gateway;

import com.nexgate.model.UpstreamService;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadBalancer {
    private final AtomicInteger counter = new AtomicInteger(0);

    public UpstreamService roundRobin(List<UpstreamService> upstreams) {
        if (upstreams == null || upstreams.isEmpty()) return null;
        return upstreams.get(Math.abs(counter.getAndIncrement() % upstreams.size()));
    }

    public UpstreamService weightedRoundRobin(List<UpstreamService> upstreams) {
        if (upstreams == null || upstreams.isEmpty()) return null;
        if (upstreams.size() == 1) return upstreams.get(0);
        int totalWeight = upstreams.stream().mapToInt(UpstreamService::getWeight).sum();
        int idx = Math.abs(counter.getAndIncrement() % totalWeight);
        int cumulative = 0;
        for (UpstreamService u : upstreams) {
            cumulative += u.getWeight();
            if (idx < cumulative) return u;
        }
        return upstreams.get(0);
    }
}
