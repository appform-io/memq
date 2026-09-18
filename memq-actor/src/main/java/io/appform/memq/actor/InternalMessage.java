package io.appform.memq.actor;

import java.util.Map;

record InternalMessage<M extends Message>(
        String id,
        long validTill,
        long publishedAt,
        Map<String, Object> headers,
        M message) {
}
