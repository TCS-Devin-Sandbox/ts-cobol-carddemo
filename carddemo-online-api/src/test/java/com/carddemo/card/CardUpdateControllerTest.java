package com.carddemo.card;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.carddemo.trantype.security.SecurityConfig;
import com.carddemo.trantype.security.SecurityProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CardUpdateController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
@ActiveProfiles("dev")
class CardUpdateControllerTest {
    @Autowired MockMvc mvc;
    @MockBean CardUpdateService service;
    @Test @DisplayName("CCUP 0000-MAIN / 9100-GETCARD-BYACCTCARD: authentication and input errors")
    void authenticationAndValidation() throws Exception {
        mvc.perform(get("/api/cards/update")).andExpect(status().isUnauthorized());
        when(service.fetch(anyString(), anyString(), anyString())).thenThrow(new CardService.CardError(org.springframework.http.HttpStatus.BAD_REQUEST, "No input received"));
        mvc.perform(get("/api/cards/update").with(jwt())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("No input received"));
    }
    @Test @DisplayName("CCUP 9200-WRITE-PROCESSING / ABEND-ROUTINE: sanitized write errors")
    void writeFailure() throws Exception {
        when(service.act(anyString(), anyString(), isNull(), anyString())).thenThrow(new DataAccessResourceFailureException("secret"));
        mvc.perform(post("/api/cards/update").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"t\",\"key\":\"PF5\"}"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.message").value("Update of record failed"));
    }
}
