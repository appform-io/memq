package io.appform.memq.actor;

import com.google.common.base.Stopwatch;
import io.appform.memq.helper.message.TestIntMessage;
import io.appform.memq.observer.ActorObserver;
import io.appform.memq.observer.ActorObserverContext;
import io.appform.memq.observer.ObserverMessageMeta;
import io.appform.memq.retry.config.NoRetryConfig;
import io.appform.memq.retry.impl.NoRetryStrategy;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
@Slf4j
class ActorTest {

    static final int THREADPOOL_SIZE = 10;

    @Test
    @SneakyThrows
    void testSuccessSinglePartitionAsyncDispacther() {
        testSuccess(1, DispatcherType.ASYNC_ISOLATED);
    }

    @Test
    @SneakyThrows
    void testSuccessMultiPartitionAsyncDispacther() {
        testSuccess(4, DispatcherType.ASYNC_ISOLATED);
    }


    @Test
    @SneakyThrows
    void testSuccessSinglePartitionSyncDispacther() {
        testSuccess(1, DispatcherType.SYNC);
    }

    @Test
    @SneakyThrows
    void testSuccessMultiPartitionSyncDispacther() {
        testSuccess(4, DispatcherType.SYNC);
    }


    @SneakyThrows
    void testSuccess(int partition, DispatcherType dispatcherType) {
        val sum = new AtomicInteger(0);
        val tp = Executors.newFixedThreadPool(THREADPOOL_SIZE);
        val tc = Executors.newFixedThreadPool(THREADPOOL_SIZE);
        try (val a = adder(sum, partition, tc, dispatcherType)) {
            a.start();
            val s = Stopwatch.createStarted();
            IntStream.rangeClosed(1, 10)
                    .forEach(i -> IntStream.rangeClosed(1, 1_000)
                            .forEach(j -> tp.submit(() -> a.publish(new TestIntMessage(1)))));
            Awaitility.await()
                    .timeout(Duration.ofMinutes(1))
                    .catchUncaughtExceptions()
                    .until(a::isEmpty);
            log.info("Test took {} ms", s.elapsed().toMillis());
            assertEquals(10_000, sum.get());
        }
        finally {
            tp.shutdownNow();
            tc.shutdownNow();
        }
    }

    @Test
    @SneakyThrows
    void testObserverMessageMetaHasCorrectTimestamps() {
        val captured = new AtomicReference<ObserverMessageMeta>();
        val observer = new ActorObserver(null) {
            @Override
            public void initialize(Actor<? extends io.appform.memq.actor.Message> actor) {
                // no-op
            }

            @Override
            public boolean execute(ActorObserverContext<? extends io.appform.memq.actor.Message> context,
                                   BooleanSupplier supplier) {
                if (context.getOperation() == ActorOperation.CONSUME) {
                    captured.set(context.getMessageMeta());
                }
                return proceed(context, supplier);
            }
        };
        val tc = Executors.newFixedThreadPool(1);
        val message = new TestIntMessage(1);
        val beforePublish = System.currentTimeMillis();
        try (val a = new Actor<TestIntMessage>("MetaChecker", tc,
                (m, meta) -> true, (m, meta) -> true, (m, meta) -> { }, (m, meta, t) -> { },
                new NoRetryStrategy(new NoRetryConfig()), DispatcherType.ASYNC_ISOLATED,
                1, Integer.MAX_VALUE, Integer.MAX_VALUE, m -> 0, List.of(observer))) {
            a.start();
            a.publish(message);
            Awaitility.await()
                    .timeout(Duration.ofSeconds(30))
                    .until(() -> captured.get() != null);
        }
        finally {
            tc.shutdownNow();
        }
        val meta = captured.get();
        assertEquals(message.id(), meta.getId());
        assertEquals(message.validTill(), meta.getValidTill());
        assertTrue(meta.getPublishedAt() >= beforePublish);
        assertTrue(meta.getPublishedAt() <= System.currentTimeMillis());
    }

    static Actor<TestIntMessage> adder(final AtomicInteger sum, int partition, ExecutorService tc, DispatcherType dispatcherType) {
        return new Actor<>("Adder",
                tc,
                (message, messageMeta) -> true,
                (message, messageMeta) -> {
                    sum.addAndGet(message.getValue());
                    return true;
                },
                (message, messageMeta) -> {
                },
                (message, messageMeta, throwable) -> {
                },
                new NoRetryStrategy(new NoRetryConfig()),
                dispatcherType,
                partition,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                message -> Math.absExact(message.id().hashCode()) % partition,
                new ArrayList<>());
    }

}