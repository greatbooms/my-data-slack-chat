package com.mydata.admin.users;

import com.mydata.users.UserRole;
import com.mydata.users.UserStatus;

public final class AdminUserInputs {
    private AdminUserInputs() {
    }

    public record CreateUserInput(
        String email,
        String displayName,
        UserRole role,
        String password
    ) {
    }

    public record UpdateUserInput(
        String displayName,
        UserRole role,
        UserStatus status
    ) {
    }

    public record ResetUserPasswordInput(String password) {
    }
}
