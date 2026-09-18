package io.appform.memq.retry;


import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

import java.util.concurrent.Callable;

public abstract class RetryStrategy {

    private final RetryPolicy<Boolean> policy;

    protected RetryStrategy(RetryPolicy<Boolean> policy) {
        this.policy = policy;
    }

    public boolean execute(Callable<Boolean> callable) {
        return Failsafe.with(policy)
                .get(callable::call);
    }

}
