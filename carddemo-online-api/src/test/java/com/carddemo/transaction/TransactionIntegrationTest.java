package com.carddemo.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Testcontainers
class TransactionIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @Test void CT00_CT01_RETURN_TO_PREV_SCREEN_menu_routes() throws Exception {
        mvc.perform(post("/api/menus/main/select").with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{\"option\":\"6\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.route").value("/transactions"))
                .andExpect(jsonPath("$.tranId").value("CT00"));
        mvc.perform(post("/api/menus/main/select").with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{\"option\":\"7\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.route").value("/transactions/view"))
                .andExpect(jsonPath("$.tranId").value("CT01"));
    }

    @Test void CT00_MAIN_PARA_STARTBR_READNEXT_ENDBR_CT01_READ_TRANSACT_FILE() throws Exception {
        jdbc.update("DELETE FROM \"transaction\"");
        for (int i = 1; i <= 12; i++) {
            jdbc.update("INSERT INTO \"transaction\" VALUES (?, '01', 1, 'TEST', 'SYNTHETIC', -1.23, 1, 'SHOP', 'CITY', '00000', '0000000000000000', '2022-07-05 10:00:00.000000', '2022-07-05 10:00:00.000000', '')", String.format("%016d", i));
        }
        mvc.perform(get("/api/transactions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/transactions").with(jwt())).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(10)).andExpect(jsonPath("$.rows[0].transaction_id").value("0000000000000001"));
        mvc.perform(get("/api/transactions").param("startKey", "0000000000000011").with(jwt()))
                .andExpect(jsonPath("$.rows.length()").value(2));
        mvc.perform(get("/api/transactions").param("startKey", "0000000000000010").param("direction", "FORWARD").with(jwt()))
                .andExpect(jsonPath("$.rows[0].transaction_id").value("0000000000000011"))
                .andExpect(jsonPath("$.pageNumber").value(2));
        mvc.perform(get("/api/transactions").param("startKey", "0000000000000011").param("direction", "BACKWARD").param("pageNumber", "2").with(jwt()))
                .andExpect(jsonPath("$.rows[0].transaction_id").value("0000000000000001"))
                .andExpect(jsonPath("$.rows.length()").value(10));
        mvc.perform(get("/api/transactions/view").param("transactionId", "0000000000000001").with(jwt()))
                .andExpect(jsonPath("$.transaction.amount").value(-1.23));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"transaction\"", Integer.class)).isEqualTo(12);
    }
}
