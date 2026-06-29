package com.nexgate.gateway;

import com.nexgate.admin.AdminApiHandler;
import com.nexgate.admin.MetricsCollector;
import com.nexgate.admin.StaticFileHandler;
import com.nexgate.circuitbreaker.CircuitBreaker;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyFrontendHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Logger log = LoggerFactory.getLogger(ProxyFrontendHandler.class);

    public static final AttributeKey<String> UPSTREAM_HOST = AttributeKey.valueOf("upstreamHost");
    public static final AttributeKey<Integer> UPSTREAM_PORT = AttributeKey.valueOf("upstreamPort");
    public static final AttributeKey<String> CIRCUIT_BREAKER_KEY = AttributeKey.valueOf("cbKey");
    public static final AttributeKey<Long> UPSTREAM_RESPONSE_TIME = AttributeKey.valueOf("upstreamResponseTime");

    private final RequestRouter router;
    private final AdminApiHandler adminApi;
    private final StaticFileHandler staticFile;
    private final MetricsCollector metrics;

    public ProxyFrontendHandler(RequestRouter router, AdminApiHandler adminApi) {
        this.router = router;
        this.adminApi = adminApi;
        this.staticFile = new StaticFileHandler("/admin");
        this.metrics = MetricsCollector.getInstance();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        metrics.incrementActive();
        String uri = request.uri();
        if (uri.startsWith("/api/admin/")) {
            adminApi.handle(ctx, request);
            metrics.decrementActive();
        } else if (uri.equals("/admin") || uri.startsWith("/admin/")) {
            staticFile.handle(ctx, request);
            metrics.decrementActive();
        } else {
            router.route(ctx, request);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        metrics.decrementActive();
        Long responseTime = ctx.channel().attr(UPSTREAM_RESPONSE_TIME).get();
        if (responseTime != null && responseTime < 0) {
            recordResult(ctx, false);
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Handler error", cause);
        recordResult(ctx, false);
        ctx.close();
    }

    private void recordResult(ChannelHandlerContext ctx, boolean success) {
        String cbKey = ctx.channel().attr(CIRCUIT_BREAKER_KEY).get();
        if (cbKey != null) {
            CircuitBreaker cb = com.nexgate.circuitbreaker.CircuitBreakerRegistry.getInstance().get(cbKey);
            if (cb != null) {
                if (success) cb.recordSuccess(); else cb.recordFailure();
            }
        }
    }
}
