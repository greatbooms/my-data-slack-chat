package com.mydata.slackbot;

import com.mydata.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SlackEventControllerTest extends PostgresIntegrationTest {
    MockMvc mockMvc;
    @Autowired WebApplicationContext webApplicationContext;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void returnsPlainTextChallengeForUrlVerification() throws Exception {
        mockMvc.perform(post("/slack/events")
                .header("X-Slack-Request-Timestamp", "1531420618")
                .header("X-Slack-Signature", "test-bypass")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"url_verification","challenge":"challenge-token"}
                    """))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
            .andExpect(content().string("challenge-token"));
    }

    @Test
    void returnsOkForUnsupportedEvent() throws Exception {
        mockMvc.perform(post("/slack/events")
                .header("X-Slack-Request-Timestamp", "1531420618")
                .header("X-Slack-Signature", "test-bypass")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"event_callback","event":{"type":"message"}}
                    """))
            .andExpect(status().isOk())
            .andExpect(content().string("ok"));
    }

    @Test
    void rejectsInvalidSignature() throws Exception {
        mockMvc.perform(post("/slack/events")
                .header("X-Slack-Request-Timestamp", "1531420618")
                .header("X-Slack-Signature", "invalid")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"type":"url_verification","challenge":"challenge-token"}
                    """))
            .andExpect(status().isUnauthorized());
    }
}
