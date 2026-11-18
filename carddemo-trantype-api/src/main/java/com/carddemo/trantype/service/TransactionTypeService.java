package com.carddemo.trantype.service;

import static com.carddemo.trantype.service.SourceMessages.*;

import com.carddemo.trantype.domain.TransactionType;
import com.carddemo.trantype.repository.TransactionTypeRepository;
import com.carddemo.trantype.repository.TypeFilters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionTypeService {

    public static final int DEFAULT_PAGE_SIZE = 7;
    private static final String START_KEY = "";

    private final TransactionTypeRepository repository;

    public TransactionTypeService(TransactionTypeRepository repository) {
        this.repository = repository;
    }

    /** CTLI 8000-READ-FORWARD / 8100-READ-BACKWARDS with 9100-CHECK-FILTERS. */
    @Transactional(readOnly = true)
    public TransactionTypePage page(String cursor, PageDirection direction, int size, int page,
                                    String typeFilter, String descFilter) {
        TypeFilters filters = new TypeFilters(TranTypeRules.typeFilter(typeFilter),
                TranTypeRules.descFilter(descFilter));
        boolean firstRequest = cursor == null || cursor.isEmpty();
        if (firstRequest && filters.active() && repository.countMatching(filters) == 0) {
            return empty(NO_RECORDS_FOR_FILTERS);
        }

        List<TransactionType> rows;
        String nextCursor;
        if (direction == PageDirection.BACKWARD && !firstRequest) {
            rows = new ArrayList<>(repository.findBackward(cursor, filters, size));
            Collections.reverse(rows);
            nextCursor = rows.isEmpty()
                    ? repository.findForward(cursor, filters, 1).stream().map(TransactionType::getTrType)
                            .findFirst().orElse(null)
                    : repository.firstKeyAfter(rows.get(rows.size() - 1).getTrType(), filters).orElse(null);
        } else {
            List<TransactionType> fetched = repository.findForward(firstRequest ? START_KEY : cursor, filters,
                    size + 1);
            rows = fetched.size() > size ? fetched.subList(0, size) : fetched;
            nextCursor = fetched.size() > size ? fetched.get(size).getTrType() : null;
        }

        if (rows.isEmpty()) {
            return empty(firstRequest ? NO_RECORDS_FOUND : NO_MORE_RECORDS);
        }
        String firstKey = rows.get(0).getTrType();
        boolean hasPrev = repository.existsBefore(firstKey, filters);
        return new TransactionTypePage(
                rows.stream().map(TransactionTypeDto::of).toList(),
                firstKey,
                rows.get(rows.size() - 1).getTrType(),
                nextCursor,
                nextCursor != null,
                hasPrev,
                hasPrev ? Math.max(page, 2) : 1,
                null);
    }

    /** CTTU 9100-GET-TRANSACTION-TYPE. */
    @Transactional(readOnly = true)
    public TransactionTypeDto get(String rawType) {
        String trType = TranTypeRules.normalizeTypeCode(rawType);
        return repository.findById(trType).map(TransactionTypeDto::of)
                .orElseThrow(() -> ApiException.notFound(RECORD_NOT_FOUND));
    }

    /** Explicit add (CTTU 9700-INSERT-RECORD). Duplicate key → DataIntegrityViolationException (409). */
    @Transactional
    public TransactionTypeDto create(String rawType, String rawDescription) {
        String trType = TranTypeRules.normalizeTypeCode(rawType);
        String description = TranTypeRules.normalizeDescription(rawDescription);
        TransactionType entity = new TransactionType(trType, description);
        repository.insert(entity);
        return TransactionTypeDto.of(entity);
    }

    /** CTTU 9600-WRITE-PROCESSING: UPDATE, and INSERT when the row does not exist (+100). */
    @Transactional
    public SaveResult save(String rawType, String rawDescription) {
        String trType = TranTypeRules.normalizeTypeCode(rawType);
        String description = TranTypeRules.normalizeDescription(rawDescription);
        try {
            var existing = repository.findById(trType);
            if (existing.isEmpty()) {
                TransactionType entity = new TransactionType(trType, description);
                repository.insert(entity);
                return new SaveResult(TransactionTypeDto.of(entity), true);
            }
            TransactionType entity = existing.get();
            if (TranTypeRules.sameDescription(entity.getTrDescription(), description)) {
                throw ApiException.noChange(NO_CHANGES_DETECTED_FETCHED);
            }
            entity.setTrDescription(description);
            repository.flush();
            return new SaveResult(TransactionTypeDto.of(entity), false);
        } catch (PessimisticLockingFailureException e) {
            throw ApiException.lockError(COULD_NOT_LOCK);
        }
    }

    /** CTLI 9200-UPDATE-RECORD: strict description update of the highlighted row. */
    @Transactional
    public TransactionTypeDto updateDescription(String rawType, String rawDescription) {
        String trType = TranTypeRules.normalizeTypeCode(rawType);
        String description = TranTypeRules.normalizeDescription(rawDescription);
        try {
            TransactionType entity = repository.findById(trType)
                    .orElseThrow(() -> ApiException.notFound(RECORD_DELETED_BY_OTHERS));
            if (TranTypeRules.sameDescription(entity.getTrDescription(), description)) {
                throw ApiException.noChange(NO_CHANGES_DETECTED_DB);
            }
            entity.setTrDescription(description);
            repository.flush();
            return TransactionTypeDto.of(entity);
        } catch (PessimisticLockingFailureException e) {
            throw ApiException.lockError(DEADLOCK);
        }
    }

    /** CTLI 9300-DELETE-RECORD / CTTU 9800-DELETE-PROCESSING. Child rows → DataIntegrityViolationException (409). */
    @Transactional
    public void delete(String rawType) {
        String trType = TranTypeRules.normalizeTypeCode(rawType);
        TransactionType entity = repository.findById(trType)
                .orElseThrow(() -> ApiException.notFound(DELETE_FAILED + "SQLCODE :+100"));
        repository.delete(entity);
        repository.flush();
    }

    private static TransactionTypePage empty(String message) {
        return new TransactionTypePage(List.of(), null, null, null, false, false, 1, message);
    }
}
