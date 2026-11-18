package com.carddemo.trantype.service;

import com.carddemo.trantype.domain.TransactionType;
import com.carddemo.trantype.repository.TransactionTypeRepository;
import com.carddemo.trantype.validation.TranTypeCodes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Business logic of COTRTLIC (list/update/delete) and COTRTUPC (get/upsert/delete). */
@Service
public class TransactionTypeService {

    /** WS-MAX-SCREEN-LINES. */
    public static final int PAGE_SIZE = 7;

    private static final Pattern TWO_DIGITS = Pattern.compile("\\d{2}");

    private final TransactionTypeRepository repository;

    public TransactionTypeService(TransactionTypeRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public TransactionTypePage list(String cursor, PageDirection direction, int size, int page,
                                    String typeFilter, String descFilter) {
        if (size < 1 || size > PAGE_SIZE) {
            throw ApiException.validation("size must be between 1 and " + PAGE_SIZE);
        }
        Filters filters = Filters.of(typeFilter, descFilter);
        int pageNumber = Math.max(page, 1);
        if (direction == PageDirection.BACKWARD && cursor != null && !cursor.isEmpty()) {
            return readBackward(cursor, size, pageNumber, filters);
        }
        return readForward(cursor == null ? "" : cursor, size, pageNumber, filters);
    }

    /** 8000-READ-FORWARD: fetch size rows plus one look-ahead row for CA-NEXT-PAGE-EXISTS. */
    private TransactionTypePage readForward(String startKey, int size, int pageNumber, Filters f) {
        List<TransactionType> fetched = repository.findForward(startKey, f.typeFlag(), f.type(),
                f.descFlag(), f.descLike(), Limit.of(size + 1));
        boolean hasNext = fetched.size() > size;
        String nextCursor = hasNext ? fetched.get(size).getTrType() : null;
        List<TransactionType> rows = hasNext ? fetched.subList(0, size) : fetched;
        return toPage(rows, nextCursor, hasNext, pageNumber, f);
    }

    /** 8100-READ-BACKWARDS: TR_TYPE &lt; first key ORDER BY TR_TYPE DESC, displayed ascending. */
    private TransactionTypePage readBackward(String firstKey, int size, int pageNumber, Filters f) {
        List<TransactionType> fetched = new ArrayList<>(repository.findBackward(firstKey, f.typeFlag(), f.type(),
                f.descFlag(), f.descLike(), Limit.of(size)));
        if (fetched.size() < size) {
            return readForward("", size, 1, f);
        }
        Collections.reverse(fetched);
        String lastKey = fetched.get(fetched.size() - 1).getTrType();
        List<TransactionType> lookAhead = repository.findForward(lastKey, f.typeFlag(), f.type(),
                f.descFlag(), f.descLike(), Limit.of(2));
        boolean hasNext = lookAhead.size() > 1;
        String nextCursor = hasNext ? lookAhead.get(1).getTrType() : null;
        return toPage(fetched, nextCursor, hasNext, pageNumber, f);
    }

    private TransactionTypePage toPage(List<TransactionType> rows, String nextCursor, boolean hasNext,
                                       int pageNumber, Filters f) {
        if (rows.isEmpty()) {
            String message = f.active() ? SourceMessages.NO_RECORDS_FILTER : SourceMessages.NO_RECORDS_SEARCH;
            return new TransactionTypePage(List.of(), null, null, null, false, false, 1, message);
        }
        String firstKey = rows.get(0).getTrType();
        String lastKey = rows.get(rows.size() - 1).getTrType();
        boolean hasPrev = !repository.findBackward(firstKey, f.typeFlag(), f.type(),
                f.descFlag(), f.descLike(), Limit.of(1)).isEmpty();
        return new TransactionTypePage(rows.stream().map(TransactionTypeDto::from).toList(),
                firstKey, lastKey, nextCursor, hasNext, hasPrev, pageNumber, null);
    }

    /** 9100-GET-TRANSACTION-TYPE. */
    @Transactional(readOnly = true)
    public TransactionTypeDto get(String trType) {
        String key = TranTypeCodes.normalise(trType);
        return repository.findById(key)
                .map(TransactionTypeDto::from)
                .orElseThrow(() -> ApiException.notFound(SourceMessages.NOT_FOUND_KEY));
    }

    /** 9700-INSERT-RECORD. */
    @Transactional
    public TransactionTypeDto create(String trType, String description) {
        String key = TranTypeCodes.normalise(trType);
        String desc = description.trim();
        repository.insert(key, desc);
        return new TransactionTypeDto(key, desc);
    }

    /**
     * upsert = true: 9600-WRITE-PROCESSING (UPDATE, INSERT on SQLCODE +100).
     * upsert = false: 9200-UPDATE-RECORD (UPDATE only, +100 = 'Record not found. Deleted by others ? ').
     */
    @Transactional
    public SaveResult save(String trType, String description, boolean upsert) {
        String key = TranTypeCodes.normalise(trType);
        String desc = description.trim();
        if (repository.updateDescription(key, desc) > 0) {
            return new SaveResult(new TransactionTypeDto(key, desc), false);
        }
        if (!upsert) {
            throw ApiException.notFound(SourceMessages.UPDATE_NOT_FOUND);
        }
        repository.insert(key, desc);
        return new SaveResult(new TransactionTypeDto(key, desc), true);
    }

    /** 9300-DELETE-RECORD / 9800-DELETE-PROCESSING. */
    @Transactional
    public void delete(String trType) {
        String key = TranTypeCodes.normalise(trType);
        if (repository.deleteByTrType(key) == 0) {
            throw ApiException.notFound(SourceMessages.DELETE_FAILED);
        }
    }

    /** 1220-EDIT-TYPECD / 1230-EDIT-DESC. */
    record Filters(boolean typeFlag, String type, boolean descFlag, String descLike) {

        static Filters of(String typeFilter, String descFilter) {
            boolean typeFlag = false;
            String type = "";
            if (typeFilter != null && !typeFilter.isBlank() && !typeFilter.equals("00")) {
                if (!TWO_DIGITS.matcher(typeFilter).matches()) {
                    throw ApiException.validation(SourceMessages.TYPE_FILTER_INVALID);
                }
                typeFlag = true;
                type = typeFilter;
            }
            boolean descFlag = descFilter != null && !descFilter.isBlank();
            String descLike = descFlag ? "%" + descFilter.trim() + "%" : "";
            return new Filters(typeFlag, type, descFlag, descLike);
        }

        boolean active() {
            return typeFlag || descFlag;
        }
    }
}
