package com.mydata.connectors.core;

import java.util.List;

public record ConnectorItemReference(
    ConnectorItemType type,
    String externalId,
    String title,
    List<String> path
) {
    public ConnectorItemReference {
        path = List.copyOf(path);
    }

    public String qualifiedExternalId() {
        String prefix = switch (type) {
            case PAGE -> "page";
            case DATABASE -> "database";
            case DATA_SOURCE -> "data-source";
            case BLOCK -> "block";
            case FILE -> "file";
            case FOLDER -> "folder";
        };
        return prefix + ":" + externalId;
    }

    public String displayPath() {
        return path.isEmpty() ? title : String.join(" / ", path);
    }
}
