package io.appform.memq.hierarchical;

import com.fasterxml.jackson.core.type.TypeReference;
import io.appform.memq.ActorSystem;
import io.appform.memq.MemQTestExtension;
import io.appform.memq.hierarchical.actor.FlowTypeHierarchicalActorBuilder;
import io.appform.memq.hierarchical.data.ActionMessage;
import io.appform.memq.hierarchical.data.FlowType;
import io.appform.memq.hierarchical.data.OneDataActionMessage;
import io.appform.memq.hierarchical.data.TwoDataActionMessage;
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
                OneDataActionMessage.builder()
                        .data("FLOW_ONE")
                        .build(),

                RoutingKey.builder().list(List.of("L1", "L2")).build(),
                OneDataActionMessage.builder()
                        .data("FLOW_ONE-L1-L2-SOME")
                        .build(),

                RoutingKey.builder().list(List.of("L1")).build(),
                TwoDataActionMessage.builder()
                        .data("FLOW_TWO-L1")
                        .build(),

                RoutingKey.builder().list(List.of("")).build(),
                TwoDataActionMessage.builder()
                        .data("FLOW_TWO")
                        .build()

//                RoutingKey.builder().list(List.of("L2", "L1")).build(),
//                OneDataActionMessage.builder()
//                        .data("FLOW_ONE-L2-L1-SOME")
//                        .build()
        );

        messages.forEach((routingKey, message) -> {
            val flowType = message.getType();

            if (actorActors.containsKey(flowType)) {
                val router = actorActors.get(flowType);
                Assertions.assertNotNull(router);
                val worker = router.getActor().getWorker().get(flowType, routingKey);
                Assertions.assertNotNull(worker);

                val routingKeyWorker = worker.getName();
                if(!routingKeyWorker.isEmpty()) {
                    val routingKeyStr = String.join(".", routingKey.getRoutingKey());
                    System.out.println(routingKeyWorker + " " + routingKeyStr);
                    Assertions.assertTrue(routingKeyWorker.contains(routingKeyStr));
                }
                message.setExecutorName(String.join("-", routingKeyWorker));
                try {
                    router.publish(routingKey, message);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });

    }


}
