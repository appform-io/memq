package io.appform.memq.observer;

public record ObserverMessageMeta(String id, long validTill, long publishedAt) {
}
