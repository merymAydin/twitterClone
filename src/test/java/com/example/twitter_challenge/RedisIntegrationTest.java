package com.example.twitter_challenge;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@TestPropertySource(properties = {
        "JWT_SECRET=VGhpc0lzQVN1cGVyTG9uZ1Rlc3RTZWNyZXRGb3JKV1Q=",
        "GEMINI_API_KEY=test-key",
        "STRIPE_SECRET_KEY=test-key"
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
