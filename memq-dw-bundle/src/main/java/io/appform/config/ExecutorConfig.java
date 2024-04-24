package io.appform.config;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@Value
@Builder
@Jacksonized
public class ExecutorConfig {

    @NotNull
    @NotEmpty
    String name;

    @Min(1)
    @Max(300)
    int threadPoolSize;

    int queueSize;
}
