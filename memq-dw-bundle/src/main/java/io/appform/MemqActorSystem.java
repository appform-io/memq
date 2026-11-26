package io.appform;

import com.codahale.metrics.MetricRegistry;
import io.appform.config.ExecutorConfig;
import io.appform.config.MemqConfig;
import io.appform.memq.ActorSystem;
import io.appform.memq.actor.Actor;
import io.appform.memq.HighLevelActorConfig;
import io.appform.memq.actor.DispatcherType;
import io.appform.memq.observer.ActorObserver;
import io.appform.memq.retry.RetryStrategy;
import io.appform.memq.retry.RetryStrategyFactory;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class MemqActorSystem implements ActorSystem, Managed {

    private final ConcurrentHashMap<String, ExecutorService> executors;
    private final ConcurrentHashMap<String, Set<String>> executorQueues;
    private final ExecutorServiceProvider executorServiceProvider;
    private final Map<String, ExecutorConfig> executorConfigMap;
    private final List<Actor<?>> registeredActors;
    private final RetryStrategyFactory retryStrategyFactory;
    private final MetricRegistry metricRegistry;
    private final List<ActorObserver> actorObservers;

    public MemqActorSystem(MemqConfig memqConfig) {
        this(memqConfig, (name, parallel) -> Executors.newFixedThreadPool(parallel), new ArrayList<>(), new MetricRegistry());
    }

    public MemqActorSystem(
            MemqConfig memqConfig,
            ExecutorServiceProvider executorServiceProvider,
            List<ActorObserver> actorObservers,
            MetricRegistry metricRegistry) {
        this.executorServiceProvider = executorServiceProvider;
        this.executorConfigMap = memqConfig.getExecutors().stream()
                .collect(Collectors.toMap(ExecutorConfig::getName, Function.identity()));
        this.executors = new ConcurrentHashMap<>();
        this.executorQueues = new ConcurrentHashMap<>();
        this.registeredActors = new ArrayList<>();
        this.actorObservers = actorObservers;
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
    public final ExecutorService createOrGetExecutorService(String name, HighLevelActorConfig config) {
        val executorName = config.getExecutorName();
        val threadPoolSize = determineThreadPoolSize(executorName);
        val queues = executorQueues.computeIfAbsent(executorName, k -> ConcurrentHashMap.newKeySet());
        queues.add(name);
        log.info("Queue '{}' using executor '{}' (threadPoolSize={}), executor shared by {} queues: {}", name, executorName, threadPoolSize, queues.size(), queues);
        return executors.computeIfAbsent(executorName,
                                         executor -> executorServiceProvider.threadPool(executorName, threadPoolSize));
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
    public List<ActorObserver> registeredObservers() {
        return List.copyOf(this.actorObservers);
    }

    @Override
    public DispatcherType registeredDispatcher(String name) {
        return DispatcherType.ASYNC_ISOLATED;
    }

    @Override
    public boolean isRunning() {
        return !registeredActors.isEmpty() && registeredActors.stream().allMatch(Actor::isRunning);
    }

    @Override
    public int maxConcurrencyPerPartition(HighLevelActorConfig highLevelActorConfig) {
        val maxPoolSize = determineThreadPoolSize(highLevelActorConfig.getExecutorName());
        int maxPoolSizePerPartition = (int) Math.ceil((double) maxPoolSize / highLevelActorConfig.getPartitions());
        return Math.min(maxPoolSizePerPartition, highLevelActorConfig.getMaxConcurrencyPerPartition());
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

    private int determineThreadPoolSize(String name) {
        return executorConfigMap.getOrDefault(name, ExecutorConfig.builder()
                                                        .name(name)
                                                        .threadPoolSize(Constants.DEFAULT_THREADPOOL)
                                                        .build()
                                            ).getThreadPoolSize();
    }
}
