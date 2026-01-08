package io.appform.memq.observer;


import io.appform.memq.actor.Actor;
import io.appform.memq.actor.Message;

import java.util.function.BooleanSupplier;


public final class TerminalActorObserver extends ActorObserver {
    public TerminalActorObserver() {
        super(null);
    }

    @Override
    public void initialize(Actor<? extends Message> actor) {
        // No-op: TerminalActorObserver has no initialization required
    }

    @Override
    public boolean execute(final ActorObserverContext<? extends Message> context, final BooleanSupplier supplier) {
        return proceed(context, supplier);
    }
}