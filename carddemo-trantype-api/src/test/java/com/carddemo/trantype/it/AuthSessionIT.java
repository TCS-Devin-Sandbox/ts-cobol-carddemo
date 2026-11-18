package com.carddemo.trantype.it;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Real session + cookie CSRF flow (no spring-security-test post-processors, which replace the CSRF
 * repository of a shared context). Distinct property forces its own application context.
 */
@SpringBootTest(properties = "carddemo.test.context=auth")
@AutoConfigureMockMvc
@Testcontainers
class AuthSessionIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    private MvcResult login(String token, String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
                        .cookie(new Cookie("XSRF-TOKEN", token))
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new LoginBody(username, password))))
                .andReturn();
    }

    record LoginBody(String username, String password) {
    }

    @Test
    void sessionLoginFlow() throws Exception {
        MvcResult csrf = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andReturn();
        String token = mapper.readTree(csrf.getResponse().getContentAsString()).get("token").asText();
        org.assertj.core.api.Assertions.assertThat(csrf.getResponse().getHeaders("Set-Cookie"))
                .anyMatch(h -> h.startsWith("XSRF-TOKEN=" + token));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isForbidden());

        MvcResult wrong = login(token, "admin", "wrong");
        org.assertj.core.api.Assertions.assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
        org.assertj.core.api.Assertions.assertThat(wrong.getResponse().getContentAsString())
                .contains("Wrong Password. Try again ...");

        MvcResult admin = login(token, "admin", "admin123");
        org.assertj.core.api.Assertions.assertThat(admin.getResponse().getStatus()).isEqualTo(200);
        MockHttpSession adminSession = (MockHttpSession) admin.getRequest().getSession(false);
        mvc.perform(get("/api/transaction-types").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].trType").value("01"));
        mvc.perform(get("/api/auth/me").session(adminSession))
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));

        MvcResult user = login(token, "user", "user123");
        MockHttpSession userSession = (MockHttpSession) user.getRequest().getSession(false);
        mvc.perform(get("/api/transaction-types").session(userSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access restricted to administrators"));

        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }
}
