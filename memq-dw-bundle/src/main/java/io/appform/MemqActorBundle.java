package io.appform;

import io.appform.config.MemqConfig;
import io.appform.memq.observer.ActorObserver;
import io.dropwizard.Configuration;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
public abstract class MemqActorBundle<T extends Configuration> implements ConfiguredBundle<T> {

    @Getter
    private MemqActorSystem memqActorSystem;
    private final List<ActorObserver> observers = new ArrayList<>();

    protected MemqActorBundle() {
    }

    protected abstract MemqConfig config(T t);

    protected abstract ExecutorServiceProvider executorServiceProvider(T t);

    @Override
    public void initialize(Bootstrap<?> bootstrap) {

    }

    @Override
    public void run(T t, Environment environment) {
        MemqConfig memqConfig = config(t);
        Objects.requireNonNull(memqConfig, "Null memq config provided");
        ExecutorServiceProvider executorServiceProvider = executorServiceProvider(t);
        Objects.requireNonNull(executorServiceProvider, "Null executor service provider provided");
        this.memqActorSystem = new MemqActorSystem(memqConfig,
                                                   executorServiceProvider,
                                                   this.observers,
                                                   environment.metrics());
        environment.lifecycle().manage(memqActorSystem);
    }


    public void registerObserver(final ActorObserver observer) {
        if (null == observer) {
            return;
        }
        this.observers.add(observer);
        log.info("Registered observer: " + observer.getClass().getSimpleName());
    }

}