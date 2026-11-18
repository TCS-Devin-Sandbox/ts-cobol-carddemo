package com.carddemo.trantype.web;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carddemo.trantype.security.SecurityConfig;
import com.carddemo.trantype.security.SecurityProperties;
import com.carddemo.trantype.service.TransactionTypeDto;
import com.carddemo.trantype.service.TransactionTypeService;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(TransactionTypeController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
class TransactionTypeControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    TransactionTypeService service;

    private static MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder b) {
        return b.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test
    void unauthenticatedIs401() throws Exception {
        mvc.perform(get("/api/transaction-types")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void nonAdminIs403() throws Exception {
        mvc.perform(get("/api/transaction-types").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access restricted to administrators"));
    }

    @Test
    void createValidationUsesSourceMessageKeyFirst() throws Exception {
        mvc.perform(admin(post("/api/transaction-types")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trType\":\"AB\",\"trDescription\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Tran Type code must be numeric."));
    }

    @Test
    void createDescriptionValidation() throws Exception {
        mvc.perform(admin(post("/api/transaction-types")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trType\":\"08\",\"trDescription\":\"BAD-DESC\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Transaction Desc can have numbers or alphabets only."));
    }

    @Test
    void duplicateKeyIs409() throws Exception {
        when(service.create(anyString(), anyString())).thenThrow(new DataIntegrityViolationException("dup",
                new SQLException("duplicate key", "23505")));
        mvc.perform(admin(post("/api/transaction-types")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trType\":\"01\",\"trDescription\":\"PURCHASE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_KEY"))
                .andExpect(jsonPath("$.message").value(
                        "Error inserting record into: TRANSACTION_TYPE Table. SQLCODE:-803"));
    }

    @Test
    void foreignKeyRestrictIs409() throws Exception {
        doThrow(new DataIntegrityViolationException("fk", new SQLException("fk", "23503")))
                .when(service).delete("01");
        mvc.perform(admin(delete("/api/transaction-types/01")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FK_RESTRICT"))
                .andExpect(jsonPath("$.message").value("Please delete associated child records first:SQLCODE :-532"));
    }

    @Test
    void getReturnsDto() throws Exception {
        when(service.get("01")).thenReturn(new TransactionTypeDto("01", "PURCHASE"));
        mvc.perform(admin(get("/api/transaction-types/01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trDescription").value("PURCHASE"));
    }

    @Test
    void pageSizeOutOfRangeIs400() throws Exception {
        mvc.perform(admin(get("/api/transaction-types").param("size", "0")))
                .andExpect(status().isBadRequest());
    }
}
