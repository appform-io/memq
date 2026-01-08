package io.appform.memq.actor;

import com.google.common.base.Preconditions;
import io.appform.memq.observer.ActorObserver;
import io.appform.memq.observer.ActorObserverContext;
import io.appform.memq.observer.ObserverMessageMeta;
import io.appform.memq.observer.TerminalActorObserver;
import io.appform.memq.retry.RetryStrategy;
import io.appform.memq.utils.TriConsumer;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public class Actor<M extends Message> implements AutoCloseable {

    private final String name;
    private final ExecutorService executorService;
    private final Dispatcher<M> messageDispatcher;
    private final ToIntFunction<M> partitioner;
    private final Map<Integer, Mailbox<M>> mailboxes;
    private final BiPredicate<M, MessageMeta> validationHandler;
    private final BiPredicate<M, MessageMeta> consumerHandler;
    private final BiConsumer<M, MessageMeta> sidelineHandler;
    private final TriConsumer<M, MessageMeta, Throwable> exceptionHandler;
    private final RetryStrategy retryer;
    private final ActorObserver rootObserver;


    @SneakyThrows
    public Actor(
            String name,
            ExecutorService executorService,
            BiPredicate<M, MessageMeta> validationHandler,
            BiPredicate<M, MessageMeta> consumerHandler,
            BiConsumer<M, MessageMeta> sidelineHandler,
            TriConsumer<M, MessageMeta, Throwable> exceptionHandler,
            RetryStrategy retryer,
            DispatcherType dispatcherType,
            int partitions,
            long maxSizePerPartition,
            int maxConcurrencyPerPartition,
            ToIntFunction<M> partitioner,
            List<ActorObserver> observers) {
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(executorService, "Executor service cannot be null");
        Objects.requireNonNull(partitioner, "Partitioner cannot be null");
        Objects.requireNonNull(validationHandler, "ValidationHandler cannot be null");
        Objects.requireNonNull(consumerHandler, "ConsumerHandler cannot be null");
        Objects.requireNonNull(sidelineHandler, "SidelineHandler cannot be null");
        Objects.requireNonNull(exceptionHandler, "ExceptionHandler cannot be null");
        Objects.requireNonNull(dispatcherType,"Dispatcher cannot be null");
        // Switch ensures validation is performed for all dispatcher types.
        // Adding a new DispatcherType will cause a compile error here if not handled.
        switch (dispatcherType){
            case SYNC ->
                    Preconditions.checkArgument( maxConcurrencyPerPartition == maxSizePerPartition,
                    "Max Queue size and max concurrency has to be same for sync dispatcher");
            case ASYNC_ISOLATED -> {
                // No-op: ASYNC_ISOLATED dispatcher has no specific validation requirements
            }
        }
        
        this.name = name;
        this.executorService = executorService;
        this.validationHandler = validationHandler;
        this.consumerHandler = consumerHandler;
        this.sidelineHandler = sidelineHandler;
        this.exceptionHandler = exceptionHandler;
        this.retryer = retryer;
        this.partitioner = partitioner;
        this.messageDispatcher = dispatcher(dispatcherType, partitions);
        this.mailboxes = IntStream.range(0, partitions)
                .boxed()
                .collect(Collectors.toMap(Function.identity(), i -> new Mailbox<M>(this, i, maxSizePerPartition, maxConcurrencyPerPartition)));
        this.rootObserver = setupObserver(observers);
    }

    public final boolean isEmpty() {
        return mailboxes.values()
                .stream()
                .allMatch(Mailbox::isEmpty);
    }

    public final long size() {
        return mailboxes.values()
                .stream()
                .mapToLong(Mailbox::size)
                .sum();
    }

    public final long inFlight() {
        return mailboxes.values()
                .stream()
                .mapToLong(Mailbox::inFlight)
                .sum();
    }

    public final boolean isRunning() {
        return messageDispatcher.isRunning();
    }

    public final void purge() {
        mailboxes.values().forEach(Mailbox::purge);
    }

    public final boolean publish(final M message) {
        return rootObserver.execute(ActorObserverContext.builder()
                                     .operation(ActorOperation.PUBLISH)
                                     .message(message)
                                     .actorName(name)
                                     .build(),
                             () -> mailboxes.get(partitioner.applyAsInt(message))
                                     .publish(message));
    }

    public final void start() {
        mailboxes.values().forEach(Mailbox::start);
    }

    @Override
    public final void close() {
        mailboxes.values().forEach(Mailbox::close);
        messageDispatcher.close();
    }

   void processWithObserver(final InternalMessage<M> internalMessage) {
        val observerMessageMeta = new ObserverMessageMeta(internalMessage.getId(), internalMessage.getPublishedAt(),
                internalMessage.getValidTill());
        this.rootObserver.execute(ActorObserverContext.builder()
                        .messageMeta(observerMessageMeta)
                        .message(internalMessage.getMessage())
                        .operation(ActorOperation.CONSUME)
                        .actorName(this.name)
                        .build(),
                () -> process(internalMessage));
    }

    String getName() {
        return name;
    }

    ExecutorService getExecutorService() {
        return executorService;
    }

    Dispatcher<M> getMessageDispatcher() {
        return messageDispatcher;
    }

    private boolean process(final InternalMessage<M> internalMessage) {
        val id = internalMessage.getId();
        val message = internalMessage.getMessage();
        var status = false;
        var messageMeta = new MessageMeta(internalMessage.getPublishedAt(),
                internalMessage.getValidTill(),
                internalMessage.getHeaders());
        try {
            val valid = this.rootObserver.execute(ActorObserverContext.builder()
                            .message(message)
                            .operation(ActorOperation.VALIDATE)
                            .actorName(this.name)
                            .build(),
                    () -> this.validationHandler.test(message, messageMeta));
            if (!valid) {
                log.debug("Message validation failed for message: {}", message);
                return false;
            }
            else {
                status = this.retryer.execute(() -> {
                    messageMeta.incrementAttempt();
                    return this.consumerHandler.test(message, messageMeta);
                });
                if (!status) {
                    log.debug("Consumer failed for message: {}", message);
                    this.rootObserver.execute(ActorObserverContext.builder()
                                    .message(message)
                                    .operation(ActorOperation.SIDELINE)
                                    .actorName(this.name)
                                    .build(),
                            () -> {
                                this.sidelineHandler.accept(message, messageMeta);
                                return true;
                            });
                }
            }
        } catch (Exception e) {
            log.error("Error processing message : " + id, e);
            this.rootObserver.execute(ActorObserverContext.builder()
                            .message(message)
                            .operation(ActorOperation.HANDLE_EXCEPTION)
                            .actorName(this.name)
                            .build(),
                    () -> {
                        this.exceptionHandler.accept(message, messageMeta, e);
                        return true;
                    });
        }
        return status;
    }

    private ActorObserver setupObserver(List<ActorObserver> observers) {
        //Terminal observer calls the actual method
        ActorObserver startObserver = new TerminalActorObserver();
        startObserver.initialize(this); //initializing terminal observer
        if (observers != null) {
            for (var observer : observers) {
                if (null == observer) {
                    continue;
                }
                startObserver = observer.setNext(startObserver);
                startObserver.initialize(this); //initializing new observer
            }
        }
        return startObserver;
    }

    private Dispatcher<M> dispatcher(final DispatcherType dispatcherType, final int partitions) {
        return switch (dispatcherType) {
            case SYNC -> new SyncDispatcher<>(partitions);
            case ASYNC_ISOLATED -> new AsyncIsolatedThreadpoolDispatcher<>(partitions);
        };
    }
}
