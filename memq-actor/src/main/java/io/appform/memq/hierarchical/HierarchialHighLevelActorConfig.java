package io.appform.memq.hierarchical;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.appform.memq.HighLevelActorConfig;
import io.appform.memq.hierarchical.tree.HierarchicalDataStoreTreeNode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@EqualsAndHashCode
@ToString
@NoArgsConstructor
public class HierarchialHighLevelActorConfig extends HighLevelActorConfig {

    /**
     * <p>This param will reused all Parent Level ActorConfig while creating all child actors,
     * if marked as false, every children will need tp provide Actor config specific to child</p>
     *
     */
    private boolean useParentConfigInWorker = true;

    @JsonUnwrapped
    private HierarchicalDataStoreTreeNode<String, HierarchicalOperationWorkerConfig> childrenData;

}
