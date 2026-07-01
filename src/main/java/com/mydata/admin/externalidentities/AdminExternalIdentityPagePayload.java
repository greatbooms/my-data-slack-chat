package com.mydata.admin.externalidentities;

import java.util.List;

public record AdminExternalIdentityPagePayload(
    List<AdminExternalIdentityPayload> items,
    int totalCount
) {
}
