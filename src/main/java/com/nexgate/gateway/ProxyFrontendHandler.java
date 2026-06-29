package com.nexgate.gateway;

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

    public ProxyFrontendHandler(RequestRouter router) {
        this.router = router;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        router.route(ctx, request);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Handler error", cause);
        recordResult(ctx, false);
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Long responseTime = ctx.channel().attr(UPSTREAM_RESPONSE_TIME).get();
        if (responseTime != null && responseTime < 0) {
            recordResult(ctx, false);
        }
        ctx.fireChannelInactive();
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
