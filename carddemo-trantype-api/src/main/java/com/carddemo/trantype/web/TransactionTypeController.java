package com.carddemo.trantype.web;

import com.carddemo.trantype.service.PageDirection;
import com.carddemo.trantype.service.SaveResult;
import com.carddemo.trantype.service.TransactionTypeDto;
import com.carddemo.trantype.service.TransactionTypePage;
import com.carddemo.trantype.service.TransactionTypeService;
import com.carddemo.trantype.validation.TranTypeCode;
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

@RestController
@RequestMapping("/api/transaction-types")
public class TransactionTypeController {

    private final TransactionTypeService service;

    public TransactionTypeController(TransactionTypeService service) {
        this.service = service;
    }

    @GetMapping
    public TransactionTypePage list(@RequestParam(required = false) String cursor,
                                    @RequestParam(required = false) String direction,
                                    @RequestParam(defaultValue = "7") int size,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(required = false) String typeFilter,
                                    @RequestParam(required = false) String descFilter) {
        return service.list(cursor, PageDirection.parse(direction), size, page, typeFilter, descFilter);
    }

    @GetMapping("/{trType}")
    public TransactionTypeDto get(@PathVariable @TranTypeCode String trType) {
        return service.get(trType);
    }

    @PostMapping("/{trType}")
    public ResponseEntity<TransactionTypeDto> create(@PathVariable @TranTypeCode String trType,
                                                     @Valid @RequestBody TransactionTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(trType, request.description()));
    }

    @PutMapping("/{trType}")
    public ResponseEntity<TransactionTypeDto> save(@PathVariable @TranTypeCode String trType,
                                                   @RequestParam(defaultValue = "true") boolean upsert,
                                                   @Valid @RequestBody TransactionTypeRequest request) {
        SaveResult result = service.save(trType, request.description(), upsert);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.transactionType());
    }

    @DeleteMapping("/{trType}")
    public ResponseEntity<Void> delete(@PathVariable @TranTypeCode String trType) {
        service.delete(trType);
        return ResponseEntity.noContent().build();
    }
}
