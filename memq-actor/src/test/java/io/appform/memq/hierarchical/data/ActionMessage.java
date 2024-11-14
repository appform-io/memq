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
        @JsonSubTypes.Type(name = FlowType.C2M_AUTH_FLOW_TEXT, value = C2MDataActionMessage.class),
        @JsonSubTypes.Type(name = FlowType.C2C_AUTH_FLOW_TEXT, value = C2CDataActionMessage.class)
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