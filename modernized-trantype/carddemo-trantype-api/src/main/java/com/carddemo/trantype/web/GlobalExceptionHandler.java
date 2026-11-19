package com.carddemo.trantype.web;

import com.carddemo.trantype.Messages;
import com.carddemo.trantype.validation.FieldEditException;
import com.carddemo.trantype.web.dto.ErrorResponse;
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
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;

/** Maps exceptions to {code, message} with the COBOL message literals (docs/target-spec.md §3.3). */
@RestControllerAdvice
public class GlobalExceptionHandler {

    static final String SQLSTATE_FOREIGN_KEY_VIOLATION = "23503";
    static final String SQLSTATE_UNIQUE_VIOLATION = "23505";
    /** CTTU edits the key before the description. */
    private static final List<String> FIELD_ORDER = List.of("trType", "trDescription");
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApi(ApiException e) {
        return respond(e.getStatus(), e.getCode(), e.getMessage());
    }

    @ExceptionHandler(FieldEditException.class)
    ResponseEntity<ErrorResponse> handleFieldEdit(FieldEditException e) {
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleBeanValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .min(Comparator.comparingInt(GlobalExceptionHandler::fieldRank))
                .map(FieldError::getDefaultMessage)
                .orElse(Messages.MALFORMED_REQUEST);
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    ResponseEntity<ErrorResponse> handleMalformed(Exception e) {
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, Messages.MALFORMED_REQUEST);
    }

    /** SQLCODE -532 (FK RESTRICT on delete) and -803 (duplicate key on insert). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException e) {
        String sqlState = sqlState(e);
        if (SQLSTATE_FOREIGN_KEY_VIOLATION.equals(sqlState)) {
            return respond(HttpStatus.CONFLICT, ErrorCode.FK_RESTRICT, Messages.DELETE_CHILDREN);
        }
        if (SQLSTATE_UNIQUE_VIOLATION.equals(sqlState)) {
            return respond(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_KEY, Messages.INSERT_DUPLICATE);
        }
        log.error("Data integrity violation (SQLSTATE {})", sqlState, e);
        return respond(HttpStatus.CONFLICT, ErrorCode.DB_ERROR, Messages.DATA_INTEGRITY);
    }

    /** SQLCODE -911 fallback when the service did not translate it. */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    ResponseEntity<ErrorResponse> handleLock(PessimisticLockingFailureException e) {
        return respond(HttpStatus.CONFLICT, ErrorCode.LOCK_CONFLICT, Messages.UPDATE_DEADLOCK);
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ErrorResponse> handleDataAccess(DataAccessException e) {
        log.error("Database error", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.DB_ERROR, Messages.DB_ACCESS_FAILURE);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleOther(Exception e) {
        log.error("Unexpected error", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.DB_ERROR, Messages.UNEXPECTED);
    }

    static String sqlState(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }

    private static int fieldRank(FieldError error) {
        int index = FIELD_ORDER.indexOf(error.getField());
        return index < 0 ? FIELD_ORDER.size() : index;
    }

    private static ResponseEntity<ErrorResponse> respond(HttpStatus status, ErrorCode code, String message) {
        return ResponseEntity.status(status).body(ErrorResponse.of(code, message));
    }
}
