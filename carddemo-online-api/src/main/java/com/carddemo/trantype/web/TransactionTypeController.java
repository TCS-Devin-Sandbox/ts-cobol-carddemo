package com.carddemo.trantype.web;

import com.carddemo.trantype.service.PageDirection;
import com.carddemo.trantype.service.SaveResult;
import com.carddemo.trantype.service.TransactionTypeDto;
import com.carddemo.trantype.service.TransactionTypePage;
import com.carddemo.trantype.service.TransactionTypeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/transaction-types")
public class TransactionTypeController {

    private final TransactionTypeService service;

    public TransactionTypeController(TransactionTypeService service) {
        this.service = service;
    }

    @GetMapping
    public TransactionTypePage list(@RequestParam(required = false) String cursor,
                                    @RequestParam(defaultValue = "FORWARD") PageDirection direction,
                                    @RequestParam(defaultValue = "7") @Min(1) @Max(50) int size,
                                    @RequestParam(defaultValue = "1") @Min(1) int page,
                                    @RequestParam(required = false) String typeFilter,
                                    @RequestParam(required = false) String descFilter) {
        return service.page(cursor, direction, size, page, typeFilter, descFilter);
    }

    @GetMapping("/{trType}")
    public TransactionTypeDto get(@PathVariable String trType) {
        return service.get(trType);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionTypeDto create(@Valid @RequestBody TransactionTypeCreateRequest request) {
        return service.create(request.trType(), request.trDescription());
    }

    @PutMapping("/{trType}")
    public ResponseEntity<TransactionTypeDto> save(@PathVariable String trType,
                                                   @Valid @RequestBody TransactionTypeUpdateRequest request) {
        SaveResult result = service.save(trType, request.trDescription());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.transactionType());
    }

    @PatchMapping("/{trType}")
    public TransactionTypeDto updateDescription(@PathVariable String trType,
                                                @Valid @RequestBody TransactionTypeUpdateRequest request) {
        return service.updateDescription(trType, request.trDescription());
    }

    @DeleteMapping("/{trType}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String trType) {
        service.delete(trType);
    }
}
