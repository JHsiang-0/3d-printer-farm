package com.example.farm.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis 工具类
 * 使用 String 存储，手动进行 JSON 序列化
 */
@Slf4j
@Component
public class RedisUtil {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisUtil(RedisTemplate<String, String> redisTemplate, 
                     ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 设置缓存（对象自动转为 JSON）
     */
    public void set(String key, Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
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
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, timeout, unit);
            log.info("✅ Redis SET success: key={}, ttl={} {}", key, timeout, unit);
        } catch (JsonProcessingException e) {
            log.error("❌ Redis set JSON error: key={}, error={}", key, e.getMessage());
        } catch (Exception e) {
            log.error("❌ Redis set error: key={}, error={}", key, e.getMessage());
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
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            log.error("Redis get error: key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 获取缓存（JSON 转为对象，支持泛型集合）
     * 适用于 List、Map 等复杂类型
     * 用法: redisUtil.getList("key", new TypeReference<List<FarmPrinter>>() {})
     */
    public <T> T get(String key, TypeReference<T> typeReference) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                log.info("🔄 Redis GET miss: key={}", key);
                return null;
            }
            T result = objectMapper.readValue(json, typeReference);
            log.info("✅ Redis GET hit: key={}", key);
            return result;
        } catch (Exception e) {
            log.error("❌ Redis get error: key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 获取 List 类型缓存（便捷方法）
     * 用法: redisUtil.getList("key", FarmPrinter.class)
     */
    public <T> List<T> getList(String key, Class<T> elementClass) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            // 使用 TypeReference 正确反序列化 List
            return objectMapper.readValue(json, new TypeReference<List<T>>() {});
        } catch (Exception e) {
            log.error("Redis getList error: key={}, error={}", key, e.getMessage());
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
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(channel, json);
        } catch (JsonProcessingException e) {
            log.error("Redis convertAndSend error: channel={}, error={}", channel, e.getMessage());
        }
    }

    // ==================== List 操作 ====================

    /**
     * 从左侧插入元素到 List
     */
    public void listLeftPush(String key, Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForList().leftPush(key, json);
        } catch (JsonProcessingException e) {
            log.error("Redis listLeftPush error: key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 从左侧插入元素到 List，并设置过期时间
     */
    public void listLeftPush(String key, Object value, long timeout, TimeUnit unit) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForList().leftPush(key, json);
            expire(key, timeout, unit);
        } catch (JsonProcessingException e) {
            log.error("Redis listLeftPush error: key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 获取 List 指定范围的元素
     */
    public <T> List<T> listRange(String key, long start, long end, Class<T> clazz) {
        try {
            List<String> jsonList = redisTemplate.opsForList().range(key, start, end);
            if (jsonList == null || jsonList.isEmpty()) {
                return new ArrayList<>();
            }
            List<T> result = new ArrayList<>();
            for (String json : jsonList) {
                result.add(objectMapper.readValue(json, clazz));
            }
            return result;
        } catch (Exception e) {
            log.error("Redis listRange error: key={}, error={}", key, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 修剪 List，只保留指定范围的元素
     */
    public void listTrim(String key, long start, long end) {
        redisTemplate.opsForList().trim(key, start, end);
    }

    /**
     * 获取 List 长度
     */
    public Long listSize(String key) {
        return redisTemplate.opsForList().size(key);
    }

    // ==================== Set 操作 ====================

    /**
     * 添加元素到 Set
     */
    public void setAdd(String key, Object... members) {
        try {
            String[] jsonMembers = new String[members.length];
            for (int i = 0; i < members.length; i++) {
                jsonMembers[i] = objectMapper.writeValueAsString(members[i]);
            }
            redisTemplate.opsForSet().add(key, jsonMembers);
        } catch (JsonProcessingException e) {
            log.error("Redis setAdd error: key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 从 Set 中移除元素
     */
    public void setRemove(String key, Object... members) {
        try {
            String[] jsonMembers = new String[members.length];
            for (int i = 0; i < members.length; i++) {
                jsonMembers[i] = objectMapper.writeValueAsString(members[i]);
            }
            redisTemplate.opsForSet().remove(key, (Object[]) jsonMembers);
        } catch (JsonProcessingException e) {
            log.error("Redis setRemove error: key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 获取 Set 大小
     */
    public Long setSize(String key) {
        return redisTemplate.opsForSet().size(key);
    }

    /**
     * 判断元素是否在 Set 中
     */
    public boolean setIsMember(String key, Object member) {
        try {
            String json = objectMapper.writeValueAsString(member);
            return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, json));
        } catch (JsonProcessingException e) {
            log.error("Redis setIsMember error: key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 删除整个 Set
     */
    public void setDelete(String key) {
        redisTemplate.delete(key);
    }
}
