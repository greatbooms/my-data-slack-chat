package com.mydata.admin;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminTokenAuthenticationInterceptorTest {
    @Test
    void rejectsBlankConfiguredAdminToken() {
        assertThatThrownBy(() -> new AdminTokenAuthenticationInterceptor(" "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("관리자 토큰");
    }
}
