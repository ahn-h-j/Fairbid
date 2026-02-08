package com.cos.fairbid.bid.adapter.in.stream;

import com.cos.fairbid.auction.application.port.out.AuctionRepositoryPort;
import com.cos.fairbid.bid.adapter.out.stream.RedisBidStreamAdapter;
import com.cos.fairbid.bid.application.port.out.BidRepositoryPort;
import com.cos.fairbid.bid.domain.Bid;
import com.cos.fairbid.bid.domain.BidType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Redis Stream 기반 입찰 RDB 동기화 컨슈머
 *
 * Consumer Group을 통해 Redis Stream 메시지를 소비하고,
 * RDB에 저장한 후 ACK를 보내는 Inbound Adapter.
 *
 * 핵심 동작:
 * 1. 새 메시지 수신 → RDB 저장 → ACK (정상 흐름)
 * 2. RDB 저장 실패 → ACK 안 함 → PENDING에 남음 (장애 시)
 * 3. @Scheduled로 PENDING 메시지 주기적 재처리 (복구 시)
 *
 * @Async 대비 장점:
 * - DB 장애 시에도 호출 스레드(입찰 API) 블로킹 없음
 * - 앱 종료 시에도 메시지가 Redis에 남아 재시작 후 재처리
 * - CallerRunsPolicy 문제 없음 (스트림 발행은 O(1))
 */
@Component
@Slf4j
public class BidStreamConsumer implements DisposableBean {

    private static final String STREAM_KEY = RedisBidStreamAdapter.STREAM_KEY;
    private static final String GROUP_NAME = "bid-rdb-sync-group";
    /** PENDING 메시지 재처리 대상 기준 시간 (이 시간 이상 ACK 안 된 메시지) */
    private static final Duration PENDING_TIMEOUT = Duration.ofSeconds(30);

    private final String consumerName;
    private final StringRedisTemplate redisTemplate;
    private final BidRepositoryPort bidRepository;
    private final AuctionRepositoryPort auctionRepository;
    private final Timer rdbSyncTimer;
    private final Counter consumeSuccessCounter;
    private final Counter consumeFailCounter;

    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;

    public BidStreamConsumer(
            StringRedisTemplate redisTemplate,
            BidRepositoryPort bidRepository,
            AuctionRepositoryPort auctionRepository,
            MeterRegistry meterRegistry
    ) {
        this.redisTemplate = redisTemplate;
        this.bidRepository = bidRepository;
        this.auctionRepository = auctionRepository;
        // 인스턴스별 고유 컨슈머 이름 (다중 인스턴스 대비)
        this.consumerName = "consumer-" + UUID.randomUUID().toString().substring(0, 8);

        this.rdbSyncTimer = Timer.builder("fairbid_bid_rdb_sync_seconds")
                .description("RDB 동기화 소요 시간")
                .publishPercentileHistogram(true)
                .register(meterRegistry);
        this.consumeSuccessCounter = Counter.builder("fairbid_stream_consume_total")
                .tag("result", "success")
                .description("Stream 메시지 소비 성공 건수")
                .register(meterRegistry);
        this.consumeFailCounter = Counter.builder("fairbid_stream_consume_total")
                .tag("result", "fail")
                .description("Stream 메시지 소비 실패 건수")
                .register(meterRegistry);
    }

    /**
     * 애플리케이션 시작 시 Consumer Group 생성 및 리스너 컨테이너 시작
     */
    @PostConstruct
    public void init() {
        createConsumerGroupIfNotExists();
        startListenerContainer();
        log.info("BidStreamConsumer 시작: group={}, consumer={}", GROUP_NAME, consumerName);
    }

    /**
     * Consumer Group이 없으면 생성한다.
     * 스트림이 없어도 MKSTREAM 옵션으로 자동 생성된다.
     */
    private void createConsumerGroupIfNotExists() {
        try {
            redisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("0"), GROUP_NAME);
            log.info("Consumer Group 생성: stream={}, group={}", STREAM_KEY, GROUP_NAME);
        } catch (RedisSystemException e) {
            // BUSYGROUP: 이미 존재하는 경우 정상
            if (e.getCause() != null && e.getCause().getMessage() != null
                    && e.getCause().getMessage().contains("BUSYGROUP")) {
                log.info("Consumer Group 이미 존재: stream={}, group={}", STREAM_KEY, GROUP_NAME);
            } else {
                throw e;
            }
        }
    }

    /** 병렬 Consumer 수 (Consumer Group 내에서 메시지를 분산 처리) */
    private static final int CONSUMER_COUNT = 10;

    /**
     * StreamMessageListenerContainer를 설정하고 시작한다.
     * Consumer Group 내에 여러 Consumer를 등록하여 병렬 처리한다.
     * 각 Consumer는 독립 스레드에서 Stream을 폴링하고, Consumer Group이
     * 메시지를 자동으로 분배하므로 중복 처리 없이 병렬성을 확보한다.
     */
    private void startListenerContainer() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CONSUMER_COUNT);
        executor.setMaxPoolSize(CONSUMER_COUNT * 2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("bid-stream-consumer-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainerOptions.builder()
                        .batchSize(50)
                        .pollTimeout(Duration.ofSeconds(1))
                        .executor(executor)
                        .errorHandler(e -> log.error("Stream 리스너 에러: {}", e.getMessage()))
                        .build();

        container = StreamMessageListenerContainer.create(
                redisTemplate.getConnectionFactory(), options);

        // 병렬 Consumer 등록: 각 Consumer가 독립적으로 Stream을 폴링
        for (int i = 0; i < CONSUMER_COUNT; i++) {
            String name = consumerName + "-" + i;
            container.receive(
                    Consumer.from(GROUP_NAME, name),
                    StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
                    this::onMessage
            );
        }

        container.start();
    }

    /**
     * 메시지 수신 콜백
     * 타입에 따라 분기하여 RDB 저장 후 수동 ACK한다.
     * 실패 시 ACK하지 않아 PENDING에 남는다.
     */
    @Transactional
    public void onMessage(MapRecord<String, String, String> message) {
        Map<String, String> body = message.getValue();
        String type = body.get("type");
        String recordId = message.getId().getValue();

        try {
            rdbSyncTimer.record(() -> {
                switch (type) {
                    case "BID_SAVE" -> processBidSave(body, recordId);
                    case "INSTANT_BUY_UPDATE" -> processInstantBuyUpdate(body);
                    default -> log.warn("알 수 없는 메시지 타입: type={}, recordId={}", type, recordId);
                }
            });

            // RDB 저장 성공 시에만 ACK
            redisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP_NAME, message.getId());
            consumeSuccessCounter.increment();
            log.debug("메시지 처리 완료: type={}, recordId={}", type, recordId);
        } catch (Exception e) {
            consumeFailCounter.increment();
            // ACK하지 않으면 PENDING 목록에 남아 retryPendingMessages()에서 재처리됨
            log.error("메시지 처리 실패 (PENDING 유지): type={}, recordId={}, error={}",
                    type, recordId, e.getMessage());
        }
    }

    /**
     * BID_SAVE 메시지 처리: 입찰 이력을 RDB에 멱등하게 저장한다.
     * streamRecordId unique 제약으로 at-least-once 중복 처리를 방지한다.
     */
    private void processBidSave(Map<String, String> body, String recordId) {
        Bid bid = Bid.reconstitute()
                .auctionId(Long.parseLong(body.get("auctionId")))
                .bidderId(Long.parseLong(body.get("bidderId")))
                .amount(Long.parseLong(body.get("amount")))
                .bidType(BidType.valueOf(body.get("bidType")))
                .createdAt(LocalDateTime.parse(body.get("createdAt")))
                .build();
        bidRepository.saveIdempotent(bid, recordId);
    }

    /**
     * INSTANT_BUY_UPDATE 메시지 처리: 경매 즉시 구매 상태를 RDB에 업데이트한다.
     */
    private void processInstantBuyUpdate(Map<String, String> body) {
        auctionRepository.updateInstantBuyActivated(
                Long.parseLong(body.get("auctionId")),
                Long.parseLong(body.get("currentPrice")),
                Integer.parseInt(body.get("totalBidCount")),
                Long.parseLong(body.get("bidIncrement")),
                Long.parseLong(body.get("bidderId")),
                Long.parseLong(body.get("currentTimeMs")),
                Long.parseLong(body.get("scheduledEndTimeMs"))
        );
    }

    /**
     * PENDING 메시지 재처리 스케줄러
     *
     * 30초마다 실행되며, PENDING_TIMEOUT 이상 ACK되지 않은 메시지를
     * XCLAIM으로 가져와 재처리한다. DB 복구 후 밀린 메시지가 자동으로
     * 소진되는 핵심 메커니즘이다.
     */
    @Scheduled(fixedRate = 30000)
    public void retryPendingMessages() {
        try {
            // XPENDING으로 처리 안 된 메시지 조회 (최대 50건)
            PendingMessages pendingMessages = redisTemplate.opsForStream()
                    .pending(STREAM_KEY, GROUP_NAME, Range.unbounded(), 50);

            if (pendingMessages == null || pendingMessages.isEmpty()) {
                return;
            }

            long retryCount = 0;
            for (PendingMessage pending : pendingMessages) {
                // PENDING_TIMEOUT 이상 지난 메시지만 재처리
                if (pending.getElapsedTimeSinceLastDelivery().compareTo(PENDING_TIMEOUT) < 0) {
                    continue;
                }

                // XCLAIM으로 이 컨슈머에게 소유권 이전
                @SuppressWarnings("unchecked")
                List<MapRecord<String, String, String>> claimed = (List<MapRecord<String, String, String>>) (List<?>)
                        redisTemplate.opsForStream()
                                .claim(STREAM_KEY, GROUP_NAME, consumerName,
                                        PENDING_TIMEOUT, pending.getId());

                for (MapRecord<String, String, String> message : claimed) {
                    onMessage(message);
                    retryCount++;
                }
            }

            if (retryCount > 0) {
                log.info("PENDING 메시지 재처리 완료: {}건", retryCount);
            }
        } catch (Exception e) {
            log.error("PENDING 메시지 재처리 중 에러: {}", e.getMessage());
        }
    }

    /**
     * 애플리케이션 종료 시 리스너 컨테이너를 정지한다.
     * 처리 중이던 메시지는 ACK되지 않아 PENDING에 남고,
     * 재시작 시 retryPendingMessages()에서 재처리된다.
     */
    @Override
    public void destroy() {
        if (container != null && container.isRunning()) {
            container.stop();
            log.info("BidStreamConsumer 종료: consumer={}", consumerName);
        }
    }
}
