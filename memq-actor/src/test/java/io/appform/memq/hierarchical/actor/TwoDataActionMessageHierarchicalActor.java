package io.appform.memq.hierarchical.actor;

import io.appform.memq.ActorSystem;
import io.appform.memq.actor.MessageMeta;
import io.appform.memq.hierarchical.HierarchicalActorConfig;
import io.appform.memq.hierarchical.HierarchicalHighLevelActor;
import io.appform.memq.hierarchical.data.ActionMessage;
import io.appform.memq.hierarchical.data.FlowType;


public class TwoDataActionMessageHierarchicalActor extends HierarchicalHighLevelActor<FlowType, ActionMessage> {


    public TwoDataActionMessageHierarchicalActor(final HierarchicalActorConfig hierarchicalTreeConfig,
                                                 final ActorSystem actorSystem) {
        super(FlowType.FLOW_TWO, hierarchicalTreeConfig, actorSystem);
    }

    @Override
    protected boolean handle(ActionMessage actionMessage, MessageMeta messageMetadata) {
        System.out.println("FLOW_TWO : " + actionMessage);
        return true;
    }
}