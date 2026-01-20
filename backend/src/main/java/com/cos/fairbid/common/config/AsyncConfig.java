package com.cos.fairbid.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 비동기 처리 설정
 * 입찰 이력 저장 등 비동기 작업용 스레드 풀 설정
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 입찰 처리용 비동기 스레드 풀
     */
    @Bean(name = "bidAsyncExecutor")
    public Executor bidAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);      // 기본 스레드 수
        executor.setMaxPoolSize(50);       // 최대 스레드 수
        executor.setQueueCapacity(100);    // 대기 큐 크기
        executor.setThreadNamePrefix("bid-async-");
        executor.initialize();
        return executor;
    }
}
