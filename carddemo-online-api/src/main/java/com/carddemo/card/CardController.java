package com.carddemo.card;

import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cards")
public class CardController {
    private final CardService cards;
    public CardController(CardService cards) { this.cards = cards; }

    @GetMapping
    public CardService.Page list(@RequestParam(defaultValue = "") String accountId,
                                @RequestParam(defaultValue = "") String cardNumber,
                                @RequestParam(defaultValue = "") String cursor,
                                @RequestParam(defaultValue = "") String firstKey,
                                @RequestParam(defaultValue = "ENTER") String key,
                                @RequestParam(defaultValue = "1") int pageNumber,
                                @RequestParam(defaultValue = "false") boolean lastPageShown) {
        return cards.list(accountId, cardNumber, cursor, firstKey, key, pageNumber, lastPageShown);
    }

    @GetMapping("/view")
    public CardService.Detail view(@RequestParam(defaultValue = "") String accountId,
                                   @RequestParam(defaultValue = "") String cardNumber) {
        return cards.view(accountId, cardNumber);
    }

    public record Selections(List<CardService.Selection> selections) { }

    @PostMapping("/select")
    public Map<String, String> select(@RequestBody Selections request) { return cards.select(request.selections()); }

    @ExceptionHandler(CardService.CardError.class)
    ResponseEntity<?> invalid(CardService.CardError error) {
        return ResponseEntity.status(error.status()).body(Map.of("message", error.getMessage(), "severity", "error"));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> databaseError(DataAccessException error) {
        return ResponseEntity.internalServerError().body(Map.of("message", "Error reading Card Data File", "severity", "error"));
    }
}
