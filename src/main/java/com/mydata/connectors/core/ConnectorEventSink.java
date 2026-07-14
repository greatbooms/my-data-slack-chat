package com.mydata.connectors.core;

public interface ConnectorEventSink {
    void onDocument(ConnectorDocumentEvent event);

    void onFailure(ConnectorFailureEvent event);
}
