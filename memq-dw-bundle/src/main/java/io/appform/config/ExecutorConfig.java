package io.appform.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;


@Value
@Builder
@Jacksonized
public class ExecutorConfig {

    @NotNull
    @NotBlank
    String name;

    @Min(1)
    @Max(300)
    int threadPoolSize;

}
