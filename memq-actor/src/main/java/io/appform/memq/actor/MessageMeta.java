package io.appform.memq.actor;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;


@Getter
public class MessageMeta {

    private AtomicInteger deliveryAttempt;
    private final long publishedAt;
    private final long validTill;
    private final Map<String, Object> headers;

    public MessageMeta(long publishedAt, long validTill, Map<String, Object> headers) {
        this.deliveryAttempt = new AtomicInteger(0);
        this.publishedAt = publishedAt;
        this.validTill = validTill;
        this.headers = headers;
    }

    public boolean isRedelivered() {
        return deliveryAttempt.get() > 1;
    }

    //Private package function
    void incrementAttempt() {
        this.deliveryAttempt.incrementAndGet();
    }


}