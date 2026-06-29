package com.nexgate.circuitbreaker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class CircuitBreaker {
    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final String name;
    private final int failureThreshold;
    private final int successThreshold;
    private final long timeoutMs;
    private final AtomicReference<State> state;
    private final AtomicInteger failureCount;
    private final AtomicInteger successCount;
    private final AtomicLong lastFailureTime;
    private final AtomicLong lastStateChange;

    public CircuitBreaker(String name, int failureThreshold, int successThreshold, long timeoutMs) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.successThreshold = successThreshold;
        this.timeoutMs = timeoutMs;
        this.state = new AtomicReference<>(State.CLOSED);
        this.failureCount = new AtomicInteger(0);
        this.successCount = new AtomicInteger(0);
        this.lastFailureTime = new AtomicLong(0);
        this.lastStateChange = new AtomicLong(System.currentTimeMillis());
    }

    public boolean tryAcquire() {
        State current = state.get();
        if (current == State.CLOSED) {
            return true;
        } else if (current == State.OPEN) {
            if (System.currentTimeMillis() - lastStateChange.get() >= timeoutMs) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    log.info("Circuit breaker '{}' moving to HALF_OPEN", name);
                    successCount.set(0);
                    return true;
                }
            }
            return false;
        } else {
            return true;
        }
    }

    public void recordSuccess() {
        State current = state.get();
        if (current == State.HALF_OPEN) {
            int successes = successCount.incrementAndGet();
            if (successes >= successThreshold) {
                if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                    log.info("Circuit breaker '{}' closing after {} successes", name, successes);
                    failureCount.set(0);
                    successCount.set(0);
                    lastStateChange.set(System.currentTimeMillis());
                }
            }
        } else if (current == State.CLOSED) {
            failureCount.set(0);
        }
    }

    public void recordFailure() {
        lastFailureTime.set(System.currentTimeMillis());
        State current = state.get();
        if (current == State.HALF_OPEN) {
            if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                log.warn("Circuit breaker '{}' opening from HALF_OPEN after failure", name);
                lastStateChange.set(System.currentTimeMillis());
            }
        } else if (current == State.CLOSED) {
            int failures = failureCount.incrementAndGet();
            if (failures >= failureThreshold) {
                if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                    log.warn("Circuit breaker '{}' OPEN after {} failures", name, failures);
                    lastStateChange.set(System.currentTimeMillis());
                }
            }
        }
    }

    public State getState() { return state.get(); }
    public int getFailureCount() { return failureCount.get(); }
    public String getName() { return name; }
}
