package com.nexgate.gateway;

import com.nexgate.admin.AdminApiHandler;
import com.nexgate.circuitbreaker.CircuitBreakerRegistry;
import com.nexgate.model.RouteConfig;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpServerBootstrap {
    private static final Logger log = LoggerFactory.getLogger(HttpServerBootstrap.class);
    private final int port;
    private final RequestRouter router;
    private final AdminApiHandler adminApi;

    public HttpServerBootstrap(int port, RequestRouter router, RouteConfig config, CircuitBreakerRegistry cbRegistry) {
        this.port = port;
        this.router = router;
        this.adminApi = new AdminApiHandler(config, cbRegistry);
    }

    public void start() {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
             .handler(new LoggingHandler(LogLevel.INFO))
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 protected void initChannel(SocketChannel ch) {
                     ch.pipeline().addLast(
                         new HttpServerCodec(),
                         new HttpObjectAggregator(10 * 1024 * 1024),
                          new ProxyFrontendHandler(router, adminApi)
                     );
                 }
             })
             .option(ChannelOption.SO_BACKLOG, 128)
             .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture f = b.bind(port).sync();
            log.info("NexGate listening on port {}", port);
            f.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Server interrupted", e);
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
