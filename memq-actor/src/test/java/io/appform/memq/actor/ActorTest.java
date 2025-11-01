package io.appform.memq.actor;

import com.google.common.base.Stopwatch;
import io.appform.memq.helper.message.TestIntMessage;
import io.appform.memq.retry.config.NoRetryConfig;
import io.appform.memq.retry.impl.NoRetryStrategy;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
@Slf4j
class ActorTest {

    static final int THREADPOOL_SIZE = 10;

    @Test
    @SneakyThrows
    void testSuccessSinglePartition() {
        testSuccess(1);
    }

    @Test
    @SneakyThrows
    void testSuccessMultiPartition() {
        testSuccess(4);
    }


    @SneakyThrows
    void testSuccess(int partition) {
        val sum = new AtomicInteger(0);
        val tp = Executors.newFixedThreadPool(THREADPOOL_SIZE);
        val tc = Executors.newFixedThreadPool(THREADPOOL_SIZE);
        try (val a = adder(sum, partition, tc)) {
            a.start();
            val s = Stopwatch.createStarted();
            IntStream.rangeClosed(1, 10)
                    .forEach(i -> IntStream.rangeClosed(1, 1_000_00)
                            .forEach(j -> tp.submit(() -> a.publish(new TestIntMessage(1)))));
            Awaitility.await()
                    .timeout(Duration.ofMinutes(1))
                    .catchUncaughtExceptions()
                    .until(a::isEmpty);
            log.info("Test took {} ms", s.elapsed().toMillis());
            assertEquals(10_00000, sum.get());
        }
        finally {
            tp.shutdownNow();
            tc.shutdownNow();
        }
    }

    static Actor<TestIntMessage> adder(final AtomicInteger sum, int partition, ExecutorService tc) {
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
                partition,
                Long.MAX_VALUE,
                Integer.MAX_VALUE,
                message -> Math.absExact(message.id().hashCode()) % partition,
                new ArrayList<>());
    }

}