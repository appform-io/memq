package io.appform.memq;

import io.appform.memq.actor.DispatcherType;
import io.appform.memq.actor.Message;
import io.appform.memq.actor.MessageMeta;
import io.appform.memq.exceptionhandler.config.SidelineConfig;
import io.appform.memq.helper.TestUtil;
import io.appform.memq.retry.config.CountLimitedExponentialWaitRetryConfig;
import lombok.Value;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class MessageMetaTest {


    @Test
    void testMessageMetaWithRetry() throws Exception {

        val retryConfig = CountLimitedExponentialWaitRetryConfig.builder()
                .maxAttempts(3)
                .waitTimeInMillis(100)
                .maxWaitTimeInMillis(500)
                .multipier(1.1)
                .build();
        val counter = new AtomicInteger(0);
        val sideline = new AtomicBoolean();
        val recordedPublishTime = new AtomicLong(-1);
        val publisherTime = new AtomicLong(-1);
        val tc = Executors.newFixedThreadPool(TestUtil.DEFAULT_THREADPOOL_SIZE);
        try (val actorSystem = TestUtil.actorSystem(tc, TestUtil.DEFAULT_DISPATCHER)) {
            val highLevelActorConfig = HighLevelActorConfig.builder()
                    .partitions(Constants.SINGLE_PARTITION)
                    .maxSizePerPartition(Long.MAX_VALUE)
                    .executorName(TestUtil.GLOBAL_EXECUTOR_SERVICE_GROUP)
                    .retryConfig(retryConfig)
                    .exceptionHandlerConfig(new SidelineConfig())
                    .build();
            val actor = allExceptionActor(counter, sideline, recordedPublishTime, publisherTime,
                    highLevelActorConfig, actorSystem);
            publisherTime.set(System.currentTimeMillis());
            val msg = new TestIntWithHeadersMessage(1);
            msg.addHeader("key", "value");
            actor.publish(msg);
            Awaitility.await()
                    .timeout(Duration.ofMinutes(1))
                    .catchUncaughtExceptions()
                    .until(actor::isEmpty);
        } finally {
            tc.shutdownNow();
        }
        assertEquals(3, counter.get());
    }


    private HighLevelActor<TestUtil.HighLevelActorType, TestIntWithHeadersMessage> allExceptionActor(final AtomicInteger counter,
                                                                                                     final AtomicBoolean sideline,
                                                                                                     final AtomicLong recordedPublishTime,
                                                                                                     final AtomicLong publisherTime,
                                                                                                     final HighLevelActorConfig highLevelActorConfig,
                                                                                                     final ActorSystem actorSystem) {
        return new HighLevelActor<>(TestUtil.HighLevelActorType.EXCEPTION_ACTOR,
                highLevelActorConfig,
                actorSystem,
                null,
                List.of()
        ) {
            @Override
            protected boolean handle(TestIntWithHeadersMessage message, MessageMeta messageMeta) {
                counter.addAndGet(1);
                //Attempt assert
                assertEquals(counter.get(), messageMeta.getDeliveryAttempt().get());


                val now = System.currentTimeMillis();
                if (recordedPublishTime.get() == -1) {
                    //First time publish
                    recordedPublishTime.set(messageMeta.getPublishedAt());
                    assertTrue(messageMeta.getPublishedAt() >= publisherTime.get()); //Same ms can be the case
                } else {
                    //Should be same on next attempt
                    assertEquals(recordedPublishTime.get(), messageMeta.getPublishedAt());
                }

                //PublishedAt assert
                assertTrue(messageMeta.getPublishedAt() <= now); //Same ms can be the case

                //Headers asser
                assertEquals("value", messageMeta.getHeaders().get("key"));
                assertNull(messageMeta.getHeaders().get("blah"));

                throw new RuntimeException();
            }

            @Override
            protected void sideline(TestIntWithHeadersMessage message, MessageMeta messageMeta) {
                assertEquals(3, messageMeta.getDeliveryAttempt().get());
                assertEquals("value", messageMeta.getHeaders().get("key"));
                assertNull(messageMeta.getHeaders().get("blah"));
                sideline.set(true);
            }
        };
    }

    @Value
    static class TestIntWithHeadersMessage implements Message {
        int value;
        String id = UUID.randomUUID().toString();
        Map<String, Object> headersMap = new HashMap<>();

        @Override
        public Map<String, Object> headers() {
            return this.headersMap;
        }

        public void addHeader(String key, Object value) {
            this.headersMap.put(key, value);
        }

        @Override
        public String id() {
            return id;
        }


    }

}
