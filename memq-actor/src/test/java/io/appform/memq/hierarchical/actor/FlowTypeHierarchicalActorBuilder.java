package io.appform.memq.hierarchical.actor;


import io.appform.memq.ActorSystem;
import io.appform.memq.hierarchical.HierarchialHighLevelActorConfig;
import io.appform.memq.hierarchical.HierarchicalHighLevelActor;
import io.appform.memq.hierarchical.data.ActionMessage;
import io.appform.memq.hierarchical.data.FlowType;

public class FlowTypeHierarchicalActorBuilder implements FlowType.FlowTypeVisitor<HierarchicalHighLevelActor<FlowType, ActionMessage>> {

    private final HierarchialHighLevelActorConfig hierarchicalTreeConfig;
    private final ActorSystem actorSystem;

    public FlowTypeHierarchicalActorBuilder(final HierarchialHighLevelActorConfig hierarchicalTreeConfig,
                                            final ActorSystem actorSystem) {
        this.hierarchicalTreeConfig = hierarchicalTreeConfig;
        this.actorSystem = actorSystem;
    }

    @Override
    public HierarchicalHighLevelActor<FlowType, ActionMessage> visitC2M() {
        return new C2MDataActionMessageHierarchicalActor(hierarchicalTreeConfig, actorSystem);
    }

    @Override
    public HierarchicalHighLevelActor<FlowType, ActionMessage> visitC2C() {
        return new C2CDataActionMessageHierarchicalActor(hierarchicalTreeConfig, actorSystem);
    }
}
