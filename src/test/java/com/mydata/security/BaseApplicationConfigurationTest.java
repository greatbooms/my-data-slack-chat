package com.mydata.security;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BaseApplicationConfigurationTest {
    @Test
    void baseConfigurationDoesNotProvideKnownSecuritySecretDefaults() throws Exception {
        String applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(applicationYaml)
            .doesNotContain("local-admin-token")
            .doesNotContain("local-signing-secret");
    }
}
