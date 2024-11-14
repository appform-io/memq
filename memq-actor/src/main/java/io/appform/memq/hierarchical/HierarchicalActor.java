package io.appform.memq.hierarchical;

import io.appform.memq.ActorSystem;
import io.appform.memq.actor.Message;
import io.appform.memq.actor.MessageMeta;
import io.appform.memq.hierarchical.tree.HierarchicalDataStoreSupplierTree;
import io.appform.memq.hierarchical.tree.HierarchicalTreeConfig;
import io.appform.memq.hierarchical.tree.key.HierarchicalRoutingKey;
import io.appform.memq.hierarchical.tree.key.RoutingKey;
import io.appform.memq.observer.ActorObserver;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.ToIntFunction;

@Slf4j
public class HierarchicalActor<MessageType extends Enum<MessageType>, M extends Message> implements IHierarchicalActor<M> {

    public static final RoutingKey EMPTY_ROUTING_KEY = RoutingKey.builder().build();

    private final HierarchicalTreeConfig<HierarchialHighLevelActorConfig, String, HierarchicalOperationWorkerConfig> hierarchicalTreeConfig;
    private final MessageType messageType;
    private final ActorSystem actorSystem;
    private final ToIntFunction<M> partitioner;
    private final List<ActorObserver> observers;
    private final BiFunction<M, MessageMeta, Boolean> messageHandler;
    private final BiConsumer<M, MessageMeta> sidelineHandler;

    @Getter
    private HierarchicalDataStoreSupplierTree<
            HierarchicalOperationWorkerConfig,
            HierarchialHighLevelActorConfig,
            MessageType,
            HierarchicalOperationWorker<MessageType, ? extends Message>> worker;


    public HierarchicalActor(MessageType messageType,
                             HierarchialHighLevelActorConfig hierarchicalActorConfig,
                             ActorSystem actorSystem,
                             BiFunction<M, MessageMeta, Boolean> messageHandler,
                             BiConsumer<M, MessageMeta> sidelineHandler,
                             ToIntFunction<M> partitioner,
                             List<ActorObserver> observers) {
        this.messageType = messageType;
        this.hierarchicalTreeConfig = new HierarchicalTreeConfig<>(hierarchicalActorConfig, hierarchicalActorConfig.getChildrenData());
        this.actorSystem = actorSystem;
        this.messageHandler = messageHandler;
        this.sidelineHandler = sidelineHandler;
        this.partitioner = partitioner;
        this.observers = observers;
    }

    @Override
    public void start() {
        log.info("Starting all workers");
        this.initializeRouter();
    }

    @Override
    public void close() {
        log.info("Closing all workers");
        worker.traverse(hierarchicalOperationWorker -> {
            log.info("Closing worker: {} {}", hierarchicalOperationWorker.getType(), hierarchicalOperationWorker.getRoutingKey().getRoutingKey());
            hierarchicalOperationWorker.close();
        });
    }

    @Override
    public void purge() {
        log.info("Purging all workers");
        worker.traverse(hierarchicalOperationWorker -> {
            log.info("Purging worker: {} {}", hierarchicalOperationWorker.getType(), hierarchicalOperationWorker.getRoutingKey().getRoutingKey());
            hierarchicalOperationWorker.purge();
        });
    }

    @Override
    public boolean publish(final M message) {
        return publishActor(EMPTY_ROUTING_KEY).publish(message);
    }

    @Override
    public long size() {
        log.info("Size of all workers");
        val atomicLong = new AtomicLong();
        worker.traverse(hierarchicalOperationWorker -> {
            log.info("Size of worker: {} {}", hierarchicalOperationWorker.getType(), hierarchicalOperationWorker.getRoutingKey().getRoutingKey());
            atomicLong.getAndAdd(hierarchicalOperationWorker.size());
        });
        return atomicLong.get();
    }

    @Override
    public long inFlight() {
        log.info("inFlight Size of all workers");
        val atomicLong = new AtomicLong();
        worker.traverse(hierarchicalOperationWorker -> {
            log.info("inFlight Size of worker: {} {}", hierarchicalOperationWorker.getType(), hierarchicalOperationWorker.getRoutingKey().getRoutingKey());
            atomicLong.getAndAdd(hierarchicalOperationWorker.inFlight());
        });
        return atomicLong.get();
    }

    @Override
    public boolean isEmpty() {
        log.info("isEmpty all workers");
        val atomicBoolean = new AtomicBoolean();
        worker.traverse(hierarchicalOperationWorker -> {
            log.info("isEmpty worker: {} {}", hierarchicalOperationWorker.getType(), hierarchicalOperationWorker.getRoutingKey().getRoutingKey());
            atomicBoolean.set(atomicBoolean.get() && hierarchicalOperationWorker.isEmpty());
        });
        return atomicBoolean.get();
    }

    @Override
    public boolean isRunning() {
        log.info("isRunning all workers");
        val atomicBoolean = new AtomicBoolean();
        worker.traverse(hierarchicalOperationWorker -> {
            log.info("isRunning worker: {} {}", hierarchicalOperationWorker.getType(), hierarchicalOperationWorker.getRoutingKey().getRoutingKey());
            atomicBoolean.set(atomicBoolean.get() && hierarchicalOperationWorker.isRunning());
        });
        return atomicBoolean.get();
    }

    @Override
    public void purge(final HierarchicalRoutingKey<String> routingKey) {
        publishActor(routingKey).purge();
    }

    @Override
    public boolean publish(final HierarchicalRoutingKey<String> routingKey,
                           final M message) {
        return publishActor(routingKey).publish(message);
    }

    @Override
    public long size(final HierarchicalRoutingKey<String> routingKey) {
        return publishActor(routingKey).size();
    }

    @Override
    public long inFlight(final HierarchicalRoutingKey<String> routingKey) {
        return publishActor(routingKey).inFlight();
    }

    @Override
    public boolean isEmpty(final HierarchicalRoutingKey<String> routingKey) {
        return publishActor(routingKey).isEmpty();
    }

    @Override
    public boolean isRunning(final HierarchicalRoutingKey<String> routingKey) {
        return publishActor(routingKey).isRunning();
    }

    private HierarchicalOperationWorker<MessageType, Message> publishActor(final HierarchicalRoutingKey<String> routingKey) {
        return (HierarchicalOperationWorker<MessageType, Message>) this.worker.get(messageType, routingKey);
    }

    private void initializeRouter() {
        this.worker = new HierarchicalDataStoreSupplierTree<>(
                messageType,
                hierarchicalTreeConfig,
                HierarchicalRouterUtils.actorConfigToWorkerConfigFunc,
                (routingKey, messageTypeKey, workerConfig) -> {
                    log.info("{} -> {}", routingKey.getRoutingKey(), messageTypeKey);
                    return new HierarchicalOperationWorker<>(
                            messageType,
                            workerConfig,
                            hierarchicalTreeConfig.getDefaultData(),
                            routingKey,
                            actorSystem,
                            messageHandler,
                            sidelineHandler,
                            partitioner,
                            observers);
                }
        );
    }

}
