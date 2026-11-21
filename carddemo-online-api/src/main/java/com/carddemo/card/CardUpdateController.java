package com.carddemo.card;

import java.security.Principal;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cards/update")
public class CardUpdateController {
    private final CardUpdateService service;
    public CardUpdateController(CardUpdateService service) { this.service = service; }
    @GetMapping
    public CardUpdateService.Screen fetch(Principal user, @RequestParam(defaultValue = "") String accountId,
                                          @RequestParam(defaultValue = "") String cardNumber) {
        return service.fetch(user.getName(), accountId, cardNumber);
    }
    public record Action(String token, CardUpdateService.Fields fields, String key) { }
    @PostMapping
    public CardUpdateService.Screen act(Principal user, @RequestBody Action action) {
        return service.act(user.getName(), action.token(), action.fields(), action.key());
    }
    @ExceptionHandler(CardService.CardError.class)
    ResponseEntity<?> invalid(CardService.CardError error) {
        return ResponseEntity.status(error.status()).body(Map.of("message", error.getMessage()));
    }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> database(DataAccessException error) {
        return ResponseEntity.internalServerError().body(Map.of("message", "Update of record failed"));
    }
}
