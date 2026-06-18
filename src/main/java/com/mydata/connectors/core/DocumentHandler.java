package com.mydata.connectors.core;

@FunctionalInterface
public interface DocumentHandler {
    void handle(RawExternalDocument document);
}
