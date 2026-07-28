package com.mydata.connectors.googledrive;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Clock;

@Configuration
@EnableConfigurationProperties(GoogleDriveProperties.class)
class GoogleDriveConfiguration {
    @Bean
    GoogleDriveClient googleDriveClient(GoogleDriveProperties properties, ObjectMapper objectMapper) {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout())
            .build();
        GoogleOAuthTokenProvider tokenProvider = new GoogleOAuthTokenProvider(
            httpClient,
            objectMapper,
            properties.tokenUrl(),
            properties.clientId(),
            properties.clientSecret(),
            properties.refreshToken(),
            properties.requestTimeout(),
            Clock.systemUTC()
        );
        return new GoogleDriveApiClient(
            httpClient,
            objectMapper,
            properties.apiBaseUrl(),
            tokenProvider,
            properties.requestTimeout()
        );
    }
}
