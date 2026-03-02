package com.example.farm.common.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * Redis 工具类
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 设置缓存
     */
    public void set(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value);
        } catch (Exception e) {
            log.error("Redis set error: key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 设置缓存并指定过期时间
     */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout, unit);
        } catch (Exception e) {
            log.error("Redis set error: key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 获取缓存
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        try {
            return (T) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("Redis get error: key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 删除缓存
     */
    public void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("Redis delete error: key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 批量删除
     */
    public void delete(Collection<String> keys) {
        try {
            redisTemplate.delete(keys);
        } catch (Exception e) {
            log.error("Redis delete error: keys={}, error={}", keys, e.getMessage());
        }
    }

    /**
     * 设置过期时间
     */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        try {
            return Boolean.TRUE.equals(redisTemplate.expire(key, timeout, unit));
        } catch (Exception e) {
            log.error("Redis expire error: key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 获取过期时间
     */
    public Long getExpire(String key, TimeUnit unit) {
        try {
            return redisTemplate.getExpire(key, unit);
        } catch (Exception e) {
            log.error("Redis getExpire error: key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 是否存在 Key
     */
    public boolean hasKey(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("Redis hasKey error: key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 自增
     */
    public Long increment(String key, long delta) {
        try {
            return redisTemplate.opsForValue().increment(key, delta);
        } catch (Exception e) {
            log.error("Redis increment error: key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 自增并设置过期时间
     */
    public Long increment(String key, long delta, long timeout, TimeUnit unit) {
        try {
            Long value = redisTemplate.opsForValue().increment(key, delta);
            if (value != null && value == 1) {
                // 第一次设置，添加过期时间
                expire(key, timeout, unit);
            }
            return value;
        } catch (Exception e) {
            log.error("Redis increment error: key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 分布式锁（尝试获取）
     */
    public boolean tryLock(String key, String value, long timeout, TimeUnit unit) {
        try {
            Boolean success = redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit);
            return Boolean.TRUE.equals(success);
        } catch (Exception e) {
            log.error("Redis tryLock error: key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 释放分布式锁
     */
    public void unlock(String key) {
        delete(key);
    }

    /**
     * 获取分布式锁的值
     */
    public String getLockValue(String key) {
        Object value = get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 发布消息到频道
     */
    public void convertAndSend(String channel, Object message) {
        try {
            redisTemplate.convertAndSend(channel, message);
        } catch (Exception e) {
            log.error("Redis convertAndSend error: channel={}, error={}", channel, e.getMessage());
        }
    }
}
