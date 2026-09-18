package io.appform.config;

import jakarta.validation.Valid;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import java.util.List;

@Value
@Builder
@Jacksonized
public class MemqConfig {

    @Valid
    @Builder.Default
    List<ExecutorConfig> executors = List.of();

}
