package com.nexgate.admin;

import com.nexgate.circuitbreaker.CircuitBreaker;
import com.nexgate.circuitbreaker.CircuitBreakerRegistry;
import com.nexgate.model.Route;
import com.nexgate.model.RouteConfig;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class AdminApiHandler {
    private static final Logger log = LoggerFactory.getLogger(AdminApiHandler.class);
    private final RouteConfig config;
    private final CircuitBreakerRegistry cbRegistry;
    private final MetricsCollector metrics;

    public AdminApiHandler(RouteConfig config, CircuitBreakerRegistry cbRegistry) {
        this.config = config;
        this.cbRegistry = cbRegistry;
        this.metrics = MetricsCollector.getInstance();
    }

    public void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = request.uri();
        try {
            if (path.equals("/api/admin/health") || path.equals("/api/admin/health/")) {
                handleHealth(ctx);
            } else if (path.equals("/api/admin/routes") || path.equals("/api/admin/routes/")) {
                handleRoutes(ctx);
            } else if (path.equals("/api/admin/circuit-breakers") || path.equals("/api/admin/circuit-breakers/")) {
                handleCircuitBreakers(ctx);
            } else {
                sendJson(ctx, HttpResponseStatus.NOT_FOUND, "{\"error\":\"Unknown admin endpoint\"}");
            }
        } catch (Exception e) {
            log.error("Admin API error", e);
            sendJson(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "{\"error\":\"Internal error\"}");
        }
    }

    private void handleHealth(ChannelHandlerContext ctx) {
        long uptime = metrics.getUptime();
        long uptimeSec = uptime / 1000;
        String json = String.format("""
            {"status":"UP","uptime":"%d:%02d:%02d","uptimeMs":%d,"startTime":%d,"totalRequests":%d,"activeConnections":%d,"routesConfigured":%d}
            """.stripIndent().replace("\n", "").replace(" ", ""),
            uptimeSec / 3600, (uptimeSec % 3600) / 60, uptimeSec % 60,
            uptime, metrics.getStartTime(),
            metrics.getTotalRequests(), metrics.getActiveConnections(),
            config.getRoutes() != null ? config.getRoutes().size() : 0);
        sendJson(ctx, HttpResponseStatus.OK, json);
    }

    private void handleRoutes(ChannelHandlerContext ctx) {
        List<Route> routes = config.getRoutes();
        if (routes == null || routes.isEmpty()) {
            sendJson(ctx, HttpResponseStatus.OK, "{\"routes\":[]}");
            return;
        }
        StringBuilder sb = new StringBuilder("{\"routes\":[");
        for (int i = 0; i < routes.size(); i++) {
            Route r = routes.get(i);
            if (i > 0) sb.append(",");
            sb.append("{");
            sb.append("\"id\":\"").append(escape(r.getId())).append("\",");
            sb.append("\"path\":\"").append(escape(r.getPath())).append("\",");
            sb.append("\"method\":\"").append(escape(r.getMethod())).append("\",");
            sb.append("\"rateLimit\":").append(r.getRateLimit() != null ? r.getRateLimit() : 0).append(",");
            sb.append("\"rateLimitDuration\":\"").append(escape(r.getRateLimitDuration())).append("\",");
            sb.append("\"auth\":").append(r.getAuth() != null ? toJsonArray(r.getAuth()) : "[]").append(",");
            sb.append("\"requests\":").append(metrics.getRouteRequests(r.getId())).append(",");
            sb.append("\"errors\":").append(metrics.getRouteErrors(r.getId())).append(",");
            sb.append("\"upstreams\":[");
            List<com.nexgate.model.UpstreamService> upstreams = r.getUpstreams();
            if (upstreams != null) {
                for (int j = 0; j < upstreams.size(); j++) {
                    if (j > 0) sb.append(",");
                    sb.append("{\"host\":\"").append(upstreams.get(j).getHost()).append("\",");
                    sb.append("\"port\":").append(upstreams.get(j).getPort()).append(",");
                    sb.append("\"weight\":").append(upstreams.get(j).getWeight()).append("}");
                }
            }
            sb.append("]");
            sb.append("}");
        }
        sb.append("]}");
        sendJson(ctx, HttpResponseStatus.OK, sb.toString());
    }

    private void handleCircuitBreakers(ChannelHandlerContext ctx) {
        List<Route> routes = config.getRoutes();
        StringBuilder sb = new StringBuilder("{\"circuitBreakers\":[");
        if (routes != null) {
            boolean first = true;
            for (Route r : routes) {
                CircuitBreaker cb = cbRegistry.get(r.getId());
                if (cb != null) {
                    if (!first) sb.append(",");
                    first = false;
                    sb.append("{");
                    sb.append("\"name\":\"").append(escape(cb.getName())).append("\",");
                    sb.append("\"state\":\"").append(cb.getState()).append("\",");
                    sb.append("\"failureCount\":").append(cb.getFailureCount());
                    sb.append("}");
                }
            }
        }
        sb.append("]}");
        sendJson(ctx, HttpResponseStatus.OK, sb.toString());
    }

    private String toJsonArray(List<String> items) {
        if (items == null || items.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escape(items.get(i))).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private void sendJson(ChannelHandlerContext ctx, HttpResponseStatus status, String body) {
        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, status,
            Unpooled.wrappedBuffer(body.getBytes())
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        ctx.writeAndFlush(response);
    }
}
