package io.appform.memq.hierarchical.data;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TwoDataActionMessage extends ActionMessage {
    private String data;

    public TwoDataActionMessage() {
        super(FlowType.FLOW_TWO);
    }

    @Builder
    public TwoDataActionMessage(String data) {
        this();
        this.data = data;
    }

    @Override
    public String id() {
        return data;
    }

}