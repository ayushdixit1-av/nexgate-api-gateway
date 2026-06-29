package com.nexgate.gateway;

import com.nexgate.model.Route;
import com.nexgate.model.RouteConfig;
import com.nexgate.model.UpstreamService;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.TimeUnit;

public class ReverseProxyHandler {
    private static final Logger log = LoggerFactory.getLogger(ReverseProxyHandler.class);
    private static final int REQUEST_TIMEOUT_MS = 30000;

    public void proxy(ChannelHandlerContext ctx, FullHttpRequest request, Route route, UpstreamService upstream, long startNanos) {
        String host = ctx.channel().attr(ProxyFrontendHandler.UPSTREAM_HOST).get();
        int port = ctx.channel().attr(ProxyFrontendHandler.UPSTREAM_PORT).get();

        URI uri = URI.create(request.uri());
        String rewrittenPath = route.getPath().replaceAll("\\{[^}]+\\}", "([^/]+)");
        String upstreamPath = rewritePath(uri.getPath(), route.getPath(), route.getUpstreams().get(0).getHost());

        FullHttpRequest proxiedRequest = new DefaultFullHttpRequest(
            request.protocolVersion(),
            request.method(),
            upstreamPath,
            request.content().retainedDuplicate(),
            request.headers().copy(),
            EmptyHttpHeaders.INSTANCE
        );
        proxiedRequest.headers().set(HttpHeaderNames.HOST, host + ":" + port);
        proxiedRequest.headers().set("X-Forwarded-For", ctx.channel().remoteAddress().toString());
        proxiedRequest.headers().set("X-Forwarded-Proto", "http");

        EventLoopGroup group = new io.netty.channel.nio.NioEventLoopGroup(1);
        Bootstrap b = new Bootstrap();
        b.group(group)
         .channel(NioSocketChannel.class)
         .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
         .handler(new ChannelInitializer<io.netty.channel.socket.SocketChannel>() {
             @Override
             protected void initChannel(io.netty.channel.socket.SocketChannel ch) {
                 ch.pipeline().addLast(
                     new ReadTimeoutHandler(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS),
                     new HttpClientCodec(),
                     new HttpObjectAggregator(10 * 1024 * 1024),
                     new SimpleChannelInboundHandler<FullHttpResponse>() {
                         @Override
                         protected void channelRead0(ChannelHandlerContext upstreamCtx, FullHttpResponse response) {
                             ctx.channel().attr(ProxyFrontendHandler.UPSTREAM_RESPONSE_TIME).set(
                                 System.nanoTime() - startNanos);
                             FullHttpResponse clientResponse = new DefaultFullHttpResponse(
                                 response.protocolVersion(),
                                 response.status(),
                                 response.content().retainedDuplicate(),
                                 response.headers().copy(),
                                 EmptyHttpHeaders.INSTANCE
                             );
                             clientResponse.headers().set("X-NexGate-Upstream", host + ":" + port);
                             clientResponse.headers().set("X-NexGate-Latency", 
                                 TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos) + "ms");
                             ctx.writeAndFlush(clientResponse);
                             upstreamCtx.close();
                         }

                         @Override
                         public void exceptionCaught(ChannelHandlerContext upstreamCtx, Throwable cause) {
                             log.error("Upstream error for {}:{}", host, port, cause);
                             ctx.channel().attr(ProxyFrontendHandler.UPSTREAM_RESPONSE_TIME).set(-1L);
                             FullHttpResponse errorResp = new DefaultFullHttpResponse(
                                 HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_GATEWAY,
                                 Unpooled.wrappedBuffer(("{\"error\":\"Upstream unavailable: " + host + ":" + port + "\"}").getBytes())
                             );
                             errorResp.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
                             ctx.writeAndFlush(errorResp);
                             upstreamCtx.close();
                         }
                     }
                 );
             }
         });

        ChannelFuture cf = b.connect(host, port);
        cf.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                future.channel().writeAndFlush(proxiedRequest);
            } else {
                log.warn("Failed to connect to upstream {}:{}", host, port);
                ctx.channel().attr(ProxyFrontendHandler.UPSTREAM_RESPONSE_TIME).set(-1L);
                FullHttpResponse errorResp = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.SERVICE_UNAVAILABLE,
                    Unpooled.wrappedBuffer("{\"error\":\"Service unavailable\"}".getBytes())
                );
                errorResp.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
                ctx.writeAndFlush(errorResp);
            }
        });
    }

    private String rewritePath(String requestPath, String routePattern, String upstreamHost) {
        return requestPath;
    }
}
