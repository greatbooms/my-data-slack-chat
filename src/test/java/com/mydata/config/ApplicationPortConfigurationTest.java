package com.mydata.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationPortConfigurationTest {
    @Test
    void uses50506AsDefaultServerPort() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));

        Properties properties = yaml.getObject();

        assertThat(properties)
            .isNotNull()
            .containsEntry("server.port", "${SERVER_PORT:50506}");
    }

    @Test
    void envExampleUses50506AsServerPort() throws Exception {
        String envExample = Files.readString(Path.of(".env.example"));

        assertThat(envExample).contains("SERVER_PORT=50506");
    }
}
