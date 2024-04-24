package io.appform;

import com.codahale.metrics.MetricRegistry;
import io.appform.config.ExecutorConfig;
import io.appform.config.MemqConfig;
import io.appform.memq.ActorSystem;
import io.appform.memq.actor.Actor;
import io.appform.memq.actor.HighLevelActorConfig;
import io.appform.memq.retry.RetryStrategy;
import io.appform.memq.retry.RetryStrategyFactory;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class MemqActorSystem implements ActorSystem, Managed {

    private final ConcurrentHashMap<String, ExecutorService> executors;
    private final ExecutorServiceProvider executorServiceProvider;
    private final Map<String, ExecutorConfig> executorConfigMap;
    private final List<Actor<?>> registeredActors;
    private final RetryStrategyFactory retryStrategyFactory;
    private final MetricRegistry metricRegistry;

    public MemqActorSystem(MemqConfig memqConfig) {
        this(memqConfig, (name, parallel, queueSize) -> Executors.newFixedThreadPool(parallel), new MetricRegistry());
    }

    public MemqActorSystem(
            MemqConfig memqConfig,
            ExecutorServiceProvider executorServiceProvider,
            MetricRegistry metricRegistry) {
        this.executorServiceProvider = executorServiceProvider;
        this.executorConfigMap = memqConfig.getExecutors().stream()
                .collect(Collectors.toMap(ExecutorConfig::getName, Function.identity()));
        this.executors = new ConcurrentHashMap<>();
        this.registeredActors = new ArrayList<>();
        this.retryStrategyFactory = new RetryStrategyFactory();
        this.metricRegistry = metricRegistry;
    }

    //System shutdown
    @Override
    public void close() {
        registeredActors.forEach(Actor::close);
        executors.values().forEach(ExecutorService::shutdown);
    }

    @Override
    public final void register(Actor<?> actor) {
        registeredActors.add(actor);
        actor.start(); //Starting actor during registration
    }

    @Override
    public final ExecutorService createOrGetExecutorService(HighLevelActorConfig config) {
        val name = config.getExecutorName();
        val threadPoolSize = determineExecutorConfig(name).getThreadPoolSize();
        val queueSize = determineExecutorConfig(name).getQueueSize();
        return executors.computeIfAbsent(name, executor -> executorServiceProvider.threadPool(name, threadPoolSize,
                queueSize));
    }

    @Override
    public final RetryStrategy createRetryer(HighLevelActorConfig highLevelActorConfig) {
        return retryStrategyFactory.create(highLevelActorConfig.getRetryConfig());
    }

    @Override
    public MetricRegistry metricRegistry() {
        return metricRegistry;
    }

    @Override
    public boolean isRunning() {
        return !registeredActors.isEmpty() && registeredActors.stream().allMatch(Actor::isRunning);
    }

    @Override
    public void start() {
        log.info("Started Memq Actor System"); //Note: None of the actors are register for now, actors will be
        // started during registration
    }

    @Override
    public void stop() {
        this.close();
        log.info("Closed Memq Actor System");
    }

    private ExecutorConfig determineExecutorConfig(String name) {
        return executorConfigMap.getOrDefault(name, ExecutorConfig.builder()
                .name(name)
                .threadPoolSize(Constants.DEFAULT_THREADPOOL)
                .build()
        );
    }
}
