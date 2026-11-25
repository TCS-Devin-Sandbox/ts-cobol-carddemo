package com.carddemo.authorization;

import com.carddemo.authorization.AuthorizationSummaryFlow.Screen;
import com.carddemo.authorization.AuthorizationSummaryFlow.State;
import com.carddemo.trantype.web.ErrorResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** CPVS over REST: one POST per AID key, the pseudo-conversational COMMAREA carried in {@code state}. */
@RestController
public class AuthorizationSummaryController {
    private static final Logger LOG = LoggerFactory.getLogger(AuthorizationSummaryController.class);
    private final AuthorizationSummaryFlow flow;

    AuthorizationSummaryController(AuthorizationSummaryFlow flow) {
        this.flow = flow;
    }

    public record Request(String action, String accountId, List<String> selections, State state) { }

    @PostMapping("/api/authorizations/summary")
    public ResponseEntity<?> summary(@RequestBody Request request) {
        try {
            AuthorizationSummaryFlow.validate(request.state());
            if (request.selections() != null && (request.selections().size() > AuthorizationSummaryFlow.PAGE_ROWS
                    || request.selections().stream().anyMatch(s -> s != null && s.length() > 1))) {
                throw new IllegalArgumentException("Invalid selection");
            }
        } catch (IllegalArgumentException e) {
            return invalid();
        }
        var state = request.state() == null ? State.INITIAL : request.state();
        Screen screen = switch (request.action() == null ? "" : request.action()) {
            case "ENTER" -> flow.enter(request.accountId(), request.selections(), state);
            case "PF7" -> flow.previous(state);
            case "PF8" -> flow.next(state);
            default -> null;
        };
        if (screen == null) {
            return invalid();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(screen);
    }

    private static ResponseEntity<ErrorResponse> invalid() {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                .body(new ErrorResponse("INVALID_REQUEST", "Invalid key pressed. Please see below..."));
    }

    @ExceptionHandler({DataAccessException.class, CannotCreateTransactionException.class})
    ResponseEntity<ErrorResponse> unavailable(Exception e) {
        LOG.warn("CPVS read failed", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(CacheControl.noStore())
                .body(new ErrorResponse("AUTHORIZATION_SUMMARY_UNAVAILABLE", "Unable to read pending authorizations"));
    }
}
