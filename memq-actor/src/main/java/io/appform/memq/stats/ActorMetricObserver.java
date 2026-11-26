package io.appform.memq.stats;


import com.codahale.metrics.*;
import io.appform.memq.actor.Actor;
import io.appform.memq.actor.ActorOperation;
import io.appform.memq.actor.Message;
import io.appform.memq.observer.ActorObserver;
import io.appform.memq.observer.ActorObserverContext;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

@Slf4j
public class ActorMetricObserver extends ActorObserver {

    private static final String ACTOR_PREFIX = "actor";
    private static final String DELIMITER = ".";
    private static final String DELIMITER_REPLACEMENT = "_";
    private final MetricRegistry metricRegistry;
    private final String actorName;
    @Getter
    private final Map<MetricKeyData, MetricData> metricCache = new ConcurrentHashMap<>();

    public ActorMetricObserver(
            final String actorName,
            final MetricRegistry metricRegistry) {
        super(null);
        this.metricRegistry = metricRegistry;
        this.actorName = actorName;

    }

    private static String normalizeString(final String name) {
        return name.replace(DELIMITER, DELIMITER_REPLACEMENT);
    }

    @Override
    public void initialize(Actor<? extends Message> actor) {
        this.metricRegistry.gauge(MetricRegistry.name(getMetricPrefix(actorName), "size"),
                                  (MetricRegistry.MetricSupplier<Gauge<Long>>) () ->
                                          new CachedGauge<>(5, TimeUnit.SECONDS) {
                                              @Override
                                              protected Long loadValue() {
                                                  return actor.size();
                                              }
                                          });
        this.metricRegistry.gauge(MetricRegistry.name(getMetricPrefix(actorName), "in_flight"),
                (MetricRegistry.MetricSupplier<Gauge<Long>>) () ->
                        new CachedGauge<>(5, TimeUnit.SECONDS) {
                            @Override
                            protected Long loadValue() {
                                return actor.inFlight();
                            }
                        });
    }

    @Override
    public boolean execute(final ActorObserverContext<? extends Message> context, final BooleanSupplier supplier) {
        return metered(context, supplier);
    }

    private boolean metered(ActorObserverContext<? extends Message> context, BooleanSupplier supplier) {
        val metricData = getMetricData(context);
        recordQueueWait(context, metricData);
        metricData.getTotal().mark();
        val timer = metricData.getTimer().time();
        try {
            val ret = proceed(context, supplier);
            if(ret) {
                metricData.getSuccess().mark();
            }
            else {
                metricData.getFailed().mark();
            }
            return ret;
        }
        catch (Throwable t) {
            metricData.getFailed().mark();
            throw t;
        }
        finally {
            timer.stop();
        }
    }

    /**
     * Records time spent by a message between publish and start of consumption.
     */
    private void recordQueueWait(final ActorObserverContext<? extends Message> context,
                                 final MetricData metricData) {
        if (metricData.getQueueWait() == null || context.getMessageMeta() == null) {
            return;
        }
        val waitMs = Math.max(0, System.currentTimeMillis() - context.getMessageMeta().getPublishedAt());
        metricData.getQueueWait().update(waitMs, TimeUnit.MILLISECONDS);
    }

    private MetricData getMetricData(final ActorObserverContext<? extends Message> context) {
        val metricKeyData = MetricKeyData.builder()
                .actorName(actorName)
                .operation(context.getOperation().name())
                .build();
        return metricCache.computeIfAbsent(metricKeyData, key ->
                getMetricData(getMetricPrefix(metricKeyData), context.getOperation()));
    }

    private MetricData getMetricData(final String metricPrefix, final ActorOperation operation) {
        return MetricData.builder()
                .timer(metricRegistry.timer(MetricRegistry.name(metricPrefix, "latency"),
                                            () -> new Timer(new SlidingTimeWindowArrayReservoir(60, TimeUnit.SECONDS))))
                .success(metricRegistry.meter(MetricRegistry.name(metricPrefix, "success")))
                .failed(metricRegistry.meter(MetricRegistry.name(metricPrefix, "failed")))
                .total(metricRegistry.meter(MetricRegistry.name(metricPrefix, "total")))
                .queueWait(operation == ActorOperation.CONSUME
                           ? metricRegistry.timer(MetricRegistry.name(metricPrefix, "queue_wait"),
                                                  () -> new Timer(new SlidingTimeWindowArrayReservoir(60, TimeUnit.SECONDS)))
                           : null)
                .build();
    }

    private String getMetricPrefix(final MetricKeyData metricKeyData) {
        return getMetricPrefix(actorName, metricKeyData.getOperation());
    }

    private String getMetricPrefix(String... metricNames) {
        val metricPrefix = new StringBuilder(ACTOR_PREFIX);
        for (val metricName : metricNames) {
            metricPrefix.append(DELIMITER).append(normalizeString(metricName));
        }
        return metricPrefix.toString();
    }
}