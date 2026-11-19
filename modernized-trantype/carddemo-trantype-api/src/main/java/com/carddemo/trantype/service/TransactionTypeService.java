package com.carddemo.trantype.service;

import com.carddemo.trantype.Messages;
import com.carddemo.trantype.domain.TransactionType;
import com.carddemo.trantype.repository.TransactionTypeRepository;
import com.carddemo.trantype.validation.FieldEditException;
import com.carddemo.trantype.validation.TransactionTypeEdits;
import com.carddemo.trantype.web.ApiException;
import com.carddemo.trantype.web.ErrorCode;
import com.carddemo.trantype.web.dto.TransactionTypeDto;
import com.carddemo.trantype.web.dto.TransactionTypePage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/** Business logic of COTRTLIC (list/update/delete) and COTRTUPC (read/add/edit/delete). */
@Service
public class TransactionTypeService {

    public static final int DEFAULT_PAGE_SIZE = 7;
    public static final int MAX_PAGE_SIZE = 50;
    private static final Pattern CURSOR = Pattern.compile("\\d{2}");
    private static final Logger log = LoggerFactory.getLogger(TransactionTypeService.class);

    private final TransactionTypeRepository repository;

    public TransactionTypeService(TransactionTypeRepository repository) {
        this.repository = repository;
    }

    public enum Direction { NEXT, PREV }

    // ---------------------------------------------------------------- list (8000 / 8100 / 9100)

    @Transactional(readOnly = true)
    public TransactionTypePage list(String cursor, String direction, Integer size,
                                    String typeFilter, String descFilter) {
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new FieldEditException(Messages.PAGE_SIZE_INVALID);
        }
        Direction dir = parseDirection(direction);
        String key = blankToNull(cursor);
        if (key != null && !CURSOR.matcher(key).matches()) {
            throw new FieldEditException(Messages.CURSOR_INVALID);
        }
        String type = TransactionTypeEdits.parseTypeFilter(typeFilter).orElse(null);
        String desc = TransactionTypeEdits.parseDescriptionFilter(descFilter).orElse(null);

        try {
            if ((type != null || desc != null) && repository.countMatching(type, desc) == 0) {
                return emptyPage(pageSize, Messages.NO_RECORDS_FOR_FILTERS);
            }
            if (dir == Direction.PREV) {
                return readBackward(key, pageSize, type, desc);
            }
            return readForward(key, pageSize, type, desc, null);
        } catch (DataAccessException e) {
            log.error("List failed", e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.DB_ERROR, Messages.LIST_FAILED, e);
        }
    }

    private TransactionTypePage readForward(String cursor, int size, String type, String desc, String message) {
        List<TransactionType> fetched = repository.findPageForward(cursor, type, desc, Limit.of(size + 1));
        boolean hasNext = fetched.size() > size;
        String nextCursor = hasNext ? fetched.get(size).getTrType() : null;
        List<TransactionType> rows = hasNext ? fetched.subList(0, size) : fetched;
        if (rows.isEmpty()) {
            return emptyPage(size, message != null ? message
                    : cursor == null ? Messages.NO_RECORDS_FOUND : Messages.NO_MORE_PAGES);
        }
        return toPage(rows, nextCursor, hasNext, size, type, desc, message);
    }

    private TransactionTypePage readBackward(String cursor, int size, String type, String desc) {
        if (cursor == null) {
            return readForward(null, size, type, desc, Messages.NO_PREVIOUS_PAGES);
        }
        List<TransactionType> fetched = repository.findPageBackward(cursor, type, desc, Limit.of(size));
        if (fetched.isEmpty()) {
            return readForward(null, size, type, desc, Messages.NO_PREVIOUS_PAGES);
        }
        if (fetched.size() < size) {
            // Source 8100 fails with SQLCODE +100 here (rows deleted since paging forward); show page 1 instead.
            return readForward(null, size, type, desc, null);
        }
        List<TransactionType> rows = new ArrayList<>(fetched);
        Collections.reverse(rows);
        return toPage(rows, cursor, true, size, type, desc, null);
    }

    private TransactionTypePage toPage(List<TransactionType> rows, String nextCursor, boolean hasNext, int size,
                                       String type, String desc, String errorMessage) {
        String firstKey = rows.get(0).getTrType();
        String lastKey = rows.get(rows.size() - 1).getTrType();
        long before = repository.countBefore(firstKey, type, desc);
        int pageNumber = (int) (before / size) + 1;
        List<TransactionTypeDto> dtos = rows.stream().map(TransactionTypeDto::from).toList();
        return new TransactionTypePage(dtos, firstKey, lastKey, nextCursor, hasNext, before > 0,
                pageNumber, size, Messages.INFO_REC_ACTIONS, errorMessage);
    }

    private static TransactionTypePage emptyPage(int size, String errorMessage) {
        return new TransactionTypePage(List.of(), null, null, null, false, false, 1, size, null, errorMessage);
    }

    private static Direction parseDirection(String direction) {
        if (direction == null || direction.isBlank() || "next".equalsIgnoreCase(direction.trim())) {
            return Direction.NEXT;
        }
        if ("prev".equalsIgnoreCase(direction.trim())) {
            return Direction.PREV;
        }
        throw new FieldEditException(Messages.DIRECTION_INVALID);
    }

    // ---------------------------------------------------------------- read one (CTTU 9100)

    @Transactional(readOnly = true)
    public TransactionTypeDto get(String trType) {
        String key = TransactionTypeEdits.normalizeTypeCode(trType);
        try {
            return repository.findById(key)
                    .map(TransactionTypeDto::from)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND,
                            Messages.RECORD_NOT_FOUND));
        } catch (DataAccessException e) {
            log.error("Read failed for {}", key, e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.DB_ERROR, Messages.READ_FAILED, e);
        }
    }

    // ---------------------------------------------------------------- insert (CTTU 9700)

    @Transactional
    public TransactionTypeDto create(String trType, String trDescription) {
        String key = TransactionTypeEdits.normalizeTypeCode(trType);
        String desc = TransactionTypeEdits.normalizeDescription(trDescription);
        try {
            repository.insert(key, desc);
            return new TransactionTypeDto(key, desc);
        } catch (DataIntegrityViolationException e) {
            throw e; // duplicate key -> GlobalExceptionHandler (409 DUPLICATE_KEY)
        } catch (DataAccessException e) {
            log.error("Insert failed for {}", key, e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.DB_ERROR, Messages.INSERT_FAILED, e);
        }
    }

    // ---------------------------------------------------------------- update (CTLI 9200 / CTTU 9600)

    /**
     * Updates the description. With {@code upsert} (CTTU 9600-WRITE-PROCESSING) a missing row is inserted
     * (9700-INSERT-RECORD); without it (CTLI 9200-UPDATE-RECORD) a missing row is "not found".
     */
    @Transactional
    public SaveResult update(String trType, String trDescription, boolean upsert) {
        String key = TransactionTypeEdits.normalizeTypeCode(trType);
        String desc = TransactionTypeEdits.normalizeDescription(trDescription);
        try {
            repository.findById(key).ifPresent(current -> {
                if (TransactionTypeEdits.isSameDescription(current.getTrDescription(), desc)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.NO_CHANGE,
                            upsert ? Messages.NO_CHANGE_FETCHED : Messages.NO_CHANGE_DATABASE);
                }
            });
            int updated = repository.updateDescription(key, desc);
            if (updated > 0) {
                return new SaveResult(new TransactionTypeDto(key, desc), false);
            }
            if (!upsert) {
                throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, Messages.UPDATE_NOT_FOUND);
            }
            repository.insert(key, desc);
            return new SaveResult(new TransactionTypeDto(key, desc), true);
        } catch (PessimisticLockingFailureException e) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.LOCK_CONFLICT,
                    upsert ? Messages.COULD_NOT_LOCK : Messages.UPDATE_DEADLOCK, e);
        } catch (DataIntegrityViolationException e) {
            throw e;
        } catch (DataAccessException e) {
            log.error("Update failed for {}", key, e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.DB_ERROR,
                    upsert ? Messages.CTTU_UPDATE_FAILED : Messages.UPDATE_FAILED, e);
        }
    }

    // ---------------------------------------------------------------- delete (CTLI 9300 / CTTU 9800)

    @Transactional
    public void delete(String trType) {
        String key = TransactionTypeEdits.normalizeTypeCode(trType);
        int deleted;
        try {
            deleted = repository.deleteByTrType(key);
        } catch (DataIntegrityViolationException e) {
            throw e; // FK RESTRICT -> GlobalExceptionHandler (409 FK_RESTRICT)
        } catch (PessimisticLockingFailureException e) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.LOCK_CONFLICT, Messages.DELETE_FAILED, e);
        } catch (DataAccessException e) {
            log.error("Delete failed for {}", key, e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.DB_ERROR, Messages.DELETE_FAILED, e);
        }
        if (deleted == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, Messages.DELETE_NOT_FOUND);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
