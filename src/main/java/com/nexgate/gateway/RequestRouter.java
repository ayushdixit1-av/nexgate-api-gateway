package com.nexgate.gateway;

import com.nexgate.admin.MetricsCollector;
import com.nexgate.circuitbreaker.CircuitBreakerRegistry;
import com.nexgate.middleware.AuthMiddleware;
import com.nexgate.model.Route;
import com.nexgate.model.RouteConfig;
import com.nexgate.model.UpstreamService;
import com.nexgate.ratelimit.RateLimiter;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RequestRouter {
    private static final Logger log = LoggerFactory.getLogger(RequestRouter.class);
    private final RouteConfig config;
    private final RateLimiter rateLimiter;
    private final AuthMiddleware authMiddleware;
    private final CircuitBreakerRegistry cbRegistry;
    private final ReverseProxyHandler proxyHandler;
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);
    private final MetricsCollector metrics;

    public RequestRouter(RouteConfig config, RateLimiter rateLimiter, 
                         AuthMiddleware authMiddleware, CircuitBreakerRegistry cbRegistry) {
        this.config = config;
        this.rateLimiter = rateLimiter;
        this.authMiddleware = authMiddleware;
        this.cbRegistry = cbRegistry;
        this.proxyHandler = new ReverseProxyHandler();
        this.metrics = MetricsCollector.getInstance();
    }

    public void route(ChannelHandlerContext ctx, FullHttpRequest request) {
        long startNanos = System.nanoTime();
        String path = request.uri();
        HttpMethod method = request.method();
        String apiKey = extractApiKey(request);

        Route matched = matchRoute(path, method);
        if (matched == null) {
            metrics.recordError("unknown");
            sendJson(ctx, HttpResponseStatus.NOT_FOUND, "{\"error\":\"No route matches " + path + "\"}");
            return;
        }
        metrics.recordRequest(matched.getId());

        if (matched.getAuth() != null && !matched.getAuth().isEmpty()) {
            String authResult = authMiddleware.authenticate(request, matched.getAuth());
            if (authResult != null) {
                metrics.recordError(matched.getId());
                sendJson(ctx, HttpResponseStatus.UNAUTHORIZED, "{\"error\":\"" + authResult + "\"}");
                return;
            }
        }

        if (matched.getRateLimit() != null && matched.getRateLimit() > 0 && apiKey != null) {
            String limitKey = "ratelimit:" + matched.getId() + ":" + apiKey;
            int maxTokens = matched.getRateLimit();
            long windowMs = parseDuration(matched.getRateLimitDuration());
            if (!rateLimiter.tryAcquire(limitKey, maxTokens, windowMs)) {
                metrics.recordError(matched.getId());
                sendJson(ctx, HttpResponseStatus.TOO_MANY_REQUESTS, 
                    "{\"error\":\"Rate limit exceeded\",\"limit\":" + maxTokens + "}");
                return;
            }
        }

        List<UpstreamService> upstreams = matched.getUpstreams();
        if (upstreams == null || upstreams.isEmpty()) {
            sendJson(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE, "{\"error\":\"No upstreams configured\"}");
            return;
        }

        com.nexgate.circuitbreaker.CircuitBreaker cb = cbRegistry.getOrCreate(matched.getId(), matched.getCircuitBreaker());
        if (!cb.tryAcquire()) {
            metrics.recordError(matched.getId());
            sendJson(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE, 
                "{\"error\":\"Circuit breaker open for " + matched.getId() + "\"}");
            return;
        }

        UpstreamService upstream = selectUpstream(upstreams);
        if (upstream == null) {
            sendJson(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE, "{\"error\":\"No healthy upstreams\"}");
            return;
        }

        ctx.channel().attr(ProxyFrontendHandler.UPSTREAM_HOST).set(upstream.getHost());
        ctx.channel().attr(ProxyFrontendHandler.UPSTREAM_PORT).set(upstream.getPort());
        ctx.channel().attr(ProxyFrontendHandler.CIRCUIT_BREAKER_KEY).set(matched.getId());
        ctx.channel().attr(ProxyFrontendHandler.UPSTREAM_RESPONSE_TIME).set(0L);

        proxyHandler.proxy(ctx, request, matched, upstream, startNanos);
    }

    private Route matchRoute(String path, HttpMethod method) {
        if (config.getRoutes() == null) return null;
        String normalizedPath = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;
        for (Route route : config.getRoutes()) {
            String pattern = route.getPath();
            if (pattern.contains("{") && pattern.contains("}")) {
                String regex = pattern.replaceAll("\\{[^}]+\\}", "[^/]+");
                if (normalizedPath.matches(regex) && methodMatches(route.getMethod(), method)) {
                    return route;
                }
            } else if (normalizedPath.equals(pattern) && methodMatches(route.getMethod(), method)) {
                return route;
            }
        }
        return null;
    }

    private boolean methodMatches(String configured, HttpMethod actual) {
        if (configured == null || configured.equalsIgnoreCase("ANY")) return true;
        return configured.equalsIgnoreCase(actual.name());
    }

    private UpstreamService selectUpstream(List<UpstreamService> upstreams) {
        if (upstreams.size() == 1) return upstreams.get(0);
        int totalWeight = upstreams.stream().mapToInt(UpstreamService::getWeight).sum();
        if (totalWeight == 1 || upstreams.size() == 1) {
            return upstreams.get(roundRobinIndex.getAndIncrement() % upstreams.size());
        }
        int idx = roundRobinIndex.getAndIncrement() % totalWeight;
        int cumulative = 0;
        for (UpstreamService u : upstreams) {
            cumulative += u.getWeight();
            if (idx < cumulative) return u;
        }
        return upstreams.get(0);
    }

    private String extractApiKey(FullHttpRequest request) {
        String key = request.headers().get("X-API-Key");
        if (key != null) return key;
        String auth = request.headers().get("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) return auth.substring(7);
        return null;
    }

    private long parseDuration(String duration) {
        if (duration == null) return 1000L;
        duration = duration.toLowerCase().trim();
        if (duration.endsWith("s")) return Long.parseLong(duration.replace("s", "")) * 1000;
        if (duration.endsWith("m")) return Long.parseLong(duration.replace("m", "")) * 60000;
        if (duration.endsWith("h")) return Long.parseLong(duration.replace("h", "")) * 3600000;
        return Long.parseLong(duration);
    }

    private void sendJson(ChannelHandlerContext ctx, HttpResponseStatus status, String body) {
        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, status,
            Unpooled.wrappedBuffer(body.getBytes())
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        ctx.writeAndFlush(response);
    }
}
