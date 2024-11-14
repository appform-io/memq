package io.appform.memq.hierarchical.tree.key;

import java.util.List;

public interface HierarchicalRoutingKey<R> {
    List<R> getRoutingKey();
}
