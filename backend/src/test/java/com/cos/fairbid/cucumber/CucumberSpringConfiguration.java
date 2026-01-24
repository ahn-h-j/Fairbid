package com.cos.fairbid.cucumber;

import com.cos.fairbid.cucumber.config.TestSecurityConfig;
import com.redis.testcontainers.RedisContainer;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.utility.DockerImageName;

/**
 * Cucumber와 Spring Boot 통합을 위한 설정 클래스.
 * TestContainers를 사용하여 실제 MySQL, Redis 환경에서 테스트를 수행한다.
 */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:mysql:8.0:///testdb",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver"
})
public class CucumberSpringConfiguration {

    // Redis 컨테이너 (테스트 전체에서 공유)
    static final RedisContainer REDIS_CONTAINER = new RedisContainer(
            DockerImageName.parse("redis:7-alpine")
    );

    static {
        REDIS_CONTAINER.start();
    }

    /**
     * Redis 컨테이너의 동적 프로퍼티를 Spring 환경에 등록
     */
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
        registry.add("spring.data.redis.port", REDIS_CONTAINER::getFirstMappedPort);
    }
}
