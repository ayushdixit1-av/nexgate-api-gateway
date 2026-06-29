package com.nexgate.ratelimit;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public class TokenBucket {
    private static final Logger log = LoggerFactory.getLogger(TokenBucket.class);
    private static final String LUA_SCRIPT = """
        local key = KEYS[1]
        local maxTokens = tonumber(ARGV[1])
        local refillRate = tonumber(ARGV[2])
        local now = tonumber(ARGV[3])
        
        local bucket = redis.call('hmget', key, 'tokens', 'lastRefill', 'maxTokens')
        local tokens = tonumber(bucket[1])
        local lastRefill = tonumber(bucket[2])
        local storedMax = tonumber(bucket[3])
        
        if tokens == nil then
            redis.call('hmset', key, 'tokens', maxTokens - 1, 'lastRefill', now, 'maxTokens', maxTokens)
            redis.call('expire', key, 10)
            return {1, maxTokens - 1}
        end
        
        local effectiveMax = storedMax or maxTokens
        local elapsed = now - lastRefill
        local refill = math.floor(elapsed * refillRate / 1000)
        tokens = math.min(effectiveMax, tokens + refill)
        
        if tokens >= 1 then
            tokens = tokens - 1
            redis.call('hmset', key, 'tokens', tokens, 'lastRefill', now)
            redis.call('expire', key, 10)
            return {1, tokens}
        else
            return {0, tokens}
        end
    """;
    private static final String SHA_HASH;

    static {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            SHA_HASH = HexFormat.of().formatHex(md.digest(LUA_SCRIPT.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private final JedisPool jedisPool;

    public TokenBucket(String redisUrl) {
        this.jedisPool = new JedisPool(redisUrl);
    }

    public boolean tryConsume(String key, int maxTokens, int refillRatePerSecond) {
        try (Jedis jedis = jedisPool.getResource()) {
            try {
                Object result = jedis.evalsha(SHA_HASH, 1, key,
                    String.valueOf(maxTokens),
                    String.valueOf(refillRatePerSecond),
                    String.valueOf(System.currentTimeMillis()));
                if (result instanceof List) {
                    List<?> list = (List<?>) result;
                    return "1".equals(String.valueOf(list.get(0)));
                }
            } catch (redis.clients.jedis.exceptions.JedisNoScriptException e) {
                Object result = jedis.eval(LUA_SCRIPT, 1, key,
                    String.valueOf(maxTokens),
                    String.valueOf(refillRatePerSecond),
                    String.valueOf(System.currentTimeMillis()));
                if (result instanceof List) {
                    List<?> list = (List<?>) result;
                    return "1".equals(String.valueOf(list.get(0)));
                }
            }
            return true;
        }
    }
}
