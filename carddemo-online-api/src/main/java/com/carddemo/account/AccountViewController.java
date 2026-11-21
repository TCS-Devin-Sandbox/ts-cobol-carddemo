package com.carddemo.account;

import com.carddemo.account.AccountViewService.Screen;
import com.carddemo.trantype.web.ErrorResponse;
import java.util.regex.Pattern;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountViewController {
    private static final Pattern EMPTY_INPUT = Pattern.compile("\\*? *");
    private static final Pattern ACCOUNT_ID = Pattern.compile("[0-9]{11}");
    private final AccountViewService accounts;

    AccountViewController(AccountViewService accounts) {
        this.accounts = accounts;
    }

    public record Lookup(String accountId) { }

    @PostMapping("/api/accounts/view")
    public ResponseEntity<Screen> view(@RequestBody Lookup lookup) {
        String id = lookup.accountId();
        Screen screen;
        if (id == null || EMPTY_INPUT.matcher(id).matches()) {
            screen = Screen.invalidAccount("NO_INPUT", "No input received");
        } else if (!ACCOUNT_ID.matcher(id).matches() || id.equals("00000000000")) {
            screen = Screen.invalidAccount("INVALID_ACCOUNT_ID", "Account Filter must  be a non-zero 11 digit number");
        } else {
            screen = accounts.lookup(id);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(screen);
    }

    @ExceptionHandler({DataAccessException.class, CannotCreateTransactionException.class})
    ResponseEntity<ErrorResponse> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(CacheControl.noStore())
                .body(new ErrorResponse("ACCOUNT_VIEW_UNAVAILABLE", "Unable to look up account"));
    }
}
