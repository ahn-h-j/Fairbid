package com.cos.fairbid.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

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

        // 큐 포화 시 호출 스레드가 직접 실행 (작업 유실 방지)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 종료 시 대기 중인 작업 완료 후 종료
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();
        return executor;
    }
}
