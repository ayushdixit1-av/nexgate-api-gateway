package com.nexgate.model;

import java.util.List;

public class Route {
    private String id;
    private String path;
    private String method;
    private List<String> auth;
    private Integer rateLimit;
    private String rateLimitDuration;
    private CircuitBreakerConfig circuitBreaker;
    private List<UpstreamService> upstreams;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public List<String> getAuth() { return auth; }
    public void setAuth(List<String> auth) { this.auth = auth; }
    public Integer getRateLimit() { return rateLimit; }
    public void setRateLimit(Integer rateLimit) { this.rateLimit = rateLimit; }
    public String getRateLimitDuration() { return rateLimitDuration; }
    public void setRateLimitDuration(String rateLimitDuration) { this.rateLimitDuration = rateLimitDuration; }
    public CircuitBreakerConfig getCircuitBreaker() { return circuitBreaker; }
    public void setCircuitBreaker(CircuitBreakerConfig circuitBreaker) { this.circuitBreaker = circuitBreaker; }
    public List<UpstreamService> getUpstreams() { return upstreams; }
    public void setUpstreams(List<UpstreamService> upstreams) { this.upstreams = upstreams; }

    public static class CircuitBreakerConfig {
        private int failureThreshold = 5;
        private int successThreshold = 3;
        private int timeoutMs = 30000;

        public int getFailureThreshold() { return failureThreshold; }
        public void setFailureThreshold(int failureThreshold) { this.failureThreshold = failureThreshold; }
        public int getSuccessThreshold() { return successThreshold; }
        public void setSuccessThreshold(int successThreshold) { this.successThreshold = successThreshold; }
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
    }
}
