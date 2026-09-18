package io.appform.memq;

import io.appform.memq.exceptionhandler.config.DropConfig;
import io.appform.memq.exceptionhandler.config.ExceptionHandlerConfig;
import io.appform.memq.retry.config.NoRetryConfig;
import io.appform.memq.retry.config.RetryConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.extern.jackson.Jacksonized;


@Value
@Builder
@Jacksonized
@AllArgsConstructor
@NoArgsConstructor
public class HighLevelActorConfig {

    @Min(1)
    @Max(100)
    @Builder.Default
    int partitions = 1;

    @Min(1)
    @Builder.Default
    long maxSizePerPartition = Long.MAX_VALUE;

    @Min(1)
    @Builder.Default
    int maxConcurrencyPerPartition = Integer.MAX_VALUE;

    @Valid
    @NotNull
    @Builder.Default
    RetryConfig retryConfig = new NoRetryConfig();

    @Valid
    @NotNull
    @Builder.Default
    ExceptionHandlerConfig exceptionHandlerConfig = new DropConfig();

    @NotNull
    @Builder.Default
    String executorName = "default";

    @Builder.Default
    boolean metricDisabled = false;

}
