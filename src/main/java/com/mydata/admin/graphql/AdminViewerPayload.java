package com.mydata.admin.graphql;

import com.mydata.users.UserRole;

import java.util.UUID;

public record AdminViewerPayload(
    UUID id,
    String email,
    String displayName,
    UserRole role
) {
}
