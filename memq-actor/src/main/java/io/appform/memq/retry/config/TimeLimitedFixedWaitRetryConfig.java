package io.appform.memq.retry.config;

import io.appform.memq.retry.RetryType;
import jakarta.validation.constraints.Min;
import lombok.*;
import lombok.extern.jackson.Jacksonized;


import java.util.Set;

@Value
@EqualsAndHashCode(callSuper = true)
public class TimeLimitedFixedWaitRetryConfig extends RetryConfig {

    @Min(1)
    int waitTimeInMillis;

    @Min(2)
    int maxTimeInMillis;

    @Builder
    @Jacksonized
    public TimeLimitedFixedWaitRetryConfig(
            int maxTimeInMillis,
            int waitTimeInMillis,
            Set<String> retriableExceptions) {
        super(RetryType.TIME_LIMITED_FIXED_WAIT, retriableExceptions);
        this.maxTimeInMillis = maxTimeInMillis;
        this.waitTimeInMillis = waitTimeInMillis;
    }
}
