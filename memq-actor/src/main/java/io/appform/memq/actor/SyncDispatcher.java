package io.appform.memq.actor;

import java.util.HashMap;
import java.util.Map;

class SyncDispatcher<M extends Message> implements Dispatcher<M> {

    private final Map<Integer, Mailbox<M>> registeredMailbox;

    public SyncDispatcher(int partition){
        registeredMailbox = new HashMap<>(partition);
    }

    //Always executed inside mailbox lock
    @Override
    public void register(final Mailbox<M> inMailbox) {
        registeredMailbox.putIfAbsent(inMailbox.getPartition(), inMailbox);
    }

    //Always executed inside mailbox lock
    @Override
    public void deRegister(final Mailbox<M> inMailbox) {
        registeredMailbox.remove(inMailbox.getPartition());
    }

    //Always executed inside mailbox lock
    @Override
    public void triggerDispatch(final Mailbox<M> inMailbox) {
        dispatch(inMailbox);
    }

    //Always executed inside mailbox lock
    @Override
    public boolean isRunning() {
        return !registeredMailbox.isEmpty();
    }

    @Override
    public void close() {
        // No-op: SyncDispatcher has no resources to clean up
    }
}


