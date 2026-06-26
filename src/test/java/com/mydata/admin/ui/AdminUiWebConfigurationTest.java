package com.mydata.admin.ui;

import com.mydata.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUiWebConfigurationTest extends PostgresIntegrationTest {
    MockMvc mockMvc;
    @Autowired WebApplicationContext webApplicationContext;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .build();
    }

    @Test
    void servesAdminUiSpaForLoginRootAndNestedRoutes() throws Exception {
        mockMvc.perform(get("/admin-ui/login"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("my-data-admin-ui")));

        mockMvc.perform(get("/admin-ui").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("my-data-admin-ui")));

        mockMvc.perform(get("/admin-ui/data-sources/123").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("my-data-admin-ui")));

        mockMvc.perform(get("/admin-ui/assets/test.js"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("asset-ok")));
    }

    @Test
    void redirectsAnonymousAdminUiApplicationRoutesToLogin() throws Exception {
        mockMvc.perform(get("/admin-ui").accept(MediaType.TEXT_HTML))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin-ui/login"));

        mockMvc.perform(get("/admin-ui/data-sources").accept(MediaType.TEXT_HTML))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin-ui/login"));
    }
}
