package com.cos.fairbid;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:mysql:8.0:///testdb",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver"
})
class FairBidApplicationTests {

    // Redis 컨테이너 (BidStreamConsumer가 @PostConstruct로 연결하므로 필수)
    static final RedisContainer REDIS_CONTAINER = new RedisContainer(
            DockerImageName.parse("redis:7-alpine")
    );

    static {
        REDIS_CONTAINER.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
        registry.add("spring.data.redis.port", REDIS_CONTAINER::getFirstMappedPort);
    }

    @Test
    void contextLoads() {
    }

}
