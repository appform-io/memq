package io.appform.memq.actor;

import lombok.val;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

interface Dispatcher<M extends Message> extends AutoCloseable {

    Logger log = LoggerFactory.getLogger(Dispatcher.class);

    void register(Mailbox<M> inMailbox);   //Always executed inside mailbox lock
    void deRegister(Mailbox<M> inMailbox); //Always executed inside mailbox lock
    void triggerDispatch(Mailbox<M> inMailbox); //Always executed inside mailbox lock
    boolean isRunning();
    void close();

    //Always executed inside mailbox lock
    default void dispatch(final Mailbox<M> mailbox) {
        val inFlight = mailbox.getInFlight();
        //Find new messages, respecting insertion order and max concurrency
        int considered = 0;
        List<InternalMessage<M>> newMessages = new ArrayList<>(mailbox.getMaxConcurrency());
        for (val entry : mailbox.getMessages().entrySet()) {
            if (considered >= mailbox.getMaxConcurrency()) {
                break;
            }
            considered++;
            val id = entry.getKey();
            if (!inFlight.contains(id)) {
                inFlight.add(id);
                newMessages.add(entry.getValue());
            }
        }
        if (newMessages.isEmpty()) {
            if (inFlight.size() == mailbox.getMaxConcurrency()) {
                log.warn("Reached max concurrency:{}. Ignoring consumption till inflight messages are consumed",
                        mailbox.getMaxConcurrency());
            }
            else {
                log.debug("No new messages. Neither is actor stopped. Ignoring spurious dispatch.");
            }
            return;
        }
        dispatchNew(mailbox, newMessages);
    }

    default void dispatchNew(final Mailbox<M> mailbox, List<InternalMessage<M>> newMessages) {
        List.copyOf(newMessages).forEach(internalMessage -> mailbox.getActor().getExecutorService().submit(() -> {
            val id = internalMessage.id();
            try {
                mailbox.getActor().processWithObserver(internalMessage);
            }
            catch (Throwable throwable) {
                log.error("Error processing internalMessage", throwable);
            }
            finally {
                mailbox.releaseMessage(id);
            }
        }));
    }
}
