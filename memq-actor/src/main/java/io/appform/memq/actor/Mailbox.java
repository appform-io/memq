package io.appform.memq.actor;

import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
class Mailbox<M extends Message> implements AutoCloseable {

    private final Actor<M> actor;
    private final int partition;
    private final String name;
    private final long maxSize;
    private final int maxConcurrency;
    private final Map<String, InternalMessage<M>> messages = new LinkedHashMap<>();
    private final Set<String> inFlight = new HashSet<>();
    private final ReentrantLock lock = new ReentrantLock();

    public Mailbox(Actor<M> actor, int partition, long maxSize, int maxConcurrency) {
        this.actor = actor;
        this.maxSize = maxSize;
        this.maxConcurrency = maxConcurrency;
        this.partition = partition;
        this.name = actor.getName() + "-" + partition;
    }

    public final boolean isEmpty() {
        acquireLock();
        try {
            return messages.isEmpty();
        }
        finally {
            releaseLock();
        }
    }

    public final long size() {
        acquireLock();
        try {
            return messages.size();
        } finally {
            releaseLock();
        }
    }

    public final int inFlight() {
        acquireLock();
        try {
            return inFlight.size();
        } finally {
            releaseLock();
        }
    }

    public final void purge() {
        acquireLock();
        try {
            messages.clear();
        } finally {
            releaseLock();
        }
    }

    public final void start() {
        acquireLock();
        try {
            actor.getMessageDispatcher().register(this);
        }
        finally {
            releaseLock();
        }
    }

    public final boolean publish(final M message) {
        acquireLock();
        try {
            val currSize = messages.size();
            if (currSize >= maxSize) {
                log.warn("Blocking publish for as curr size:{} is more than specified threshold:{}",
                        currSize, maxSize);
                return false;
            }
            val internalMessage = new InternalMessage<>(message.id(), message.validTill(),
                    System.currentTimeMillis(), message.headers(), message);
            messages.putIfAbsent(internalMessage.id(), internalMessage);
            actor.getMessageDispatcher().triggerDispatch(this);
            return true;
        } finally {
            releaseLock();
        }
    }

    @Override
    public final void close() {
        acquireLock();
        try {
            actor.getMessageDispatcher().deRegister(this);
        }
        finally {
            releaseLock();
        }
    }

    void releaseLock() {
        lock.unlock();
    }

    void acquireLock() {
        lock.lock();
    }

    Condition lockNewCondition() {
        return lock.newCondition();
    }


    void releaseMessage(String id) {
        acquireLock();
        try {
            inFlight.remove(id);
            messages.remove(id);
        }
        finally {
            releaseLock();
        }
    }

    Actor<M> getActor() {
        return actor;
    }

    int getPartition() {
        return partition;
    }

    int getMaxConcurrency() {
        return maxConcurrency;
    }

    String getName() {
        return name;
    }

    Set<String> getInFlight() {
        return inFlight;
    }

    Map<String, InternalMessage<M>> getMessages() {
        return messages;
    }

}

