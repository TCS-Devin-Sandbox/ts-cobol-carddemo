package com.carddemo.trantype.web;

import static com.carddemo.trantype.service.SourceMessages.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.trantype.service.ApiException;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static DataIntegrityViolationException violation(String state) {
        return new DataIntegrityViolationException("x",
                new RuntimeException("wrapper", new SQLException("pg", state)));
    }

    @Test
    void foreignKeyViolationMapsToChildRecordsMessage() {
        var response = handler.integrity(violation("23503"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(
                new ErrorResponse("FK_RESTRICT", DELETE_CHILDREN_FIRST + "SQLCODE :-532"));
    }

    @Test
    void uniqueViolationMapsToInsertMessage() {
        var response = handler.integrity(violation("23505"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("DUPLICATE_KEY", INSERT_FAILED + "-803"));
    }

    @Test
    void otherIntegrityViolation() {
        var response = handler.integrity(violation("23502"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("DB_ERROR", ACCESS_FAILED + " SQLSTATE 23502"));
    }

    @Test
    void lockFallback() {
        var response = handler.lock(new CannotAcquireLockException("l"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("LOCK_ERROR", DEADLOCK));
    }

    @Test
    void apiExceptionPassesThrough() {
        var response = handler.api(ApiException.notFound(RECORD_NOT_FOUND));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("NOT_FOUND", RECORD_NOT_FOUND));
    }

    @Test
    void missingSqlState() {
        assertThat(GlobalExceptionHandler.sqlState(new RuntimeException())).isNull();
    }
}
