package io.appform.memq.retry.config;

import io.appform.memq.retry.RetryType;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

/**
 * No retry will be done
 */
@Builder
@Jacksonized
public final class NoRetryConfig extends RetryConfig {
    public NoRetryConfig() {
        super(RetryType.NO_RETRY);
    }
}
