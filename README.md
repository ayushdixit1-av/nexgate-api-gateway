# NexGate — High-Performance API Gateway

<p align="center">
  <b>Asynchronous, event-driven API gateway built on Netty with Redis-backed rate limiting, JWT/OAuth2 auth, and circuit breaker resilience.</b>
</p>

<div align="center">

![Java](https://img.shields.io/badge/Java_21-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Netty](https://img.shields.io/badge/Netty-4.1.118-0d8e45?style=flat)
![Redis](https://img.shields.io/badge/Redis_7-DC382D?style=flat&logo=redis&logoColor=white)
![Build](https://img.shields.io/badge/build-passing-brightgreen)

</div>

---

## Architecture

\\\mermaid
graph TB
    Client[Client HTTP] --> Gateway[NexGate Gateway :8080]
    Gateway --> AdminUI[Admin Dashboard /admin]
    Gateway --> AdminAPI[Admin REST API /api/admin]
    subgraph Request Flow
        Gateway -->|/api/users/**| US[User Service :9001/:9002]
        Gateway -->|/api/orders/**| OS[Order Service :9003]
        Gateway -->|/api/public/**| PS[Public API :9004]
        Gateway -->|/webhooks/**| WH[Webhook Handler :9005]
    end
    subgraph Redis
        RL[(Rate Limit Redis 7)]
    end
    Gateway -->|rate limit| RL
    subgraph Observability
        Metrics[(Metrics Collector)]
        CB[(Circuit Breaker Registry)]
    end
    Gateway --> Metrics
    Gateway --> CB
    AdminAPI --> Metrics
    AdminAPI --> CB
\\\

---

## Features

| Feature | Implementation |
|---------|---------------|
| **Async Proxy** | Non-blocking Netty HTTP pipeline with zero-copy buffering |
| **Route Matching** | Path pattern matching with wildcard and path-param support |
| **Load Balancing** | Round-robin and weighted round-robin across upstream services |
| **Auth Middleware** | JWT (HMAC-SHA), API Key, and OAuth2 Bearer token validation |
| **Rate Limiting** | Redis-backed sliding window counter + Lua-scripted token bucket |
| **Circuit Breaker** | State machine (CLOSED → OPEN → HALF_OPEN → CLOSED) |
| **Admin Dashboard** | Built-in web UI at /admin with live metrics |
| **Admin REST API** | JSON endpoints for health, routes, circuit breaker state |
| **Metrics** | Request count, error count, active connections, uptime |
| **Docker** | Single Dockerfile + docker-compose with Redis and mock services |
| **YAML Config** | Declarative route configuration |

---

## Quick Start

### Prerequisites
- Java 21+
- Maven 3.8+
- Redis 7+ (or Docker)
- Docker & Docker Compose (optional, for full demo)

### Build
\\\ash
git clone https://github.com/ayushdixit1-av/nexgate-api-gateway.git
cd nexgate-api-gateway
mvn clean package -DskipTests
\\\

### Run with Docker (recommended)
\\\ash
docker compose up --build
\\\

### Run locally
\\\ash
mvn clean package -DskipTests
java -jar target/nexgate-1.0.0.jar
\\\

---

## Dashboard

Open **http://localhost:8080/admin** in your browser:
- Metric cards: total requests, active connections, routes, circuit breakers
- Route table: IDs, paths, methods, auth, rate limits, upstreams, request/error counts
- Circuit breaker status with state and failure count per route
- Auto-refresh every 5 seconds

---

## Configuration

Edit \config/routes.yml\:

\\\yaml
port: 8080
routes:
  - id: user-service
    path: /api/users/**
    method: ANY
    auth: [jwt]
    rateLimit: 1000
    rateLimitDuration: 1m
    circuitBreaker:
      failureThreshold: 5
      successThreshold: 3
      timeoutMs: 30000
    upstreams:
      - host: localhost
        port: 9001
        weight: 3
      - host: localhost
        port: 9002
        weight: 2
\\\

### Route Fields
| Field | Required | Description |
|-------|----------|-------------|
| \id\ | Yes | Unique route identifier |
| \path\ | Yes | Path pattern |
| \method\ | Yes | HTTP method or \ANY\ |
| \uth\ | No | List: \jwt\, \pi-key\, \oauth2\ |
| \ateLimit\ | No | Max requests per window |
| \ateLimitDuration\ | No | Window duration (1s, 1m, 1h) |
| \circuitBreaker\ | No | Circuit breaker settings |
| \upstreams\ | Yes | Array of backend services |

---

## Admin REST API

### Health Check
\\\http
GET /api/admin/health
\\\
\\\json
{"status":"UP","uptime":"0:05:23","totalRequests":142,"routesConfigured":5}
\\\

### Routes
\\\http
GET /api/admin/routes
\\\

### Circuit Breakers
\\\http
GET /api/admin/circuit-breakers
\\\

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| \PORT\ | \8080\ | Gateway listen port |
| \REDIS_URL\ | \edis://localhost:6379\ | Redis connection string |
| \JWT_SECRET\ | (default dev key) | HMAC-SHA key for JWT validation |
| \NEXGATE_CONFIG\ | \config/routes.yml\ | Path to route configuration |

---

## Testing

\\\ash
mvn clean test
\\\

Tests cover:
- Circuit breaker state machine (CLOSED, OPEN, HALF_OPEN transitions)
- Round-robin and weighted round-robin load balancing
- Route pattern matching with path parameters
- Batch route matching

---

## Project Structure

\\\
src/main/java/com/nexgate/
  NexGateServer.java          # Entry point
  admin/
    AdminApiHandler.java       # REST API for admin dashboard
    MetricsCollector.java      # Request/error metrics tracking
    StaticFileHandler.java     # Serves admin frontend
  circuitbreaker/
    CircuitBreaker.java        # State machine implementation
    CircuitBreakerRegistry.java
  config/
    YamlConfigLoader.java      # SnakeYAML config parser
  gateway/
    HttpServerBootstrap.java   # Netty server bootstrap
    LoadBalancer.java          # Round-robin + weighted RR
    ProxyFrontendHandler.java  # Channel handler + request dispatcher
    RequestRouter.java          # Route matching, auth, rate limiting
    ReverseProxyHandler.java   # Upstream proxy with timeout
  middleware/
    AuthMiddleware.java         # JWT, API Key, OAuth2 validation
  model/
    Route.java, RouteConfig.java, UpstreamService.java
  ratelimit/
    RateLimiter.java            # Redis counter-based rate limiter
    TokenBucket.java            # Lua-scripted token bucket
src/main/resources/
  static/admin/index.html       # Admin dashboard frontend
  logback.xml
\\\

---

## License

MIT
