package io.appform.memq;


import io.appform.memq.actor.Actor;
import io.appform.memq.actor.Message;
import io.appform.memq.actor.MessageMeta;
import io.appform.memq.observer.ActorObserver;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.ToIntFunction;

@Slf4j
public abstract class HighLevelActor<T extends Enum<T>, M extends Message> {

    @Getter
    private final T type;
    @Getter
    private final String name;
    private final Actor<M> actor;

    @SuppressWarnings("unused")
    protected HighLevelActor(
            T type,
            HighLevelActorConfig highLevelActorConfig,
            ActorSystem actorSystem) {
        this(type.name(), type, highLevelActorConfig, actorSystem);
    }

    protected HighLevelActor(String name,
            T type,
            HighLevelActorConfig highLevelActorConfig,
            ActorSystem actorSystem) {
        this(name, type, highLevelActorConfig, actorSystem, null, List.of());
    }

    protected HighLevelActor(
            T type,
            HighLevelActorConfig highLevelActorConfig,
            ActorSystem actorSystem,
            ToIntFunction<M> partitioner) {
        this(type.name(), type, highLevelActorConfig, actorSystem, partitioner);
    }

    protected HighLevelActor(String name,
            T type,
            HighLevelActorConfig highLevelActorConfig,
            ActorSystem actorSystem,
            ToIntFunction<M> partitioner) {
        this(name, type, highLevelActorConfig, actorSystem, partitioner, List.of());
    }

    protected HighLevelActor(
            T type,
            HighLevelActorConfig highLevelActorConfig,
            ActorSystem actorSystem,
            List<ActorObserver> observers) {
        this(type.name(), type, highLevelActorConfig, actorSystem, observers);
    }

    protected HighLevelActor(String name,
            T type,
            HighLevelActorConfig highLevelActorConfig,
            ActorSystem actorSystem,
            List<ActorObserver> observers) {
        this(name, type, highLevelActorConfig, actorSystem, null, observers);
    }

    protected HighLevelActor(
            T type,
            HighLevelActorConfig highLevelActorConfig,
            ActorSystem actorSystem,
            ToIntFunction<M> partitioner,
            List<ActorObserver> observers) {
        this(type.name(), type, highLevelActorConfig, actorSystem, partitioner, observers);
    }

    protected HighLevelActor(String name,
            T type,
            HighLevelActorConfig highLevelActorConfig,
            ActorSystem actorSystem,
            ToIntFunction<M> partitioner,
            List<ActorObserver> observers) {
        this.type = type;
        this.name = name;
        this.actor = new Actor<>(name,
                actorSystem.createOrGetExecutorService(highLevelActorConfig),
                actorSystem.expiryValidator(highLevelActorConfig),
                this::handle,
                this::sideline,
                actorSystem.createExceptionHandler(highLevelActorConfig, this::sideline),
                actorSystem.createRetryer(highLevelActorConfig),
                actorSystem.registeredDispatcher(name),
                highLevelActorConfig.getPartitions(),
                highLevelActorConfig.getMaxSizePerPartition(),
                highLevelActorConfig.getMaxConcurrencyPerPartition(),
                actorSystem.partitioner(highLevelActorConfig, partitioner),
                actorSystem.observers(name, highLevelActorConfig, observers));
        actorSystem.register(actor);
    }

    protected abstract boolean handle(final M message, MessageMeta messageMeta);

    protected void sideline(final M message, MessageMeta messageMeta) {
        log.warn("skipping sideline for actor:{} message:{}", type.name(), message);
    }

    public final void purge() {
        actor.purge();
    }

    public final boolean publish(final M message) {
        return actor.publish(message);
    }

    public final long size() {
        return actor.size();
    }

    public final long inFlight() {
        return actor.inFlight();
    }

    public final boolean isEmpty() {
       return actor.isEmpty();
    }

    public final boolean isRunning() {
        return actor.isRunning();
    }

}
