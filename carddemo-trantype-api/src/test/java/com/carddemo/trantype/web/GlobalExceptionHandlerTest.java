package com.carddemo.trantype.web;

import com.carddemo.trantype.Messages;
import com.carddemo.trantype.web.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static DataIntegrityViolationException violation(String sqlState) {
        return new DataIntegrityViolationException("x",
                new RuntimeException("wrapper", new SQLException("pg", sqlState)));
    }

    @Test
    void foreignKeyViolationMapsToChildRecordsMessage() { // REQ-CTLI-018, REQ-CTTU-019
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrity(violation("23503"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("FK_RESTRICT", Messages.DELETE_CHILDREN));
        assertThat(response.getBody().message()).startsWith("Please delete associated child records first:");
    }

    @Test
    void uniqueViolationMapsToInsertMessage() { // REQ-CTTU-014
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrity(violation("23505"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("DUPLICATE_KEY", Messages.INSERT_DUPLICATE));
        assertThat(response.getBody().message())
                .startsWith("Error inserting record into: TRANSACTION_TYPE Table. SQLCODE:");
    }

    @Test
    void otherIntegrityViolation() {
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrity(violation("23502"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("DB_ERROR");
    }

    @Test
    void lockFallback() {
        ResponseEntity<ErrorResponse> response = handler.handleLock(new CannotAcquireLockException("l"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo(Messages.UPDATE_DEADLOCK);
    }

    @Test
    void apiExceptionPassesThrough() {
        ResponseEntity<ErrorResponse> response = handler.handleApi(
                new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, Messages.RECORD_NOT_FOUND));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("NOT_FOUND", Messages.RECORD_NOT_FOUND));
    }
}
