package com.nexgate.admin;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Map;

public class StaticFileHandler {
    private static final Logger log = LoggerFactory.getLogger(StaticFileHandler.class);
    private static final Map<String, String> MIME_TYPES = Map.of(
        "html", "text/html; charset=UTF-8",
        "js", "application/javascript",
        "css", "text/css",
        "png", "image/png",
        "svg", "image/svg+xml",
        "ico", "image/x-icon",
        "json", "application/json"
    );

    private final String prefix;

    public StaticFileHandler(String prefix) {
        this.prefix = prefix;
    }

    public void handle(ChannelHandlerContext ctx, FullHttpRequest request) {
        String uri = request.uri();
        String path = uri.substring(prefix.length());
        if (path.isEmpty() || path.equals("/")) {
            path = "/index.html";
        }
        String resourcePath = "static/admin" + path;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                sendError(ctx, HttpResponseStatus.NOT_FOUND, "Not found: " + path);
                return;
            }
            byte[] content = in.readAllBytes();
            String ext = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : "html";
            String contentType = MIME_TYPES.getOrDefault(ext, "application/octet-stream");
            FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.wrappedBuffer(content)
            );
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
            response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
            ctx.writeAndFlush(response);
        } catch (Exception e) {
            log.error("Static file error", e);
            sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, "Internal error");
        }
    }

    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status, String message) {
        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, status,
            Unpooled.wrappedBuffer(message.getBytes(CharsetUtil.UTF_8))
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        ctx.writeAndFlush(response);
    }
}
