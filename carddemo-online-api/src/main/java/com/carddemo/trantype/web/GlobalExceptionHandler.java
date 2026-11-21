package com.carddemo.trantype.web;

import static com.carddemo.trantype.service.SourceMessages.*;

import com.carddemo.trantype.service.ApiException;
import jakarta.validation.ConstraintViolationException;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    static final String FK_VIOLATION = "23503";
    static final String UNIQUE_VIOLATION = "23505";
    private static final List<String> FIELD_ORDER = List.of("trType", "trDescription");
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> api(ApiException e) {
        return error(e.getStatus(), e.getCode(), e.getMessage());
    }

    /** First failing field wins, key before description (COBOL keeps only the first WS-RETURN-MSG). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> invalid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .min(Comparator.comparingInt((FieldError f) -> {
                    int i = FIELD_ORDER.indexOf(f.getField());
                    return i < 0 ? Integer.MAX_VALUE : i;
                }))
                .map(FieldError::getDefaultMessage)
                .orElse("Invalid request");
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    ResponseEntity<ErrorResponse> badRequest(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request parameter");
    }

    /** DB2 −532 (FK RESTRICT) and −803 (duplicate key) equivalents. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> integrity(DataIntegrityViolationException e) {
        String state = sqlState(e);
        if (FK_VIOLATION.equals(state)) {
            return error(HttpStatus.CONFLICT, "FK_RESTRICT", DELETE_CHILDREN_FIRST + "SQLCODE :-532");
        }
        if (UNIQUE_VIOLATION.equals(state)) {
            return error(HttpStatus.CONFLICT, "DUPLICATE_KEY", INSERT_FAILED + "-803");
        }
        return dataAccess(e);
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    ResponseEntity<ErrorResponse> lock(PessimisticLockingFailureException e) {
        return api(ApiException.lockError(DEADLOCK));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ErrorResponse> dataAccess(DataAccessException e) {
        log.error("Database failure", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "DB_ERROR", ACCESS_FAILED + " SQLSTATE " + sqlState(e));
    }

    static String sqlState(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }
}
