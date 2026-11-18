package com.carddemo.trantype.web;

import com.carddemo.trantype.service.SaveResult;
import com.carddemo.trantype.service.TransactionTypeService;
import com.carddemo.trantype.web.dto.TransactionTypeDto;
import com.carddemo.trantype.web.dto.TransactionTypePage;
import com.carddemo.trantype.web.dto.TransactionTypeRequest;
import com.carddemo.trantype.web.dto.TransactionTypeUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/transaction-types")
public class TransactionTypeController {

    private final TransactionTypeService service;

    public TransactionTypeController(TransactionTypeService service) {
        this.service = service;
    }

    @GetMapping
    public TransactionTypePage list(@RequestParam(required = false) String cursor,
                                    @RequestParam(required = false, defaultValue = "next") String direction,
                                    @RequestParam(required = false) Integer size,
                                    @RequestParam(required = false) String typeFilter,
                                    @RequestParam(required = false) String descFilter) {
        return service.list(cursor, direction, size, typeFilter, descFilter);
    }

    @GetMapping("/{trType}")
    public TransactionTypeDto get(@PathVariable String trType) {
        return service.get(trType);
    }

    @PostMapping
    public ResponseEntity<TransactionTypeDto> create(@Valid @RequestBody TransactionTypeRequest request) {
        TransactionTypeDto created = service.create(request.trType(), request.trDescription());
        return ResponseEntity.created(location(created.trType())).body(created);
    }

    @PutMapping("/{trType}")
    public ResponseEntity<TransactionTypeDto> update(@PathVariable String trType,
                                                     @RequestParam(defaultValue = "false") boolean upsert,
                                                     @Valid @RequestBody TransactionTypeUpdateRequest request) {
        SaveResult result = service.update(trType, request.trDescription(), upsert);
        if (result.inserted()) {
            return ResponseEntity.created(location(result.transactionType().trType()))
                    .body(result.transactionType());
        }
        return ResponseEntity.ok(result.transactionType());
    }

    @DeleteMapping("/{trType}")
    public ResponseEntity<Void> delete(@PathVariable String trType) {
        service.delete(trType);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private static URI location(String trType) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/transaction-types/{trType}")
                .buildAndExpand(trType)
                .toUri();
    }
}
