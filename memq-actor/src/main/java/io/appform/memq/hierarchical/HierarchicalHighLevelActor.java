package io.appform.memq.hierarchical;


import io.appform.memq.ActorSystem;
import io.appform.memq.actor.Message;
import io.appform.memq.actor.MessageMeta;
import io.appform.memq.hierarchical.tree.key.HierarchicalRoutingKey;
import io.appform.memq.observer.ActorObserver;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.ToIntFunction;

@Slf4j
public abstract class HierarchicalHighLevelActor<MessageType extends Enum<MessageType>, M extends Message> {


    @Getter
    private final MessageType type;
    @Getter
    private final HierarchicalActor<MessageType, M> actor;

    @SuppressWarnings("unused")
    protected HierarchicalHighLevelActor(
            MessageType type,
            HierarchicalActorConfig highLevelActorConfig,
            ActorSystem actorSystem) {
        this(type, highLevelActorConfig, actorSystem, null, List.of());
    }

    protected HierarchicalHighLevelActor(
            MessageType type,
            HierarchicalActorConfig highLevelActorConfig,
            ActorSystem actorSystem,
            ToIntFunction<M> partitioner) {
        this(type, highLevelActorConfig, actorSystem, partitioner, List.of());
    }

    protected HierarchicalHighLevelActor(
            MessageType type,
            HierarchicalActorConfig highLevelActorConfig,
            ActorSystem actorSystem,
            List<ActorObserver> observers) {
        this(type, highLevelActorConfig, actorSystem, null, observers);
    }

    protected HierarchicalHighLevelActor(
            MessageType type,
            HierarchicalActorConfig highLevelActorConfig,
            ActorSystem actorSystem,
            ToIntFunction<M> partitioner,
            List<ActorObserver> observers) {
        this.type = type;
        this.actor = new HierarchicalActor<>(type, highLevelActorConfig, actorSystem, this::handle, this::sideline, partitioner, observers);
        actorSystem.register(actor);
    }

    protected abstract boolean handle(final M message, MessageMeta messageMeta);

    protected void sideline(final M message, MessageMeta messageMeta) {
        log.warn("skipping sideline for actor:{} message:{}", type.name(), message);
    }

    public final boolean publish(final M message) {
        return actor.publish(message);
    }

    public final boolean publish(final HierarchicalRoutingKey<String> routingKey, final M message) {
        return actor.publish(routingKey, message);
    }


    public final void purge() {
        actor.purge();
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
