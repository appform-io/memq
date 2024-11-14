package io.appform.memq.hierarchical;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlowHierarchicalMemqActorConfig<MessageType extends Enum<MessageType>> {
    private Map<MessageType, HierarchialHighLevelActorConfig> workers;
}
