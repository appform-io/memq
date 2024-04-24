package io.appform;

import com.codahale.metrics.MetricRegistry;
import io.appform.config.ExecutorConfig;
import io.appform.config.MemqConfig;
import io.appform.memq.HighLevelActor;
import io.appform.memq.actor.HighLevelActorConfig;
import io.appform.memq.actor.Message;
import io.appform.memq.retry.config.NoRetryConfig;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MemqActorSystemTest {

    private static final String GLOBAL_THREADPOOL_NAME = "GLOBAL";
    private static final int SINGLE_PARTITION = 1;

    @Test
    void newActorRegisterAndCloseTest() {
        assertDoesNotThrow(() -> {
            val metricRegistry = new MetricRegistry();
            val memqConfig = MemqConfig.builder()
                    .executors(List.of(ExecutorConfig.builder()
                            .threadPoolSize(Constants.DEFAULT_THREADPOOL)
                            .name(GLOBAL_THREADPOOL_NAME)
                            .build()))
                    .build();
            val memqActorSystem = new MemqActorSystem(memqConfig,
                    (name, parallel, queueSize) -> Executors.newFixedThreadPool(Constants.DEFAULT_THREADPOOL),
                    metricRegistry);
            val highLevelActorConfig = HighLevelActorConfig.builder()
                    .partitions(SINGLE_PARTITION)
                    .retryConfig(new NoRetryConfig())
                    .executorName(GLOBAL_THREADPOOL_NAME)
                    .build();
            val highLevelActor = new HighLevelActor<>(ActorType.TEST_HIGH_LEVEL_ACTOR, highLevelActorConfig, memqActorSystem) {
                @Override
                protected boolean handle(Message message) {
                    return true;
                }
            };
            assertNotNull(highLevelActor);
            memqActorSystem.close();
        });
    }

    enum ActorType {
        TEST_HIGH_LEVEL_ACTOR,
    }

    static class TestMessage implements Message {

        String value = UUID.randomUUID().toString();

        @Override
        public String id() {
            return value;
        }
    }
}
