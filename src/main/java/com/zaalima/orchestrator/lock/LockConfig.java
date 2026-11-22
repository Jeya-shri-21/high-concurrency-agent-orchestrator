package com.zaalima.orchestrator.lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Spring configuration responsible for selecting the active {@link DistributedLockManager}.
 * <p>
 * Configuration strategy:
 * <ul>
 *   <li><strong>Production / Docker Multi-Node:</strong> When {@code orchestrator.lock.type=redis},
 *       {@link RedisDistributedLockManager} is designated as the {@link Primary} bean, ensuring
 *       all orchestrator nodes coordinate mutual exclusion through Redis.</li>
 *   <li><strong>Local Development / Standalone:</strong> When {@code orchestrator.lock.type=in-memory}
 *       (or omitted), {@link InMemoryDistributedLockManager} is designated as the {@link Primary} bean,
 *       providing a lightweight, zero-dependency single-JVM fallback.</li>
 * </ul>
 */
@Configuration
public class LockConfig {

    private static final Logger log = LoggerFactory.getLogger(LockConfig.class);

    @Bean("distributedLockManager")
    @Primary
    @ConditionalOnProperty(name = "orchestrator.lock.type", havingValue = "redis")
    public DistributedLockManager redisPrimaryLockManager(
            @Qualifier("redisDistributedLockManager") DistributedLockManager redisLockManager) {
        log.info("DistributedLockManager configured: REDIS (cluster-wide multi-node mutual exclusion enabled)");
        return redisLockManager;
    }

    @Bean("distributedLockManager")
    @Primary
    @ConditionalOnProperty(name = "orchestrator.lock.type", havingValue = "in-memory", matchIfMissing = true)
    public DistributedLockManager inMemoryPrimaryLockManager(
            @Qualifier("inMemoryDistributedLockManager") DistributedLockManager inMemoryLockManager) {
        log.info("DistributedLockManager configured: IN-MEMORY (single-JVM local development fallback)");
        return inMemoryLockManager;
    }
}
