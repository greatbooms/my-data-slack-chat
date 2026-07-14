package com.mydata.connectors.core;

public record ConnectorDocumentEvent(
    RawExternalDocument document,
    ConnectorItemReference reference
) {
}
