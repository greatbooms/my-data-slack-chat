package com.mydata.connectors.core;

public record ConnectorFailureEvent(
    ConnectorItemReference reference,
    ConnectorFailureStage stage,
    String userSafeReason
) {
    public String formattedReason() {
        return "[%s] %s (%s): %s".formatted(
            reference.type(), reference.displayPath(), stage, userSafeReason
        );
    }
}
