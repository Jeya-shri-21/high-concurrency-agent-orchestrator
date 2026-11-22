package com.zaalima.orchestrator.lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LockConfig Implementation Selection Tests")
class LockConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(LockConfig.class)
            .withBean("redisDistributedLockManager", DistributedLockManager.class, () -> Mockito.mock(RedisDistributedLockManager.class))
            .withBean("inMemoryDistributedLockManager", DistributedLockManager.class, () -> Mockito.mock(InMemoryDistributedLockManager.class));

    @Test
    @DisplayName("Default configuration selects InMemoryDistributedLockManager as primary")
    void whenDefaultProperty_thenInMemoryLockManagerIsSelected() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("distributedLockManager");
            DistributedLockManager primaryLockManager = context.getBean(DistributedLockManager.class);
            assertThat(primaryLockManager).isInstanceOf(InMemoryDistributedLockManager.class);
        });
    }

    @Test
    @DisplayName("orchestrator.lock.type=in-memory selects InMemoryDistributedLockManager as primary")
    void whenLockTypeInMemory_thenInMemoryLockManagerIsSelected() {
        contextRunner
                .withPropertyValues("orchestrator.lock.type=in-memory")
                .run(context -> {
                    assertThat(context).hasBean("distributedLockManager");
                    DistributedLockManager primaryLockManager = context.getBean(DistributedLockManager.class);
                    assertThat(primaryLockManager).isInstanceOf(InMemoryDistributedLockManager.class);
                });
    }

    @Test
    @DisplayName("orchestrator.lock.type=redis selects RedisDistributedLockManager as primary")
    void whenLockTypeRedis_thenRedisLockManagerIsSelected() {
        contextRunner
                .withPropertyValues("orchestrator.lock.type=redis")
                .run(context -> {
                    assertThat(context).hasBean("distributedLockManager");
                    DistributedLockManager primaryLockManager = context.getBean(DistributedLockManager.class);
                    assertThat(primaryLockManager).isInstanceOf(RedisDistributedLockManager.class);
                });
    }

    @Test
    @DisplayName("Both named lock beans remain accessible via qualifier")
    void whenQueriedByQualifier_bothImplementationsAreAccessible() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("redisDistributedLockManager");
            assertThat(context).hasBean("inMemoryDistributedLockManager");
            assertThat(context.getBean("redisDistributedLockManager")).isInstanceOf(RedisDistributedLockManager.class);
            assertThat(context.getBean("inMemoryDistributedLockManager")).isInstanceOf(InMemoryDistributedLockManager.class);
        });
    }
}
