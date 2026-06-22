package com.mydata.admin.users;

import java.util.List;

public record AdminUserPagePayload(
    List<AdminUserPayload> items,
    int totalCount
) {
}
