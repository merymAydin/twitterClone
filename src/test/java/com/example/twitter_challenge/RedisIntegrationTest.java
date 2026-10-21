package com.example.twitter_challenge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_REDIS_TESTS", matches = "true")
@TestPropertySource(properties = {
        "spring.data.redis.host=${REDIS_HOST:localhost}",
        "spring.data.redis.port=${REDIS_PORT:6379}"
})
public class RedisIntegrationTest {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    public void setRedisTemplate() {
        redisTemplate.opsForValue().set("test-key", "hello");
        Object result = redisTemplate.opsForValue().get("test-key");

        assertEquals("hello", result);
    }


}
