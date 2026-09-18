package io.appform.memq.retry.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.appform.memq.retry.RetryType;
import lombok.Data;

import java.util.Set;

/**
 * Configure a retry strategy
 */
@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "COUNT_LIMITED_EXPONENTIAL_BACKOFF", value =
                CountLimitedExponentialWaitRetryConfig.class),
        @JsonSubTypes.Type(name = "COUNT_LIMITED_FIXED_WAIT", value = CountLimitedFixedWaitRetryConfig.class),
        @JsonSubTypes.Type(name = "COUNT_LIMITED_INCREMENTAL_WAIT", value = CountLimitedRandomWaitRetryConfig.class),
        @JsonSubTypes.Type(name = "NO_RETRY", value = NoRetryConfig.class),
        @JsonSubTypes.Type(name = "TIME_LIMITED_EXPONENTIAL_BACKOFF", value =
                TimeLimitedExponentialWaitRetryConfig.class),
        @JsonSubTypes.Type(name = "TIME_LIMITED_FIXED_WAIT", value = TimeLimitedFixedWaitRetryConfig.class),
        @JsonSubTypes.Type(name = "TIME_LIMITED_INCREMENTAL_WAIT", value = TimeLimitedRandomWaitRetryConfig.class)
})
public sealed abstract class RetryConfig
        permits CountLimitedExponentialWaitRetryConfig, CountLimitedFixedWaitRetryConfig,
                CountLimitedRandomWaitRetryConfig, NoRetryConfig,
                TimeLimitedExponentialWaitRetryConfig, TimeLimitedFixedWaitRetryConfig,
                TimeLimitedRandomWaitRetryConfig {
    private final RetryType type;

    private Set<String> retriableExceptions;

    protected RetryConfig(RetryType type) {
        this.type = type;
    }

    protected RetryConfig(
            RetryType type,
            Set<String> retriableExceptions) {
        this.type = type;
        this.retriableExceptions = retriableExceptions;
    }
}
