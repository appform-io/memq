package io.appform.memq.actor;

public interface IActor<M extends Message> extends AutoCloseable {

    void start();
    void close();

    boolean isEmpty();
    long size();
    long inFlight();
    boolean isRunning();
    void purge();

    boolean publish(final M message);
}