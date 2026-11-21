package com.carddemo.account;

import com.carddemo.trantype.web.ErrorResponse;
import com.carddemo.trantype.service.ApiException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountUpdateController {
    private final AccountUpdateService service;
    AccountUpdateController(AccountUpdateService service) { this.service = service; }

    @PostMapping("/api/accounts/update/read")
    ResponseEntity<AccountUpdateService.Screen> read(@RequestBody AccountViewController.Lookup lookup) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.read(lookup.accountId()));
    }

    @PostMapping("/api/accounts/update")
    ResponseEntity<AccountUpdateService.Screen> change(@RequestBody AccountUpdateService.Change change) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.change(change));
    }

    @ExceptionHandler({DataAccessException.class, CannotCreateTransactionException.class})
    ResponseEntity<ErrorResponse> unavailable() {
        return ResponseEntity.status(503).cacheControl(CacheControl.noStore())
                .body(new ErrorResponse("ACCOUNT_UPDATE_UNAVAILABLE", "Changes unsuccessful. Please try again"));
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> conflict(ApiException error) {
        return ResponseEntity.status(error.getStatus()).cacheControl(CacheControl.noStore())
                .body(new ErrorResponse(error.getCode(), error.getMessage()));
    }
}
