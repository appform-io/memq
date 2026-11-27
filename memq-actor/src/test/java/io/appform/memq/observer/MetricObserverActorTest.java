package io.appform.memq.observer;

import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;
import io.appform.memq.ActorSystem;
import io.appform.memq.Constants;
import io.appform.memq.MemQTestExtension;
import io.appform.memq.actor.ActorOperation;
import io.appform.memq.helper.TestUtil;
import io.appform.memq.helper.message.TestIntMessage;
import lombok.SneakyThrows;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MemQTestExtension.class)
class MetricObserverActorTest {

    @TestTemplate
    @SneakyThrows
    void testMetrics(ActorSystem actorSystem) {
        val actorName = TestUtil.HighLevelActorType.EXCEPTION_ACTOR.name();
        val metricPrefix = "actor." + actorName + ".";
        val totalPostfix = ".total";
        val successPostfix = ".success";
        val failedPostfix = ".failed";
        val queueWaitPostfix = ".queue_wait";
        val counter = new AtomicInteger();
        val sideline = new AtomicBoolean();
        val actorConfig = TestUtil.noRetryActorConfig(Constants.SINGLE_PARTITION, false);
        val actor = TestUtil.allExceptionActor(counter, sideline,
                actorConfig, actorSystem);
        actor.publish(new TestIntMessage(1));
        Awaitility.await()
                .timeout(Duration.ofMinutes(1))
                .catchUncaughtExceptions()
                .until(actor::isEmpty); //Wait until all messages are processed
        val metrics = actorSystem.metricRegistry().getMetrics();
        assertEquals(22, metrics.size());
        assertEquals(1, ((Timer) metrics.get(metricPrefix + ActorOperation.CONSUME.name() + queueWaitPostfix)).getCount());
        assertEquals(1, ((Meter) metrics.get(metricPrefix + ActorOperation.PUBLISH.name() + totalPostfix)).getCount());
        assertEquals(1, ((Meter) metrics.get(metricPrefix + ActorOperation.PUBLISH.name() + successPostfix)).getCount());
        assertEquals(0, ((Meter) metrics.get(metricPrefix + ActorOperation.PUBLISH.name() + failedPostfix)).getCount());
        assertEquals(1, ((Meter) metrics.get(metricPrefix + ActorOperation.VALIDATE.name() + totalPostfix)).getCount());
        assertEquals(1, ((Meter) metrics.get(metricPrefix + ActorOperation.VALIDATE.name() + successPostfix)).getCount());
        assertEquals(0, ((Meter) metrics.get(metricPrefix + ActorOperation.VALIDATE.name() + failedPostfix)).getCount());
        assertEquals(1, ((Meter) metrics.get(metricPrefix + ActorOperation.HANDLE_EXCEPTION.name() + totalPostfix)).getCount());
        assertEquals(1, ((Meter) metrics.get(metricPrefix + ActorOperation.HANDLE_EXCEPTION.name() + successPostfix)).getCount());
        assertEquals(0, ((Meter) metrics.get(metricPrefix + ActorOperation.HANDLE_EXCEPTION.name() + failedPostfix)).getCount());
        assertEquals(1, ((Meter) metrics.get(metricPrefix + ActorOperation.CONSUME.name() + totalPostfix)).getCount());
        assertEquals(0, ((Meter) metrics.get(metricPrefix + ActorOperation.CONSUME.name() + successPostfix)).getCount());
        assertEquals(1, ((Meter) metrics.get(metricPrefix + ActorOperation.CONSUME.name() + failedPostfix)).getCount());
        assertEquals(actorName, actor.getType().name());
    }

    @TestTemplate
    @SneakyThrows
    void testNoMetrics(ActorSystem actorSystem) {
        val counter = new AtomicInteger();
        val sideline = new AtomicBoolean();
        val actorName = TestUtil.HighLevelActorType.EXCEPTION_ACTOR.name();
        val actorConfig = TestUtil.noRetryActorConfig(Constants.SINGLE_PARTITION, true);
        val actor = TestUtil.allExceptionActor(counter, sideline,
                                               actorConfig, actorSystem);
        actor.publish(new TestIntMessage(1));

        val metrics = actorSystem.metricRegistry().getMetrics();
        assertEquals(0, metrics.size());
        assertEquals(actorName, actor.getType().name());
    }
}
