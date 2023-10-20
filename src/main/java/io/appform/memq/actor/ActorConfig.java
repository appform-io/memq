package io.appform.memq.actor;

import io.appform.memq.exceptionhandler.config.ExceptionHandlerConfig;
import io.appform.memq.ExecutorConfig;
import io.appform.memq.exceptionhandler.config.SidelineConfig;
import io.appform.memq.retry.config.NoRetryConfig;
import io.appform.memq.retry.config.RetryConfig;
import lombok.*;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
@EqualsAndHashCode
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ActorConfig {

    @Min(1)
    @Max(100)
    @Builder.Default
    private int partitions = 1;

    @NotNull
    @Valid
    @Builder.Default
    private RetryConfig retryConfig = new NoRetryConfig();

    @NotNull
    @Valid
    private ExceptionHandlerConfig exceptionHandlerConfig = new SidelineConfig();

    @NotNull
    @Valid
    private ExecutorConfig executorConfig;

}