package io.appform.memq.hierarchical.data;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.appform.memq.actor.Message;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.NotNull;

@Data
@EqualsAndHashCode
@ToString
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = FlowType.FLOW_ONE_TEXT, value = OneDataActionMessage.class),
        @JsonSubTypes.Type(name = FlowType.FLOW_TWO_TEXT, value = TwoDataActionMessage.class)
})
public abstract class ActionMessage implements Message {

    @NotNull
    private final FlowType type;

    @Setter
    private String executorName;

    protected ActionMessage(FlowType type) {
        this.type = type;
    }

}