package com.cos.fairbid.notification.adapter.out.pubsub;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis Pub/Sub 리스너 설정
 *
 * 경매 입찰 업데이트와 경매 종료 채널을 구독하여
 * RedisMessageSubscriber가 메시지를 수신할 수 있게 한다.
 */
@Configuration
public class RedisPubSubConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisMessageSubscriber subscriber) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // 입찰 업데이트 채널 구독
        container.addMessageListener(subscriber,
                new ChannelTopic(RedisPubSubBroadcastAdapter.CHANNEL_BID_UPDATE));

        // 경매 종료 채널 구독
        container.addMessageListener(subscriber,
                new ChannelTopic(RedisPubSubBroadcastAdapter.CHANNEL_AUCTION_CLOSED));

        return container;
    }
}
