package io.appform.memq.actor;

import lombok.val;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class AsyncIsolatedThreadpoolDispatcher<M extends Message> implements Dispatcher<M> {
    private final ExecutorService executorService;
    private final Map<Integer, AsyncDispatcherWorker<M>> registeredMailboxWorker;

    public AsyncIsolatedThreadpoolDispatcher(int inPartitions) {
        this.executorService = Executors.newFixedThreadPool(inPartitions);
        this.registeredMailboxWorker = new HashMap<>(inPartitions);
    }

    //Always executed inside mailbox lock
    @Override
    public final void register(final Mailbox<M> inMailbox) {
        val mailBoxAsyncDispatcherWorker = new AsyncDispatcherWorker<>(inMailbox, this);
        registeredMailboxWorker.putIfAbsent(inMailbox.getPartition(), mailBoxAsyncDispatcherWorker);
        mailBoxAsyncDispatcherWorker.start(executorService::submit);
    }

    //Always executed inside mailbox lock
    @Override
    public final void deRegister(final Mailbox<M> inMailbox) {
        val worker = registeredMailboxWorker.remove(inMailbox.getPartition());
        if (worker != null) {
            worker.close();
        }
    }

    //Always executed inside mailbox lock
    @Override
    public final void triggerDispatch(final Mailbox<M> inMailbox) {
        registeredMailboxWorker.get(inMailbox.getPartition()).trigger();
    }

    @Override
    public final boolean isRunning() {
        return !registeredMailboxWorker.isEmpty()
                && registeredMailboxWorker.values()
                .stream()
                .allMatch(AsyncDispatcherWorker::isRunning);
    }

    @Override
    public final void close() {
        executorService.shutdown();
    }
}


