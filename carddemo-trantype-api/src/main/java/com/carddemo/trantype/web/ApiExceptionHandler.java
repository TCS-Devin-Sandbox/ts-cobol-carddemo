package com.carddemo.trantype.web;

import com.carddemo.trantype.service.ApiException;
import com.carddemo.trantype.service.ErrorCode;
import com.carddemo.trantype.service.SourceMessages;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Maps exceptions to {code, message, sqlcode} using the COBOL SQLCODE branches. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    static final String SQLSTATE_FOREIGN_KEY = "23503";
    static final String SQLSTATE_UNIQUE = "23505";

    /** Edit order of the source: key edits (1210) before description edits (1230). First error wins. */
    private static final List<String> EDIT_ORDER = List.of(
            SourceMessages.TYPE_CODE_REQUIRED,
            SourceMessages.TYPE_CODE_NUMERIC,
            SourceMessages.TYPE_CODE_NOT_ZERO,
            SourceMessages.DESC_REQUIRED,
            SourceMessages.DESC_ALPHANUMERIC);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApi(ApiException e) {
        return respond(e.getStatus(), e.getCode(), e.getMessage(), e.getSqlcode());
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException e) {
        return validation(e.getAllErrors().stream().map(MessageSourceResolvable::getDefaultMessage));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException e) {
        return validation(e.getBindingResult().getAllErrors().stream().map(MessageSourceResolvable::getDefaultMessage));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, SourceMessages.DESC_REQUIRED, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Invalid value for " + e.getName(), null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException e) {
        String sqlState = sqlState(e);
        if (SQLSTATE_FOREIGN_KEY.equals(sqlState)) {
            return respond(HttpStatus.CONFLICT, ErrorCode.CHILD_RECORDS_EXIST, SourceMessages.CHILD_RECORDS, -532);
        }
        if (SQLSTATE_UNIQUE.equals(sqlState)) {
            return respond(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_KEY, SourceMessages.INSERT_FAILED, -803);
        }
        LOG.error("Unmapped integrity violation (SQLSTATE {})", sqlState, e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, SourceMessages.UPDATE_FAILED, -1);
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    ResponseEntity<ErrorResponse> handleLock(PessimisticLockingFailureException e) {
        return respond(HttpStatus.CONFLICT, ErrorCode.LOCK_ERROR, SourceMessages.LOCK_ERROR, -911);
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ErrorResponse> handleDataAccess(DataAccessException e) {
        LOG.error("Database error", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, SourceMessages.ACCESS_FAILED, -1);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleOther(Exception e) {
        LOG.error("Unexpected error", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, SourceMessages.UNEXPECTED_ABEND, null);
    }

    private ResponseEntity<ErrorResponse> validation(Stream<String> messages) {
        String message = messages
                .min(Comparator.comparingInt(m -> {
                    int index = EDIT_ORDER.indexOf(m);
                    return index < 0 ? EDIT_ORDER.size() : index;
                }))
                .orElse(SourceMessages.DESC_REQUIRED);
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, message, null);
    }

    private static String sqlState(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }

    private static ResponseEntity<ErrorResponse> respond(HttpStatus status, ErrorCode code, String message,
                                                         Integer sqlcode) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message, sqlcode));
    }
}
