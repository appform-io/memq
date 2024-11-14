package io.appform.memq.hierarchical;

import com.fasterxml.jackson.core.type.TypeReference;
import io.appform.memq.ActorSystem;
import io.appform.memq.MemQTestExtension;
import io.appform.memq.hierarchical.actor.FlowTypeHierarchicalActorBuilder;
import io.appform.memq.hierarchical.data.ActionMessage;
import io.appform.memq.hierarchical.data.C2CDataActionMessage;
import io.appform.memq.hierarchical.data.C2MDataActionMessage;
import io.appform.memq.hierarchical.data.FlowType;
import io.appform.memq.hierarchical.tree.key.RoutingKey;
import io.appform.memq.util.YamlReader;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@ExtendWith(MemQTestExtension.class)
public class HierarchicalHighLevelActorTest {

    private final static FlowHierarchicalMemqActorConfig<FlowType> RMQ_CONFIG = YamlReader.loadConfig("rmqHierarchicalMemq.yaml", new TypeReference<>() {
    });
    private Map<FlowType, HierarchicalHighLevelActor<FlowType, ActionMessage>> actorActors;

    enum HierarchicalHighLevelActorType {
        C2M_AUTH_FLOW,
        C2C_AUTH_FLOW;
    }

    static final int THREAD_POOL_SIZE = 10;

    @SneakyThrows
    public void createActors(ActorSystem actorSystem) {
        actorActors = RMQ_CONFIG.getWorkers()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getKey().accept(new FlowTypeHierarchicalActorBuilder(e.getValue(), actorSystem))));
    }

    @Test
    @SneakyThrows
    void testSuccessSinglePartition(ActorSystem actorSystem) {
        createActors(actorSystem);
        val messages = Map.of(
                RoutingKey.builder().list(List.of("")).build(),
                C2MDataActionMessage.builder()
                        .data("C2M")
                        .build(),

                RoutingKey.builder().list(List.of("REGULAR", "JAR")).build(),
                C2MDataActionMessage.builder()
                        .data("C2M-REGULAR-JAR-SOME")
                        .build(),

                RoutingKey.builder().list(List.of("REGULAR")).build(),
                C2CDataActionMessage.builder()
                        .data("C2C-REGULAR")
                        .build(),

                RoutingKey.builder().list(List.of("C2C_AUTH_FLOW")).build(),
                C2CDataActionMessage.builder()
                        .data("C2C")
                        .build(),

                RoutingKey.builder().list(List.of("FULL_AUTH", "JAR")).build(),
                C2MDataActionMessage.builder()
                        .data("C2M-FULL_AUTH-JAR-SOME")
                        .build()
        );

        messages.forEach((routingKey, message) -> {
            val flowType = message.getType();

            if (actorActors.containsKey(flowType)) {
                val router = actorActors.get(flowType);
                Assertions.assertNotNull(router);

                val flowLevelPrefix = Arrays.asList(RMQ_CONFIG.getWorkers().get(flowType).getExecutorName().split("\\."));
                System.out.println("flowLevelPrefix" + flowLevelPrefix);

                val worker = router.getActor().getWorker().get(flowType, routingKey);
                Assertions.assertNotNull(worker);

                val routingKeyWorker = worker.getRoutingKey();
                if(!worker.getRoutingKey().getRoutingKey().isEmpty()) {
                    val routingKeyWorkerStr = String.join(",",routingKeyWorker.getRoutingKey());
                    val routingKeyStr = String.join(",", routingKey.getRoutingKey());
                    Assertions.assertEquals(routingKeyWorkerStr, routingKeyStr);
                }
                message.setExecutorName(String.join("-", routingKeyWorker.getRoutingKey()));
                try {
                    router.publish(routingKey, message);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });

    }


}
