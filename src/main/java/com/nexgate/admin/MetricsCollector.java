package com.nexgate.admin;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MetricsCollector {
    private static final MetricsCollector INSTANCE = new MetricsCollector();
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong activeConnections = new AtomicLong(0);
    private final ConcurrentHashMap<String, AtomicLong> routeRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> routeErrors = new ConcurrentHashMap<>();
    private final long startTime = System.currentTimeMillis();

    public static MetricsCollector getInstance() { return INSTANCE; }

    public void recordRequest(String routeId) {
        totalRequests.incrementAndGet();
        routeRequests.computeIfAbsent(routeId, k -> new AtomicLong(0)).incrementAndGet();
    }

    public void recordError(String routeId) {
        routeErrors.computeIfAbsent(routeId, k -> new AtomicLong(0)).incrementAndGet();
    }

    public void incrementActive() { activeConnections.incrementAndGet(); }
    public void decrementActive() { activeConnections.decrementAndGet(); }

    public long getTotalRequests() { return totalRequests.get(); }
    public long getActiveConnections() { return activeConnections.get(); }
    public long getUptime() { return System.currentTimeMillis() - startTime; }
    public long getStartTime() { return startTime; }
    public long getRouteRequests(String routeId) {
        AtomicLong v = routeRequests.get(routeId);
        return v == null ? 0 : v.get();
    }
    public long getRouteErrors(String routeId) {
        AtomicLong v = routeErrors.get(routeId);
        return v == null ? 0 : v.get();
    }
}
