package io.appform.memq.hierarchical.actor;


import io.appform.memq.ActorSystem;
import io.appform.memq.actor.MessageMeta;
import io.appform.memq.hierarchical.HierarchialHighLevelActorConfig;
import io.appform.memq.hierarchical.HierarchicalHighLevelActor;
import io.appform.memq.hierarchical.data.ActionMessage;
import io.appform.memq.hierarchical.data.FlowType;

public class C2MDataActionMessageHierarchicalActor extends HierarchicalHighLevelActor<FlowType, ActionMessage> {


    public C2MDataActionMessageHierarchicalActor(final HierarchialHighLevelActorConfig hierarchicalTreeConfig,
                                                 final ActorSystem actorSystem) {
        super(FlowType.C2M_AUTH_FLOW, hierarchicalTreeConfig, actorSystem);
    }

    @Override
    protected boolean handle(ActionMessage actionMessage, MessageMeta messageMeta) {
        System.out.println("C2M : " + actionMessage);
        return true;
    }

}