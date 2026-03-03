package com.example.farm.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * Redis 工具类
 * 使用 String 存储，手动进行 JSON 序列化
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper redisObjectMapper;

    /**
     * 设置缓存（对象自动转为 JSON）
     */
    public void set(String key, Object value) {
        try {
            String json = redisObjectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json);
        } catch (JsonProcessingException e) {
            log.error("Redis set JSON error: key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 设置缓存并指定过期时间
     */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        try {
            String json = redisObjectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, timeout, unit);
        } catch (JsonProcessingException e) {
            log.error("Redis set JSON error: key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 获取缓存（JSON 转为对象）
     */
    public <T> T get(String key, Class<T> clazz) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            return redisObjectMapper.readValue(json, clazz);
        } catch (Exception e) {
            log.error("Redis get error: key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 获取原始字符串缓存
     */
    public String getString(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 设置字符串缓存
     */
    public void setString(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 设置字符串缓存并指定过期时间
     */
    public void setString(String key, String value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /**
     * 删除缓存
     */
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    /**
     * 批量删除
     */
    public void delete(Collection<String> keys) {
        redisTemplate.delete(keys);
    }

    /**
     * 设置过期时间
     */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(redisTemplate.expire(key, timeout, unit));
    }

    /**
     * 获取过期时间
     */
    public Long getExpire(String key, TimeUnit unit) {
        return redisTemplate.getExpire(key, unit);
    }

    /**
     * 是否存在 Key
     */
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 自增
     */
    public Long increment(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    /**
     * 自增并设置过期时间
     */
    public Long increment(String key, long delta, long timeout, TimeUnit unit) {
        Long value = redisTemplate.opsForValue().increment(key, delta);
        if (value != null && value == delta) {
            // 第一次设置，添加过期时间
            expire(key, timeout, unit);
        }
        return value;
    }

    /**
     * 分布式锁（尝试获取）
     */
    public boolean tryLock(String key, String value, long timeout, TimeUnit unit) {
        Boolean success = redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit);
        return Boolean.TRUE.equals(success);
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
        return getString(key);
    }

    /**
     * 发布消息到频道
     */
    public void convertAndSend(String channel, Object message) {
        try {
            String json = redisObjectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(channel, json);
        } catch (JsonProcessingException e) {
            log.error("Redis convertAndSend error: channel={}, error={}", channel, e.getMessage());
        }
    }
}
