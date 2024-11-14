package io.appform.memq.hierarchical.actor;

import io.appform.memq.ActorSystem;
import io.appform.memq.actor.MessageMeta;
import io.appform.memq.hierarchical.HierarchialHighLevelActorConfig;
import io.appform.memq.hierarchical.HierarchicalHighLevelActor;
import io.appform.memq.hierarchical.data.ActionMessage;
import io.appform.memq.hierarchical.data.FlowType;


public class C2CDataActionMessageHierarchicalActor extends HierarchicalHighLevelActor<FlowType, ActionMessage> {


    public C2CDataActionMessageHierarchicalActor(final HierarchialHighLevelActorConfig hierarchicalTreeConfig,
                                                 final ActorSystem actorSystem) {
        super(FlowType.C2C_AUTH_FLOW, hierarchicalTreeConfig, actorSystem);
    }

    @Override
    protected boolean handle(ActionMessage actionMessage, MessageMeta messageMetadata) {
        System.out.println("C2C : " + actionMessage);
        return true;
    }
}