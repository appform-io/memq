package io.appform.memq.hierarchical;

import io.appform.memq.HighLevelActorConfig;
import io.appform.memq.hierarchical.tree.key.RoutingKey;
import lombok.val;
import org.apache.commons.lang3.StringUtils;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HierarchicalRouterHelper {

    private static final String EXECUTORS = "executors";
    private static final BiFunction<Stream<String>, String, String> beautifierFunction = (stream, delimiter) -> stream
            .filter(e -> !StringUtils.isEmpty(e))
            .collect(Collectors.joining(delimiter));


    public final Function<HierarchicalActorConfig, HierarchicalSubActorConfig> actorConfigToSubActorConfigFunc =
            actorConfig -> HierarchicalSubActorConfig.builder()
                    .partitions(actorConfig.getPartitions())
                    .maxSizePerPartition(actorConfig.getMaxSizePerPartition())
                    .maxConcurrencyPerPartition(actorConfig.getMaxConcurrencyPerPartition())
                    .retryConfig(actorConfig.getRetryConfig())
                    .exceptionHandlerConfig(actorConfig.getExceptionHandlerConfig())
                    .build();


    public <MessageType extends Enum<MessageType>> HighLevelActorConfig hierarchicalActorConfig(final MessageType messageType,
                                                                                                final RoutingKey routingKeyData,
                                                                                                final HierarchicalSubActorConfig subActorConfig,
                                                                                                final HierarchicalActorConfig mainActorConfig) {
        val useParentConfigInWorker = mainActorConfig.isUseParentConfigInWorker();
        return HighLevelActorConfig.builder()
                // Custom fields
                .executorName(executorName(mainActorConfig.getExecutorName(), messageType, routingKeyData))

                // Copy from parent if useParentConfigInWorker is set
                .partitions(useParentConfigInWorker ? mainActorConfig.getPartitions() : subActorConfig.getPartitions())
                .maxSizePerPartition(useParentConfigInWorker ? mainActorConfig.getMaxSizePerPartition() : subActorConfig.getMaxSizePerPartition())
                .maxConcurrencyPerPartition(useParentConfigInWorker ? mainActorConfig.getMaxConcurrencyPerPartition() : subActorConfig.getMaxConcurrencyPerPartition())
                .retryConfig(useParentConfigInWorker ? mainActorConfig.getRetryConfig() : subActorConfig.getRetryConfig())
                .exceptionHandlerConfig(useParentConfigInWorker ? mainActorConfig.getExceptionHandlerConfig() : subActorConfig.getExceptionHandlerConfig())

                // Direct from Parent
                .metricDisabled(mainActorConfig.isMetricDisabled())
                .build();
    }

    private <MessageType extends Enum<MessageType>> String executorName(final String parentExecutorName,
                                                                        final MessageType messageType,
                                                                        final RoutingKey routingKeyData) {
        val routingKey = routingKeyData.getRoutingKey();

        if (!StringUtils.isEmpty(parentExecutorName)) {
            // For backward compatibility
            if (routingKey.isEmpty()) {
                return parentExecutorName;
            }

            return beautifierFunction.apply(Stream.of(parentExecutorName, String.join(".", routingKey)), ".");
        }

        return beautifierFunction.apply(Stream.of(EXECUTORS, String.join(".", routingKey), messageType.name()), ".");
    }
}