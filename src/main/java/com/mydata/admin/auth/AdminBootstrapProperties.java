package com.mydata.admin.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "my-data.admin.bootstrap")
public record AdminBootstrapProperties(String email, String password, String displayName) {
}
