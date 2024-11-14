package io.appform.memq.hierarchical;

import io.appform.memq.HighLevelActorConfig;
import io.appform.memq.hierarchical.tree.key.RoutingKey;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.apache.commons.lang3.StringUtils;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@UtilityClass
public class HierarchicalRouterUtils {

    private static final String EXECUTORS = "executors";
    private static final BiFunction<Stream<String>, String, String> beautifierFunction = (stream, delimiter) -> stream
            .filter(e -> !StringUtils.isEmpty(e))
            .collect(Collectors.joining(delimiter));


    static final Function<HierarchialHighLevelActorConfig, HierarchicalOperationWorkerConfig> actorConfigToWorkerConfigFunc =
            actorConfig -> HierarchicalOperationWorkerConfig.builder()
                    .partitions(actorConfig.getPartitions())
                    .maxSizePerPartition(actorConfig.getMaxSizePerPartition())
                    .maxConcurrencyPerPartition(actorConfig.getMaxConcurrencyPerPartition())
                    .retryConfig(actorConfig.getRetryConfig())
                    .exceptionHandlerConfig(actorConfig.getExceptionHandlerConfig())
                    .build();


    static <MessageType extends Enum<MessageType>> HighLevelActorConfig hierarchicalActorConfig(
            MessageType messageType,
            RoutingKey routingKeyData,
            HierarchicalOperationWorkerConfig workerConfig,
            HierarchialHighLevelActorConfig mainActorConfig) {
        val useParentConfigInWorker = mainActorConfig.isUseParentConfigInWorker();
        return HighLevelActorConfig.builder()
                // Custom fields
                .executorName(executorName(mainActorConfig.getExecutorName(), messageType, routingKeyData))

                .partitions(useParentConfigInWorker ? mainActorConfig.getPartitions() : workerConfig.getPartitions())
                .maxSizePerPartition(useParentConfigInWorker ? mainActorConfig.getMaxSizePerPartition() : workerConfig.getMaxSizePerPartition())
                .maxConcurrencyPerPartition(useParentConfigInWorker ? mainActorConfig.getMaxConcurrencyPerPartition() : workerConfig.getMaxConcurrencyPerPartition())
                .retryConfig(useParentConfigInWorker ? mainActorConfig.getRetryConfig() : workerConfig.getRetryConfig())
                .exceptionHandlerConfig(useParentConfigInWorker ? mainActorConfig.getExceptionHandlerConfig() : workerConfig.getExceptionHandlerConfig())
                .metricDisabled(mainActorConfig.isMetricDisabled())
                .build();
    }

    private static <MessageType extends Enum<MessageType>> String executorName(final String parentExchangeName,
                                                                               final MessageType messageType,
                                                                               final RoutingKey routingKeyData) {
        val routingKey = routingKeyData.getRoutingKey();

        if (!StringUtils.isEmpty(parentExchangeName)) {
            // For backward compatibility
            if(routingKey.isEmpty()) {
                return parentExchangeName;
            }

            return beautifierFunction.apply(Stream.of(parentExchangeName, String.join(".", routingKey)), ".");
        }

        return beautifierFunction.apply(Stream.of(EXECUTORS, String.join(".", routingKey), messageType.name()), ".");
    }
}