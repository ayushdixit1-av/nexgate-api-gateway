package com.nexgate;

import com.nexgate.circuitbreaker.CircuitBreaker;
import com.nexgate.ratelimit.RateLimiter;
import com.nexgate.gateway.LoadBalancer;
import com.nexgate.model.UpstreamService;
import com.nexgate.model.Route;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

class NexGateTests {

    @Test
    void testCircuitBreakerStartsClosed() {
        CircuitBreaker cb = new CircuitBreaker("test", 3, 2, 1000);
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        assertTrue(cb.tryAcquire());
    }

    @Test
    void testCircuitBreakerOpensAfterFailures() {
        CircuitBreaker cb = new CircuitBreaker("test", 3, 2, 60000);
        assertTrue(cb.tryAcquire());
        cb.recordFailure();
        cb.recordFailure();
        cb.recordFailure();
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        assertFalse(cb.tryAcquire());
    }

    @Test
    void testCircuitBreakerHalfOpenAfterTimeout() throws Exception {
        CircuitBreaker cb = new CircuitBreaker("test", 3, 2, 100);
        cb.recordFailure();
        cb.recordFailure();
        cb.recordFailure();
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        Thread.sleep(150);
        assertTrue(cb.tryAcquire());
        assertEquals(CircuitBreaker.State.HALF_OPEN, cb.getState());
    }

    @Test
    void testCircuitBreakerClosesAfterSuccessThreshold() {
        CircuitBreaker cb = new CircuitBreaker("test", 3, 2, 50);
        cb.recordFailure();
        cb.recordFailure();
        cb.recordFailure();
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        try { Thread.sleep(100); } catch (InterruptedException e) {}
        assertTrue(cb.tryAcquire());
        cb.recordSuccess();
        cb.recordSuccess();
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
    }

    @Test
    void testRoundRobin() {
        LoadBalancer lb = new LoadBalancer();
        UpstreamService u1 = new UpstreamService(); u1.setHost("h1"); u1.setPort(1);
        UpstreamService u2 = new UpstreamService(); u2.setHost("h2"); u2.setPort(2);
        List<UpstreamService> upstreams = List.of(u1, u2);

        assertEquals("h1", lb.roundRobin(upstreams).getHost());
        assertEquals("h2", lb.roundRobin(upstreams).getHost());
        assertEquals("h1", lb.roundRobin(upstreams).getHost());
    }

    @Test
    void testWeightedRoundRobin() {
        LoadBalancer lb = new LoadBalancer();
        UpstreamService u1 = new UpstreamService(); u1.setHost("h1"); u1.setWeight(3);
        UpstreamService u2 = new UpstreamService(); u2.setHost("h2"); u2.setWeight(1);
        List<UpstreamService> upstreams = List.of(u1, u2);

        int h1 = 0, h2 = 0;
        for (int i = 0; i < 400; i++) {
            if (lb.weightedRoundRobin(upstreams).getHost().equals("h1")) h1++; else h2++;
        }
        assertEquals(300, h1);
        assertEquals(100, h2);
    }

    @Test
    void testRoutePatternMatch() {
        Route route = new Route();
        route.setPath("/api/users/{id}");
        String pattern = route.getPath().replaceAll("\\{[^}]+\\}", "[^/]+");
        assertTrue("/api/users/123".matches(pattern));
        assertTrue("/api/users/abc".matches(pattern));
        assertFalse("/api/users/123/profile".matches(pattern));
    }

    @Test
    void testRouteBatchMatch() {
        String pattern = "/api/orders/{orderId}/items/{itemId}"
            .replaceAll("\\{[^}]+\\}", "[^/]+");
        assertTrue("/api/orders/42/items/5".matches(pattern));
        assertFalse("/api/orders/42".matches(pattern));
    }
}
