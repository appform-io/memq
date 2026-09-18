package io.appform.memq.retry.config;

import io.appform.memq.retry.RetryType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

import java.util.Set;

@Value
@EqualsAndHashCode(callSuper = true)
public class CountLimitedExponentialWaitRetryConfig extends RetryConfig {

    @Min(1)
    int maxAttempts;

    @Min(1)
    int waitTimeInMillis;

    @Min(2)
    int maxWaitTimeInMillis;

    @DecimalMin("1.1")
    double multipier;

    @Builder
    @Jacksonized
    public CountLimitedExponentialWaitRetryConfig(
            int maxAttempts,
            int waitTimeInMillis,
            int maxWaitTimeInMillis,
            double multipier,
            Set<String> retriableExceptions) {
        super(RetryType.COUNT_LIMITED_EXPONENTIAL_BACKOFF, retriableExceptions);
        this.maxAttempts = maxAttempts;
        this.waitTimeInMillis = waitTimeInMillis;
        this.maxWaitTimeInMillis = maxWaitTimeInMillis;
        this.multipier = multipier;
    }
}
