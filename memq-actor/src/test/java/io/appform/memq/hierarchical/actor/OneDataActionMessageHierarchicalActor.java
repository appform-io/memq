package io.appform.memq.hierarchical.actor;


import io.appform.memq.ActorSystem;
import io.appform.memq.actor.MessageMeta;
import io.appform.memq.hierarchical.HierarchicalActorConfig;
import io.appform.memq.hierarchical.HierarchicalHighLevelActor;
import io.appform.memq.hierarchical.data.ActionMessage;
import io.appform.memq.hierarchical.data.FlowType;

public class OneDataActionMessageHierarchicalActor extends HierarchicalHighLevelActor<FlowType, ActionMessage> {


    public OneDataActionMessageHierarchicalActor(final HierarchicalActorConfig hierarchicalTreeConfig,
                                                 final ActorSystem actorSystem) {
        super(FlowType.FLOW_ONE, hierarchicalTreeConfig, actorSystem);
    }

    @Override
    protected boolean handle(ActionMessage actionMessage, MessageMeta messageMeta) {
        System.out.println("FLOW_ONE : " + actionMessage);
        return true;
    }

}