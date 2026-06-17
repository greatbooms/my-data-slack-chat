package com.mydata.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17")
        .withDatabaseName("my_data_test")
        .withUsername("my_data")
        .withPassword("my_data");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
