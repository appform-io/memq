package io.appform.memq.hierarchical.actor;


import io.appform.memq.ActorSystem;
import io.appform.memq.hierarchical.HierarchicalActorConfig;
import io.appform.memq.hierarchical.HierarchicalHighLevelActor;
import io.appform.memq.hierarchical.data.ActionMessage;
import io.appform.memq.hierarchical.data.FlowType;

public class FlowTypeHierarchicalActorBuilder implements FlowType.FlowTypeVisitor<HierarchicalHighLevelActor<FlowType, ActionMessage>> {

    private final HierarchicalActorConfig hierarchicalTreeConfig;
    private final ActorSystem actorSystem;

    public FlowTypeHierarchicalActorBuilder(final HierarchicalActorConfig hierarchicalTreeConfig,
                                            final ActorSystem actorSystem) {
        this.hierarchicalTreeConfig = hierarchicalTreeConfig;
        this.actorSystem = actorSystem;
    }

    @Override
    public HierarchicalHighLevelActor<FlowType, ActionMessage> visitOne() {
        return new OneDataActionMessageHierarchicalActor(hierarchicalTreeConfig, actorSystem);
    }

    @Override
    public HierarchicalHighLevelActor<FlowType, ActionMessage> visitTwo() {
        return new TwoDataActionMessageHierarchicalActor(hierarchicalTreeConfig, actorSystem);
    }
}
