package io.appform.memq.retry.config;

import io.appform.memq.retry.RetryType;
import jakarta.validation.constraints.Min;
import lombok.*;
import lombok.extern.jackson.Jacksonized;


import java.util.Set;

@Value
@EqualsAndHashCode(callSuper = true)
public class CountLimitedRandomWaitRetryConfig extends RetryConfig {

    @Min(1)
    int maxAttempts;

    @Min(1)
    int minWaitTimeInMillis;

    @Min(2)
    int maxWaitTimeInMillis;

    @Builder
    @Jacksonized
    CountLimitedRandomWaitRetryConfig(
            int maxAttempts,
            int minWaitTimeInMillis,
            int maxWaitTimeInMillis,
            Set<String> retriableExceptions) {
        super(RetryType.COUNT_LIMITED_RANDOM_WAIT, retriableExceptions);
        this.maxAttempts = maxAttempts;
        this.minWaitTimeInMillis = minWaitTimeInMillis;
        this.maxWaitTimeInMillis = maxWaitTimeInMillis;
    }
}
