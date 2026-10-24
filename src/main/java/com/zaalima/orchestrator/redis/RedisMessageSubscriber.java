package com.zaalima.orchestrator.redis;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
public class RedisMessageSubscriber implements MessageListener {

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String receivedMessage =
                new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8);

        System.out.println("Redis message received: " + receivedMessage);
    }
}
