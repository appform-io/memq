package io.appform.memq.helper;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.Lists;
import io.appform.memq.ActorSystem;
import io.appform.memq.HighLevelActor;
import io.appform.memq.actor.Actor;
import io.appform.memq.HighLevelActorConfig;
import io.appform.memq.actor.DispatcherType;
import io.appform.memq.exceptionhandler.config.ExceptionHandlerConfig;
import io.appform.memq.exceptionhandler.config.SidelineConfig;
import io.appform.memq.helper.message.TestIntMessage;
import io.appform.memq.actor.MessageMeta;
import io.appform.memq.observer.ActorObserver;
import io.appform.memq.retry.RetryStrategy;
import io.appform.memq.retry.RetryStrategyFactory;
import io.appform.memq.retry.config.NoRetryConfig;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.awaitility.Awaitility;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class TestUtil {

    public enum HighLevelActorType {
        EXCEPTION_ACTOR,
        BLOCKING_ACTOR,
        ;
    }

    public static final String GLOBAL_EXECUTOR_SERVICE_GROUP = "global";
    public static final int DEFAULT_THREADPOOL_SIZE = 2;
    public static final DispatcherType DEFAULT_DISPATCHER = DispatcherType.ASYNC_ISOLATED;

    public static ActorSystem actorSystem(ExecutorService tp, DispatcherType dispatcherType) {
        val metricRegistry = new MetricRegistry();
        return new ActorSystem() {
            private final RetryStrategyFactory retryStrategyFactory = new RetryStrategyFactory();
            private final List<Actor<?>> registeredActors = Lists.newArrayList();

            @Override
            public void register(Actor<?> actor) {
                registeredActors.add(actor);
                actor.start();
            }

            @Override
            public ExecutorService createOrGetExecutorService(String name, HighLevelActorConfig config) {
                return tp;
            }

            @Override
            public RetryStrategy createRetryer(HighLevelActorConfig highLevelActorConfig) {
                return retryStrategyFactory.create(highLevelActorConfig.getRetryConfig());
            }

            @Override
            public MetricRegistry metricRegistry() {
                return metricRegistry ;
            }

            @Override
            public List<ActorObserver> registeredObservers() {
                return List.of();
            }

            @Override
            public DispatcherType registeredDispatcher(String name) {
                return dispatcherType;
            }

            @Override
            public boolean isRunning() {
                return !registeredActors.isEmpty() && registeredActors.stream().allMatch(Actor::isRunning);
            }

            @Override
            public int maxConcurrencyPerPartition(HighLevelActorConfig highLevelActorConfig) {
                //SYNC dispatcher requires maxConcurrency == maxSize, so cap is not applicable
                if (dispatcherType != DispatcherType.SYNC && tp instanceof ThreadPoolExecutor tpe) {
                    //Floor so that sum across partitions never exceeds pool size (min 1 per partition)
                    val perPartition = Math.max(1, tpe.getMaximumPoolSize() / highLevelActorConfig.getPartitions());
                    return Math.min(perPartition, highLevelActorConfig.getMaxConcurrencyPerPartition());
                }
                return highLevelActorConfig.getMaxConcurrencyPerPartition();
            }

            @Override
            public void close() {
                registeredActors.forEach(Actor::close);
            }
        };
    }

    public static HighLevelActor<HighLevelActorType, TestIntMessage> blockingActor(final AtomicInteger counter,
                                                                                   final AtomicBoolean sideline,
                                                                                   final AtomicBoolean blockConsume,
                                                                                   final HighLevelActorConfig highLevelActorConfig,
                                                                                   final ActorSystem actorSystem,
                                                                                   final List<ActorObserver> observers) {
        return new HighLevelActor<>(HighLevelActorType.BLOCKING_ACTOR,
                highLevelActorConfig,
                actorSystem,
                observers
        ) {
            @Override
            protected boolean handle(TestIntMessage message, MessageMeta messageMeta) {
                counter.addAndGet(message.getValue());
                log.info("Started blocked actor handle with counter: {}", counter.get());
                while(blockConsume.get()) {
                    Awaitility.waitAtMost(Duration.ofMillis(20));
                }
                return true;
            }

            @Override
            protected void sideline(TestIntMessage message, MessageMeta messageMeta) {
                sideline.set(true);
            }
        };
    }

    public static HighLevelActor<HighLevelActorType,TestIntMessage> allExceptionActor(final AtomicInteger counter,
                                     final AtomicBoolean sideline,
                                     final HighLevelActorConfig highLevelActorConfig,
                                     final ActorSystem actorSystem) {
        return new HighLevelActor<>(HighLevelActorType.EXCEPTION_ACTOR,
                highLevelActorConfig,
                actorSystem,
                null,
                List.of()
        ) {
            @Override
            protected boolean handle(TestIntMessage message, MessageMeta messageMeta) {
                counter.addAndGet(message.getValue());
                throw new RuntimeException();
            }

            @Override
            protected void sideline(TestIntMessage message, MessageMeta messageMeta) {
                sideline.set(true);
            }
        };
    }

    public static HighLevelActor<HighLevelActorType,TestIntMessage> successAfterNumberOfExceptionsActor(final AtomicInteger attemptCounter,
                                                                                      final AtomicBoolean sideline,
                                                                                      final HighLevelActorConfig highLevelActorConfig,
                                                                                      final ActorSystem actorSystem,
                                                                                                        final int numberOfExceptions) {
        return new HighLevelActor<>(HighLevelActorType.EXCEPTION_ACTOR,
                highLevelActorConfig,
                actorSystem,
                null,
                List.of()
        ) {
            @Override
            protected boolean handle(TestIntMessage message, MessageMeta messageMeta) {
                if(messageMeta.getDeliveryAttempt().get() < numberOfExceptions) {
                    throw new RuntimeException();
                }
                attemptCounter.addAndGet(messageMeta.getDeliveryAttempt().get());
                return true;
            }

            @Override
            protected void sideline(TestIntMessage message, MessageMeta messageMeta) {
                sideline.set(true);
            }
        };
    }



    public static HighLevelActorConfig noRetryActorConfig(int partition, ExceptionHandlerConfig exceptionHandlerConfig) {
        return noRetryActorConfig(partition, false, exceptionHandlerConfig);
    }

    public static HighLevelActorConfig noRetryActorConfig(int partition, boolean metricDisabled) {
        return noRetryActorConfig(partition, metricDisabled, new SidelineConfig());
    }

    public static HighLevelActorConfig noRetryActorConfig(int partition) {
        return noRetryActorConfig(partition, false, new SidelineConfig());
    }

    public static HighLevelActorConfig noRetryActorConfig(int partition,
                                                          boolean metricDisabled,
                                                          ExceptionHandlerConfig exceptionHandlerConfig) {
        return noRetryActorConfig(partition, metricDisabled, exceptionHandlerConfig, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    public static HighLevelActorConfig noRetryActorConfig(int partition,
                                                          boolean metricDisabled,
                                                          int maxSizePerPartition) {
        return noRetryActorConfig(partition, metricDisabled, new SidelineConfig(), maxSizePerPartition, maxSizePerPartition);
    }

    public static HighLevelActorConfig noRetryActorConfig(int partition,
                                                          boolean metricDisabled,
                                                          long maxSizePerPartition,
                                                          int maxConcurrencyPerPartition) {
        return noRetryActorConfig(partition, metricDisabled, new SidelineConfig(), maxSizePerPartition, maxConcurrencyPerPartition);
    }

    public static HighLevelActorConfig noRetryActorConfig(int partition,
                                                          boolean metricDisabled,
                                                          ExceptionHandlerConfig exceptionHandlerConfig,
                                                          long maxSizePerPartition,
                                                          int maxConcurrencyPerPartition) {
        return HighLevelActorConfig.builder()
                .partitions(partition)
                .maxSizePerPartition(maxSizePerPartition)
                .retryConfig(new NoRetryConfig())
                .executorName(GLOBAL_EXECUTOR_SERVICE_GROUP)
                .metricDisabled(metricDisabled)
                .maxConcurrencyPerPartition(maxConcurrencyPerPartition)
                .exceptionHandlerConfig(exceptionHandlerConfig)
                .build();
    }
}
