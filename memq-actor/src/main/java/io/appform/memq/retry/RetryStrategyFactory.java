package io.appform.memq.retry;

import io.appform.memq.retry.config.*;
import io.appform.memq.retry.impl.*;

/**
 * Creates Policy based on config
 */
public class RetryStrategyFactory {
    public RetryStrategy create(RetryConfig config) {
        return switch (config) {
            case NoRetryConfig c -> new NoRetryStrategy(c);
            case TimeLimitedExponentialWaitRetryConfig c -> new TimeLimitedExponentialWaitRetryStrategy(c);
            case TimeLimitedRandomWaitRetryConfig c -> new TimeLimitedRandomWaitRetryStrategy(c);
            case TimeLimitedFixedWaitRetryConfig c -> new TimeLimitedFixedWaitRetryStrategy(c);
            case CountLimitedExponentialWaitRetryConfig c -> new CountLimitedExponentialWaitRetryStrategy(c);
            case CountLimitedRandomWaitRetryConfig c -> new CountLimitedRandomWaitRetryStrategy(c);
            case CountLimitedFixedWaitRetryConfig c -> new CountLimitedFixedWaitRetryStrategy(c);
        };
    }
}
