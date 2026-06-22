package com.mydata.admin.ui;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

@Configuration
public class AdminUiWebConfiguration implements WebMvcConfigurer {
    private static final String ADMIN_UI_RESOURCE_LOCATION = "classpath:/static/admin-ui/";
    private static final String INDEX_HTML = "index.html";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/admin-ui/**")
            .addResourceLocations(ADMIN_UI_RESOURCE_LOCATION)
            .resourceChain(false)
            .addResolver(new AdminUiPathResourceResolver());
    }

    private static final class AdminUiPathResourceResolver extends PathResourceResolver {
        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requestedResource = location.createRelative(resourcePath);
            if (requestedResource.exists() && requestedResource.isReadable()) {
                return requestedResource;
            }
            if (isAssetRequest(resourcePath)) {
                return null;
            }
            return location.createRelative(INDEX_HTML);
        }

        private static boolean isAssetRequest(String resourcePath) {
            return resourcePath.startsWith("assets/") || resourcePath.contains(".");
        }
    }
}

@Controller
class AdminUiIndexController {
    private static final String ADMIN_UI_INDEX = "classpath:/static/admin-ui/index.html";

    private final ResourceLoader resourceLoader;

    AdminUiIndexController(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @GetMapping(value = {"/admin-ui", "/admin-ui/"}, produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<Resource> index() {
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(resourceLoader.getResource(ADMIN_UI_INDEX));
    }
}
