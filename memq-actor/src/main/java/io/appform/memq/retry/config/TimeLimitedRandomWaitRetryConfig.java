package io.appform.memq.retry.config;

import io.appform.memq.retry.RetryType;
import jakarta.validation.constraints.Min;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

import java.util.Set;

@Value
@EqualsAndHashCode(callSuper = true)
public class TimeLimitedRandomWaitRetryConfig extends RetryConfig {

    @Min(1)
    int minWaiTimeInMillis;

    @Min(2)
    int maxWaitTimeInMillis;

    @Min(3)
    int maxTimeInMillis;

    @Builder
    @Jacksonized
    public TimeLimitedRandomWaitRetryConfig(
            int maxTimeInMillis,
            int minWaiTimeInMillis,
            int maxWaitTimeInMillis,
            Set<String> retriableExceptions) {
        super(RetryType.TIME_LIMITED_RANDOM_WAIT, retriableExceptions);
        this.maxTimeInMillis = maxTimeInMillis;
        this.minWaiTimeInMillis = minWaiTimeInMillis;
        this.maxWaitTimeInMillis = maxWaitTimeInMillis;
    }
}
