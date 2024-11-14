package io.appform.memq.hierarchical;

import io.appform.memq.actor.IActor;
import io.appform.memq.actor.Message;
import io.appform.memq.hierarchical.tree.key.HierarchicalRoutingKey;

public interface IHierarchicalActor<M extends Message> extends IActor<M> {

    boolean isEmpty(final HierarchicalRoutingKey<String> routingKey);
    long size(final HierarchicalRoutingKey<String> routingKey);
    long inFlight(final HierarchicalRoutingKey<String> routingKey);
    boolean isRunning(final HierarchicalRoutingKey<String> routingKey);

    void purge(final HierarchicalRoutingKey<String> routingKey);

    boolean publish(final HierarchicalRoutingKey<String> routingKey, final M message);
}
