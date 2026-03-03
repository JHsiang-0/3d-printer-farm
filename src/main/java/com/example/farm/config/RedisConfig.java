package com.example.farm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置类
 * 使用 String 序列化，手动处理 JSON 转换
 */
@Configuration
public class RedisConfig {

    /**
     * 配置 RedisTemplate
     * 使用 StringRedisSerializer 避免弃用 API
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 统一使用 StringRedisSerializer
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 提供 ObjectMapper Bean 用于手动 JSON 转换
     */
    @Bean
    public ObjectMapper redisObjectMapper() {
        return new ObjectMapper();
    }
}
