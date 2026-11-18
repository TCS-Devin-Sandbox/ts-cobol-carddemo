package com.carddemo.trantype.web;

import com.carddemo.trantype.Messages;
import com.carddemo.trantype.security.SecurityConfig;
import com.carddemo.trantype.security.SecurityProperties;
import com.carddemo.trantype.service.SaveResult;
import com.carddemo.trantype.service.TransactionTypeService;
import com.carddemo.trantype.web.dto.TransactionTypeDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionTypeController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
@TestPropertySource(properties = "carddemo.security.jwt-secret=test-secret-test-secret-test-secret-123")
class TransactionTypeControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    TransactionTypeService service;

    private static RequestPostProcessor admin() {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static RequestPostProcessor regularUser() {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"));
    }

    @Test
    void unauthenticatedIsRejected() throws Exception { // REQ-EDGE-013
        mvc.perform(get("/api/transaction-types"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value(Messages.AUTH_REQUIRED));
        verifyNoInteractions(service);
    }

    @Test
    void nonAdminIsForbidden() throws Exception { // REQ-EDGE-012
        mvc.perform(get("/api/transaction-types").with(regularUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(Messages.ADMIN_ONLY));
        mvc.perform(delete("/api/transaction-types/01").with(regularUser())).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void createValidatesKeyBeforeDescription() throws Exception { // REQ-CTTU-002/010, REQ-NFR-009
        mvc.perform(post("/api/transaction-types").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trType\":\"\",\"trDescription\":\"BAD-DESC\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(Messages.CODE_REQUIRED));
        mvc.perform(post("/api/transaction-types").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trType\":\"08\",\"trDescription\":\"BAD-DESC\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Messages.DESC_ALPHANUM));
        verifyNoInteractions(service);
    }

    @Test
    void createReturns201WithLocation() throws Exception { // REQ-CTTU-013
        when(service.create("8", "FEES")).thenReturn(new TransactionTypeDto("08", "FEES"));

        mvc.perform(post("/api/transaction-types").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trType\":\"8\",\"trDescription\":\"FEES\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/transaction-types/08"))
                .andExpect(jsonPath("$.trType").value("08"));
    }

    @Test
    void upsertInsertReturns201() throws Exception {
        when(service.update("42", "NEW", true))
                .thenReturn(new SaveResult(new TransactionTypeDto("42", "NEW"), true));

        mvc.perform(put("/api/transaction-types/42?upsert=true").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"trDescription\":\"NEW\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void updateReturns200() throws Exception {
        when(service.update("01", "PURCHASES", false))
                .thenReturn(new SaveResult(new TransactionTypeDto("01", "PURCHASES"), false));

        mvc.perform(put("/api/transaction-types/01").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"trDescription\":\"PURCHASES\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trDescription").value("PURCHASES"));
    }

    @Test
    void malformedBody() throws Exception {
        mvc.perform(put("/api/transaction-types/01").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Messages.MALFORMED_REQUEST));
    }

    @Test
    void deleteReturns204() throws Exception {
        mvc.perform(delete("/api/transaction-types/09").with(admin())).andExpect(status().isNoContent());
    }

    @Test
    void listDelegates() throws Exception {
        when(service.list(any(), any(), any(), any(), any())).thenReturn(
                new com.carddemo.trantype.web.dto.TransactionTypePage(java.util.List.of(), null, null, null,
                        false, false, 1, 7, null, Messages.NO_RECORDS_FOUND));

        mvc.perform(get("/api/transaction-types?size=7").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorMessage").value(Messages.NO_RECORDS_FOUND))
                .andExpect(jsonPath("$.pageNumber").value(1));
    }
}
