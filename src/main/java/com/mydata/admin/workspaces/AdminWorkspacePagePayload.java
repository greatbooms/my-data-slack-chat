package com.mydata.admin.workspaces;

import java.util.List;

public record AdminWorkspacePagePayload(
    List<AdminWorkspacePayload> items,
    int totalCount
) {
}
