package com.carddemo.trantype.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carddemo.trantype.security.SecurityConfig;
import com.carddemo.trantype.service.SaveResult;
import com.carddemo.trantype.service.TransactionTypeDto;
import com.carddemo.trantype.service.TransactionTypeService;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(TransactionTypeController.class)
@Import(SecurityConfig.class)
class TransactionTypeControllerTest {

    private static final RequestPostProcessor ADMIN = user("admin").roles("ADMIN");

    @Autowired
    private MockMvc mvc;

    @MockBean
    private TransactionTypeService service;

    private static DataIntegrityViolationException integrity(String sqlState) {
        return new DataIntegrityViolationException("x",
                new ConstraintViolationException("x", new SQLException("x", sqlState), "c"));
    }

    private static MockHttpServletRequestBuilder putDesc(String trType, String json) {
        return put("/api/transaction-types/" + trType).with(ADMIN).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(get("/api/transaction-types"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void nonAdminIsForbidden() throws Exception {
        mvc.perform(get("/api/transaction-types").with(user("user").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Access restricted to administrators"));
    }

    @Test
    void missingCsrfTokenIsForbidden() throws Exception {
        mvc.perform(delete("/api/transaction-types/01").with(ADMIN)).andExpect(status().isForbidden());
    }

    @Test
    void invalidPathKeyUsesSourceLiterals() throws Exception {
        mvc.perform(get("/api/transaction-types/ab").with(ADMIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Tran Type code must be numeric."));
        mvc.perform(get("/api/transaction-types/00").with(ADMIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tran Type code must not be zero."));
    }

    @Test
    void blankDescriptionUsesSourceLiteral() throws Exception {
        mvc.perform(putDesc("01", "{\"description\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Transaction Desc must be supplied."));
    }

    @Test
    void nonAlphanumericDescriptionUsesSourceLiteral() throws Exception {
        mvc.perform(putDesc("01", "{\"description\":\"BAD-DESC!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Transaction Desc can have numbers or alphabets only."));
    }

    @Test
    void keyEditReportedBeforeDescriptionEdit() throws Exception {
        mvc.perform(putDesc("xx", "{\"description\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tran Type code must be numeric."));
    }

    @Test
    void upsertInsertReturnsCreated() throws Exception {
        when(service.save("42", "ANSWER", true)).thenReturn(new SaveResult(new TransactionTypeDto("42", "ANSWER"), true));
        mvc.perform(putDesc("42", "{\"description\":\"ANSWER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trType").value("42"));
    }

    @Test
    void foreignKeyViolationMapsToChildRecordsMessage() throws Exception {
        doThrow(integrity("23503")).when(service).delete("01");
        mvc.perform(delete("/api/transaction-types/01").with(ADMIN).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHILD_RECORDS_EXIST"))
                .andExpect(jsonPath("$.message").value("Please delete associated child records first:"))
                .andExpect(jsonPath("$.sqlcode").value(-532));
    }

    @Test
    void duplicateKeyMapsToInsertMessage() throws Exception {
        when(service.save(anyString(), anyString(), anyBoolean())).thenThrow(integrity("23505"));
        mvc.perform(putDesc("01", "{\"description\":\"X\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_KEY"))
                .andExpect(jsonPath("$.message").value("Error inserting record into: TRANSACTION_TYPE Table. SQLCODE:"))
                .andExpect(jsonPath("$.sqlcode").value(-803));
    }

    @Test
    void lockFailureMapsToMinus911() throws Exception {
        when(service.save(anyString(), anyString(), anyBoolean())).thenThrow(new CannotAcquireLockException("lock"));
        mvc.perform(putDesc("01", "{\"description\":\"X\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOCK_ERROR"))
                .andExpect(jsonPath("$.message").value("Could not lock record for update"))
                .andExpect(jsonPath("$.sqlcode").value(-911));
    }

    @Test
    void unexpectedErrorIs500() throws Exception {
        when(service.get(any())).thenThrow(new IllegalStateException("boom"));
        mvc.perform(get("/api/transaction-types/01").with(ADMIN))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    @Test
    void invalidDirectionIsBadRequest() throws Exception {
        mvc.perform(get("/api/transaction-types?direction=sideways").with(ADMIN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void databaseUnavailableMapsTo500WithSourceText() throws Exception {
        when(service.get(any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("down"));
        mvc.perform(get("/api/transaction-types/01").with(ADMIN))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Error accessing: TRANSACTION_TYPE table. SQLCODE:"));
    }
}
