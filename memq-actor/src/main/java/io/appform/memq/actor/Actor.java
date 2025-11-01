package io.appform.memq.actor;

import io.appform.memq.observer.ActorObserver;
import io.appform.memq.observer.ActorObserverContext;
import io.appform.memq.observer.ObserverMessageMeta;
import io.appform.memq.observer.TerminalActorObserver;
import io.appform.memq.retry.RetryStrategy;
import io.appform.memq.utils.TriConsumer;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public class Actor<M extends Message> implements AutoCloseable {

    private final String name;
    private final ExecutorService executorService;
    private final ExecutorService messageDispatcher; //TODO::Separate dispatch and add NoDispatch flow
    private final ToIntFunction<M> partitioner;
    private final Map<Integer, Mailbox<M>> mailboxes;
    private final BiFunction<M, MessageMeta, Boolean> validationHandler;
    private final BiFunction<M, MessageMeta, Boolean> consumerHandler;
    private final BiConsumer<M, MessageMeta> sidelineHandler;
    private final TriConsumer<M, MessageMeta, Throwable> exceptionHandler;
    private final RetryStrategy retryer;
    private final ActorObserver rootObserver;


    @SneakyThrows
    public Actor(
            String name,
            ExecutorService executorService,
            BiFunction<M, MessageMeta, Boolean> validationHandler,
            BiFunction<M, MessageMeta, Boolean> consumerHandler,
            BiConsumer<M, MessageMeta> sidelineHandler,
            TriConsumer<M, MessageMeta, Throwable> exceptionHandler,
            RetryStrategy retryer,
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
        this.name = name;
        this.executorService = executorService;
        this.validationHandler = validationHandler;
        this.consumerHandler = consumerHandler;
        this.sidelineHandler = sidelineHandler;
        this.exceptionHandler = exceptionHandler;
        this.messageDispatcher = Executors.newFixedThreadPool(partitions);
        this.retryer = retryer;
        this.partitioner = partitioner;
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
        return mailboxes.values()
                .stream()
                .allMatch(Mailbox::isRunning);
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

    private static class Mailbox<M extends Message> implements AutoCloseable {

        private final Actor<M> actor;
        private final String name;
        private final long maxSize;
        private final int maxConcurrency;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition checkCondition = lock.newCondition();
        private final Map<String, InternalMessage<M>> messages = new LinkedHashMap<>();
        private final Set<String> inFlight = new HashSet<>();
        private final AtomicBoolean stopped = new AtomicBoolean();
        private Future<?> monitorFuture;

        public Mailbox(Actor<M> actor, int partition, long maxSize, int maxConcurrency) {
            this.actor = actor;
            this.maxSize = maxSize;
            this.maxConcurrency = maxConcurrency;
            this.name = actor.name + "-" + partition;
        }

        public final boolean isEmpty() {
            lock.lock();
            try {
                return messages.isEmpty();
            }
            finally {
                lock.unlock();
            }
        }

        public final long size() {
            lock.lock();
            try {
                return messages.size();
            } finally {
                lock.unlock();
            }
        }


        public final int inFlight() {
            lock.lock();
            try {
                return inFlight.size();
            } finally {
                lock.unlock();
            }
        }

        public final void purge() {
            lock.lock();
            try {
                messages.clear();
            } finally {
                lock.unlock();
            }
        }

        public final boolean isRunning() {
            return !stopped.get();
        }

        public final void start() {
            monitorFuture = actor.messageDispatcher.submit(this::monitor);
        }

        public final boolean publish(final M message) {
            lock.lock();
            try {
                val currSize = messages.size();
                if (currSize >= this.maxSize) {
                    log.warn("Blocking publish for as curr size:{} is more than specified threshold:{}",
                            currSize, this.maxSize);
                    return false;
                }
                val internalMessage = new InternalMessage<>(message.id(), message.validTill(),
                        System.currentTimeMillis(), message.headers(), message);
                messages.putIfAbsent(internalMessage.getId(), internalMessage);
                checkCondition.signalAll();
            } finally {
                lock.unlock();
            }
            return true;
        }


        @Override
        public final void close() {
            lock.lock();
            try {
                stopped.set(true);
                checkCondition.signalAll();
            }
            finally {
                lock.unlock();
            }
            if (null != monitorFuture) {
                monitorFuture.cancel(true);
            }
            actor.messageDispatcher.shutdown();
        }

        private void monitor() {
            lock.lock();
            try {
                while (true) {
                    //We can do the tests twice or just stop
                    //waiting after sometime and check the conditions anyway
                    //The set difference operation _might_ be expensive, hence going for the latter approach for now
                    //Can be changed in the future if needed
                    checkCondition.await(100, TimeUnit.MILLISECONDS);
                    if (stopped.get()) {
                        log.info("Actor {} monitor thread exiting", name);
                        return;
                    }
                    var messagesToBeDelivered = new ArrayList<InternalMessage<M>>();
                    //Find new messages
                    messages.keySet()
                            .stream()
                            .limit(this.maxConcurrency)
                            .filter(id -> !inFlight.contains(id))
                            .forEach(messageId -> {
                                inFlight.add(messageId);
                                messagesToBeDelivered.add(messages.get(messageId));
                            });
                    messagesToBeDelivered.forEach(internalMessage -> actor.executorService.submit(() -> {
                        val id = internalMessage.getId();
                        try {
                            val observerMessageMeta = new ObserverMessageMeta(id, internalMessage.getPublishedAt(),
                                    internalMessage.getValidTill());
                            actor.rootObserver.execute(ActorObserverContext.builder()
                                            .messageMeta(observerMessageMeta)
                                            .message(internalMessage.getMessage())
                                            .operation(ActorOperation.CONSUME)
                                            .actorName(actor.name)
                                            .build(),
                                    () -> process(internalMessage));
                        }
                        catch (Throwable throwable) {
                            log.error("Error processing internalMessage", throwable);
                        }
                        finally {
                            releaseMessage(id);
                        }
                    }));
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Monitor thread stopped for {}", name);
            }
            finally {
                lock.unlock();
            }
        }

        private boolean process(final InternalMessage<M> internalMessage) {
            val id = internalMessage.getId();
            val message = internalMessage.getMessage();
            var status = false;
            var messageMeta = new MessageMeta(internalMessage.getPublishedAt(),
                    internalMessage.getValidTill(),
                    internalMessage.getHeaders());
            try {
                val valid = actor.rootObserver.execute(ActorObserverContext.builder()
                                .message(message)
                                .operation(ActorOperation.VALIDATE)
                                .actorName(actor.name)
                                .build(),
                        () -> actor.validationHandler.apply(message, messageMeta));
                if (!valid) {
                    log.debug("Message validation failed for message: {}", message);
                    return false;
                }
                else {
                    status = actor.retryer.execute(() -> {
                        messageMeta.incrementAttempt();
                        return actor.consumerHandler.apply(message, messageMeta);
                    });
                    if (!status) {
                        log.debug("Consumer failed for message: {}", message);
                        actor.rootObserver.execute(ActorObserverContext.builder()
                                        .message(message)
                                        .operation(ActorOperation.SIDELINE)
                                        .actorName(actor.name)
                                        .build(),
                                () -> {
                                    actor.sidelineHandler.accept(message, messageMeta);
                                    return true;
                                });
                    }
                }
            } catch (Exception e) {
                log.error("Error processing message : " + id, e);
                actor.rootObserver.execute(ActorObserverContext.builder()
                                .message(message)
                                .operation(ActorOperation.HANDLE_EXCEPTION)
                                .actorName(actor.name)
                                .build(),
                        () -> {
                            actor.exceptionHandler.accept(message, messageMeta, e);
                            return true;
                        });
            }
            return status;
        }

        private void releaseMessage(String id) {
            lock.lock();
            try {
                inFlight.remove(id);
                messages.remove(id);
            }
            finally {
                lock.unlock();
            }
        }
    }
}
