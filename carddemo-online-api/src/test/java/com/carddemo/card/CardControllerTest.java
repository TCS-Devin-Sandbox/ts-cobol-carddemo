package com.carddemo.card;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CardController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
@ActiveProfiles("dev")
class CardControllerTest {
    @Autowired MockMvc mvc;
    @MockBean CardService cards;

    @Test @DisplayName("CCLI 9000-READ-FORWARD / 9100-READ-BACKWARDS; CCDL 9100-GETCARD-BYACCTCARD: file failure")
    void databaseFailureDoesNotLeakInfrastructure() throws Exception {
        when(cards.list(anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyBoolean()))
                .thenThrow(new DataAccessResourceFailureException("secret connection details"));
        when(cards.view(anyString(), anyString())).thenThrow(new DataAccessResourceFailureException("secret"));
        for (String path : new String[]{"/api/cards", "/api/cards?key=PF7", "/api/cards/view"}) {
            mvc.perform(get(path).with(jwt())).andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Error reading Card Data File"))
                    .andExpect(jsonPath("$.severity").value("error"));
        }
    }
}
