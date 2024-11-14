package io.appform.memq.hierarchical;

import io.appform.memq.ActorSystem;
import io.appform.memq.HighLevelActor;
import io.appform.memq.actor.Message;
import io.appform.memq.actor.MessageMeta;
import io.appform.memq.hierarchical.tree.key.RoutingKey;
import io.appform.memq.observer.ActorObserver;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.ToIntFunction;

@Getter
@EqualsAndHashCode
public class HierarchicalOperationWorker<MessageType extends Enum<MessageType>, M extends Message>
        extends HighLevelActor<MessageType, M> {

    private final RoutingKey routingKey;
    private final BiFunction<M, MessageMeta, Boolean> messageHandler;
    private final BiConsumer<M, MessageMeta> sidelineHandler;

    public HierarchicalOperationWorker(final MessageType messageType,
                                       final HierarchicalOperationWorkerConfig workerConfig,
                                       final HierarchialHighLevelActorConfig hierarchicalActorConfig,
                                       final RoutingKey routingKey,
                                       final ActorSystem actorSystem,
                                       final BiFunction<M, MessageMeta, Boolean> messageHandler,
                                       final BiConsumer<M, MessageMeta> sidelineHandler,
                                       final ToIntFunction<M> partitioner,
                                       final List<ActorObserver> observers) {
        super(messageType,
                HierarchicalRouterUtils.hierarchicalActorConfig(messageType, routingKey, workerConfig, hierarchicalActorConfig),
                actorSystem, partitioner, observers);
        this.routingKey = routingKey;
        this.messageHandler = messageHandler;
        this.sidelineHandler = sidelineHandler;
    }

    @Override
    protected boolean handle(M message, MessageMeta messageMeta) {
        return messageHandler.apply(message, messageMeta);
    }

    @Override
    protected void sideline(M message, MessageMeta messageMeta) {
        sidelineHandler.accept(message, messageMeta);
    }

    public final void close() {
        actor.close();
    }
}
