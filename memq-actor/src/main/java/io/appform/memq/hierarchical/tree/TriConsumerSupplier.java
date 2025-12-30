package io.appform.memq.hierarchical.tree;

@FunctionalInterface
public interface TriConsumerSupplier<S, R, K, V> {
    S get(R routingKey, K key, V value);
}