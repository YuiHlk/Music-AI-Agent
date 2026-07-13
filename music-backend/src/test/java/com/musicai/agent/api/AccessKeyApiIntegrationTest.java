package com.musicai.agent.api;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "music-ai.security.access-key=portfolio-secret")
@AutoConfigureMockMvc
class AccessKeyApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void protectsApiAndUsesAnHttpOnlySessionCookieForEventSourceCompatibility() throws Exception {
        mvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("AUTHENTICATION_REQUIRED")));

        Cookie session = mvc.perform(post("/api/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessKey\":\"portfolio-secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated", is(true)))
                .andReturn().getResponse().getCookie(AccessKeyFilter.SESSION_COOKIE);

        assertThat(session).isNotNull();
        assertThat(session.isHttpOnly()).isTrue();
        assertThat(session.getValue()).doesNotContain("portfolio-secret");

        mvc.perform(get("/api/projects").cookie(session))
                .andExpect(status().isOk());
    }
}
