package io.appform.memq.stats;

import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;

public record MetricData(Meter total, Meter success, Meter failed, Timer timer) {
}
