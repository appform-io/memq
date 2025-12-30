package io.appform.memq.hierarchical;

import io.appform.memq.actor.IActor;
import io.appform.memq.actor.Message;
import io.appform.memq.hierarchical.tree.key.HierarchicalRoutingKey;

public interface IHierarchicalActor<M extends Message> extends IActor<M> {

    boolean publish(final HierarchicalRoutingKey<String> routingKey, final M message);
}
