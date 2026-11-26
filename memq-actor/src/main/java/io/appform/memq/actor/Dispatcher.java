package io.appform.memq.actor;

import com.google.common.collect.Sets;
import lombok.val;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.stream.Collectors;

interface Dispatcher<M extends Message> extends AutoCloseable {

    Logger log = LoggerFactory.getLogger(Dispatcher.class);

    void register(Mailbox<M> inMailbox);   //Always executed inside mailbox lock
    void deRegister(Mailbox<M> inMailbox); //Always executed inside mailbox lock
    void triggerDispatch(Mailbox<M> inMailbox); //Always executed inside mailbox lock
    boolean isRunning();
    void close();

    //Always executed inside mailbox lock
    default void dispatch(final Mailbox<M> mailbox) {
        //Check if we have reached max concurrency. If yes, ignore dispatching new messages
        if (mailbox.getInFlight().size() >= mailbox.getMaxConcurrency()) {
            log.warn("Mailbox:{} reached max concurrency:{}. Ignoring consumption till inflight messages are consumed",
                    mailbox.getName(), mailbox.getMaxConcurrency());
            return;
        }
        //Find new messages
        val newInOrderedMessages = mailbox.getMessages().keySet()
                .stream()
                .limit(mailbox.getMaxConcurrency())
                .collect(Collectors.toSet());
        val newMessageIds = Set.copyOf(Sets.difference(newInOrderedMessages, mailbox.getInFlight()));
        if (newMessageIds.isEmpty()) {
            log.debug("No new messages. Neither is actor stopped. Ignoring spurious dispatch.");
            return;
        }
        mailbox.getInFlight().addAll(newMessageIds);
        val messagesToBeDelivered = newMessageIds.stream()
                .map(mailbox.getMessages()::get)
                .toList();
        messagesToBeDelivered.forEach(internalMessage -> mailbox.getActor().getExecutorService().submit(() -> {
            val id = internalMessage.getId();
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
