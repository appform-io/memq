package io.appform.memq.retry.config;

import io.appform.memq.retry.RetryType;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.jackson.Jacksonized;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TimeLimitedFixedWaitRetryConfig extends RetryConfig {

    @Valid
    @NotNull
    @Builder.Default
    private Duration waitTime = Duration.ofMillis(500);

    @Valid
    @NotNull
    @Builder.Default
    private Duration maxTime = Duration.ofMillis(30_000);

    @Builder
    @Jacksonized
    public TimeLimitedFixedWaitRetryConfig(
            Duration maxTime,
            Duration waitTime,
            Set<String> retriableExceptions) {
        super(RetryType.TIME_LIMITED_FIXED_WAIT, retriableExceptions);
        this.maxTime = maxTime;
        this.waitTime = waitTime;
    }
}
