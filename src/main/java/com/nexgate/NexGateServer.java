package com.nexgate;

import com.nexgate.model.RouteConfig;
import com.nexgate.config.YamlConfigLoader;
import com.nexgate.gateway.HttpServerBootstrap;
import com.nexgate.gateway.RequestRouter;
import com.nexgate.ratelimit.RateLimiter;
import com.nexgate.middleware.AuthMiddleware;
import com.nexgate.circuitbreaker.CircuitBreakerRegistry;

public class NexGateServer {

    public static void main(String[] args) {
        String configPath = System.getenv().getOrDefault("NEXGATE_CONFIG", "config/routes.yml");
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        String redisUrl = System.getenv().getOrDefault("REDIS_URL", "redis://localhost:6379");

        RouteConfig config = YamlConfigLoader.load(configPath);
        RateLimiter rateLimiter = new RateLimiter(redisUrl);
        AuthMiddleware authMiddleware = new AuthMiddleware();
        CircuitBreakerRegistry cbRegistry = new CircuitBreakerRegistry();
        RequestRouter router = new RequestRouter(config, rateLimiter, authMiddleware, cbRegistry);

        HttpServerBootstrap bootstrap = new HttpServerBootstrap(port, router, config, cbRegistry);
        bootstrap.start();
    }
}
