package io.appform;

import com.codahale.metrics.MetricRegistry;
import io.appform.config.ExecutorConfig;
import io.appform.config.MemqConfig;
import io.appform.memq.ActorSystem;
import io.appform.memq.actor.IActor;
import io.appform.memq.HighLevelActorConfig;
import io.appform.memq.hierarchical.IHierarchicalActor;
import io.appform.memq.observer.ActorObserver;
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
    private final List<IActor<?>> registeredActors;
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
        this.registeredActors = new ArrayList<>();
        this.actorObservers = actorObservers;
        this.retryStrategyFactory = new RetryStrategyFactory();
        this.metricRegistry = metricRegistry;
    }

    //System shutdown
    @Override
    public void close() {
        registeredActors.forEach(IActor::close);
        executors.values().forEach(ExecutorService::shutdown);
    }

    @Override
    public final void register(IActor<?> actor) {
        registeredActors.add(actor);
        actor.start(); //Starting actor during registration
    }

    @Override
    public final void register(IHierarchicalActor<?> actor) {
        registeredActors.add(actor);
        actor.start(); //Starting actor during registration
    }

    @Override
    public final ExecutorService createOrGetExecutorService(HighLevelActorConfig config) {
        val name = config.getExecutorName();
        val threadPoolSize = determineThreadPoolSize(name);
        return executors.computeIfAbsent(name, executor -> executorServiceProvider.threadPool(name, threadPoolSize));
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
    public boolean isRunning() {
        return !registeredActors.isEmpty() && registeredActors.stream().allMatch(IActor::isRunning);
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
