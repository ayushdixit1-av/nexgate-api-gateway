package com.nexgate.circuitbreaker;

import com.nexgate.model.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

public class CircuitBreakerRegistry {
    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerRegistry.class);
    private static final CircuitBreakerRegistry INSTANCE = new CircuitBreakerRegistry();
    private final ConcurrentHashMap<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();

    public static CircuitBreakerRegistry getInstance() { return INSTANCE; }

    public CircuitBreaker getOrCreate(String routeId, Route.CircuitBreakerConfig config) {
        return breakers.computeIfAbsent(routeId, id -> {
            int failureThreshold = config != null ? config.getFailureThreshold() : 5;
            int successThreshold = config != null ? config.getSuccessThreshold() : 3;
            int timeoutMs = config != null ? config.getTimeoutMs() : 30000;
            log.info("Creating circuit breaker '{}' (fail={}, success={}, timeout={}ms)",
                id, failureThreshold, successThreshold, timeoutMs);
            return new CircuitBreaker(id, failureThreshold, successThreshold, timeoutMs);
        });
    }

    public CircuitBreaker get(String routeId) {
        return breakers.get(routeId);
    }

    public int size() { return breakers.size(); }
}
