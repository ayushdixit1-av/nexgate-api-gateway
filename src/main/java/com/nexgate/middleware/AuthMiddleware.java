package com.nexgate.middleware;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.netty.handler.codec.http.FullHttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class AuthMiddleware {
    private static final Logger log = LoggerFactory.getLogger(AuthMiddleware.class);
    private final SecretKey jwtSecret;
    private final JwtParser jwtParser;

    public AuthMiddleware() {
        String secretStr = System.getenv().getOrDefault("JWT_SECRET", "NexGateDefaultSecretKeyChangeInProduction2024!");
        this.jwtSecret = Keys.hmacShaKeyFor(secretStr.getBytes(StandardCharsets.UTF_8));
        this.jwtParser = Jwts.parser().verifyWith(jwtSecret).build();
    }

    public String authenticate(FullHttpRequest request, List<String> authMethods) {
        for (String method : authMethods) {
            String error = switch (method.toLowerCase()) {
                case "jwt" -> validateJwt(request);
                case "api-key" -> validateApiKey(request);
                case "oauth2" -> validateOAuth2(request);
                default -> "Unknown auth method: " + method;
            };
            if (error != null) return error;
        }
        return null;
    }

    private String validateJwt(FullHttpRequest request) {
        String authHeader = request.headers().get("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return "Missing or invalid Authorization header";
        }
        String token = authHeader.substring(7);
        try {
            Claims claims = jwtParser.parseSignedClaims(token).getPayload();
            request.headers().set("X-User-Id", claims.getSubject());
            request.headers().set("X-User-Roles", String.join(",", claims.get("roles", List.class)));
            return null;
        } catch (JwtException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return "Invalid or expired JWT token";
        }
    }

    private String validateApiKey(FullHttpRequest request) {
        String apiKey = request.headers().get("X-API-Key");
        if (apiKey == null || apiKey.isBlank()) {
            return "Missing X-API-Key header";
        }
        if (apiKey.length() < 16) {
            return "Invalid API key format";
        }
        return null;
    }

    private String validateOAuth2(FullHttpRequest request) {
        String authHeader = request.headers().get("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return "Missing OAuth2 token";
        }
        String token = authHeader.substring(7);
        if (token.contains(".")) {
            try {
                String[] parts = token.split("\\.");
                if (parts.length == 3) {
                    return null;
                }
            } catch (Exception e) {
                return "Invalid OAuth2 token format";
            }
        }
        return null;
    }
}
