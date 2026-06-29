package com.nexgate.ratelimit;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RateLimiter {
    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);
    private final JedisPool jedisPool;

    public RateLimiter(String redisUrl) {
        this.jedisPool = new JedisPool(redisUrl);
        log.info("RateLimiter connected to Redis");
    }

    public boolean tryAcquire(String key, int maxTokens, long windowMs) {
        try (Jedis jedis = jedisPool.getResource()) {
            String current = jedis.get(key);
            if (current == null) {
                jedis.set(key, "1", SetParams.setParams().px(windowMs));
                return true;
            }
            int count;
            try {
                count = Integer.parseInt(current);
            } catch (NumberFormatException e) {
                count = 0;
            }
            if (count >= maxTokens) {
                return false;
            }
            long remainingTtl = jedis.pttl(key);
            if (remainingTtl <= 0) {
                jedis.set(key, "1", SetParams.setParams().px(windowMs));
                return true;
            }
            jedis.incr(key);
            return true;
        }
    }

    public int getRemainingTokens(String key, int maxTokens) {
        try (Jedis jedis = jedisPool.getResource()) {
            String current = jedis.get(key);
            if (current == null) return maxTokens;
            return Math.max(0, maxTokens - Integer.parseInt(current));
        }
    }
}
