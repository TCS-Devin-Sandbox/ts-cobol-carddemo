package com.carddemo.trantype.service;

import com.carddemo.trantype.Messages;
import com.carddemo.trantype.domain.TransactionType;
import com.carddemo.trantype.repository.TransactionTypeRepository;
import com.carddemo.trantype.validation.FieldEditException;
import com.carddemo.trantype.web.ApiException;
import com.carddemo.trantype.web.ErrorCode;
import com.carddemo.trantype.web.dto.TransactionTypeDto;
import com.carddemo.trantype.web.dto.TransactionTypePage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionTypeServiceTest {

    @Mock
    TransactionTypeRepository repository;

    @InjectMocks
    TransactionTypeService service;

    private static List<TransactionType> rows(int fromInclusive, int toInclusive) {
        return IntStream.rangeClosed(fromInclusive, toInclusive)
                .mapToObj(i -> new TransactionType(String.format("%02d", i), "TYPE " + i))
                .toList();
    }

    private static List<TransactionType> descending(List<TransactionType> ascending) {
        List<TransactionType> copy = new java.util.ArrayList<>(ascending);
        java.util.Collections.reverse(copy);
        return copy;
    }

    private static ApiException apiError(Runnable call) {
        try {
            call.run();
        } catch (ApiException e) {
            return e;
        }
        throw new AssertionError("ApiException expected");
    }

    // ------------------------------------------------------------- paging

    @Test
    void firstPageUsesLookAheadForHasNext() { // REQ-CTLI-001, REQ-CTLI-002
        when(repository.findPageForward(isNull(), isNull(), isNull(), eq(Limit.of(8)))).thenReturn(rows(1, 8));
        when(repository.countBefore("01", null, null)).thenReturn(0L);

        TransactionTypePage page = service.list(null, "next", null, null, null);

        assertThat(page.rows()).extracting(TransactionTypeDto::trType)
                .containsExactly("01", "02", "03", "04", "05", "06", "07");
        assertThat(page.hasNext()).isTrue();
        assertThat(page.nextCursor()).isEqualTo("08");
        assertThat(page.firstKey()).isEqualTo("01");
        assertThat(page.lastKey()).isEqualTo("07");
        assertThat(page.hasPrev()).isFalse();
        assertThat(page.pageNumber()).isEqualTo(1);
        assertThat(page.infoMessage()).isEqualTo(Messages.INFO_REC_ACTIONS);
        assertThat(page.errorMessage()).isNull();
    }

    @Test
    void exactlySevenRowsHasNoNextPage() { // REQ-EDGE-002
        when(repository.findPageForward(isNull(), isNull(), isNull(), eq(Limit.of(8)))).thenReturn(rows(1, 7));
        when(repository.countBefore("01", null, null)).thenReturn(0L);

        TransactionTypePage page = service.list(null, null, 7, null, null);

        assertThat(page.rows()).hasSize(7);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void forwardFromCursorComputesPageNumber() { // REQ-CTLI-003, REQ-CTLI-008
        when(repository.findPageForward(eq("08"), isNull(), isNull(), eq(Limit.of(8)))).thenReturn(rows(8, 10));
        when(repository.countBefore("08", null, null)).thenReturn(7L);

        TransactionTypePage page = service.list("08", "next", 7, null, null);

        assertThat(page.rows()).extracting(TransactionTypeDto::trType).containsExactly("08", "09", "10");
        assertThat(page.pageNumber()).isEqualTo(2);
        assertThat(page.hasPrev()).isTrue();
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    void backwardReadsDescendingAndReverses() { // REQ-CTLI-004
        List<TransactionType> desc = descending(rows(1, 7));
        when(repository.findPageBackward(eq("08"), isNull(), isNull(), eq(Limit.of(7)))).thenReturn(desc);
        when(repository.countBefore("01", null, null)).thenReturn(0L);

        TransactionTypePage page = service.list("08", "prev", 7, null, null);

        assertThat(page.rows()).extracting(TransactionTypeDto::trType)
                .containsExactly("01", "02", "03", "04", "05", "06", "07");
        assertThat(page.hasNext()).isTrue();
        assertThat(page.nextCursor()).isEqualTo("08");
        assertThat(page.pageNumber()).isEqualTo(1);
    }

    @Test
    void backwardWithNothingBeforeReturnsFirstPageWithMessage() { // REQ-CTLI-005
        when(repository.findPageBackward(eq("01"), isNull(), isNull(), any())).thenReturn(List.of());
        when(repository.findPageForward(isNull(), isNull(), isNull(), eq(Limit.of(8)))).thenReturn(rows(1, 3));
        when(repository.countBefore("01", null, null)).thenReturn(0L);

        TransactionTypePage page = service.list("01", "prev", 7, null, null);

        assertThat(page.errorMessage()).isEqualTo(Messages.NO_PREVIOUS_PAGES);
        assertThat(page.rows()).hasSize(3);
    }

    @Test
    void shortBackwardPageFallsBackToFirstPage() { // REQ-EDGE-004
        when(repository.findPageBackward(eq("10"), isNull(), isNull(), any())).thenReturn(descending(rows(1, 3)));
        when(repository.findPageForward(isNull(), isNull(), isNull(), eq(Limit.of(8)))).thenReturn(rows(1, 5));
        when(repository.countBefore("01", null, null)).thenReturn(0L);

        TransactionTypePage page = service.list("10", "prev", 7, null, null);

        assertThat(page.pageNumber()).isEqualTo(1);
        assertThat(page.rows()).hasSize(5);
        assertThat(page.errorMessage()).isNull();
    }

    @Test
    void emptyTable() { // REQ-CTLI-007, REQ-EDGE-001
        when(repository.findPageForward(isNull(), isNull(), isNull(), any())).thenReturn(List.of());

        TransactionTypePage page = service.list(null, "next", 7, null, null);

        assertThat(page.rows()).isEmpty();
        assertThat(page.hasNext()).isFalse();
        assertThat(page.hasPrev()).isFalse();
        assertThat(page.pageNumber()).isEqualTo(1);
        assertThat(page.errorMessage()).isEqualTo(Messages.NO_RECORDS_FOUND);
    }

    @Test
    void filtersWithNoMatches() { // REQ-CTLI-012
        when(repository.countMatching("09", "%X%")).thenReturn(0L);

        TransactionTypePage page = service.list(null, "next", 7, "09", "X");

        assertThat(page.rows()).isEmpty();
        assertThat(page.errorMessage()).isEqualTo(Messages.NO_RECORDS_FOR_FILTERS);
        verify(repository, never()).findPageForward(any(), any(), any(), any());
    }

    @Test
    void filtersArePassedToRepository() { // REQ-CTLI-009, REQ-CTLI-011
        when(repository.countMatching("02", "%PAY%")).thenReturn(1L);
        when(repository.findPageForward(isNull(), eq("02"), eq("%PAY%"), any()))
                .thenReturn(List.of(new TransactionType("02", "PAYMENT")));
        when(repository.countBefore("02", "02", "%PAY%")).thenReturn(0L);

        TransactionTypePage page = service.list(null, "next", 7, "02", "PAY");

        assertThat(page.rows()).containsExactly(new TransactionTypeDto("02", "PAYMENT"));
    }

    @Test
    void invalidListParameters() { // REQ-CTLI-010
        assertThatThrownBy(() -> service.list(null, "next", 7, "1", null)).hasMessage(Messages.TYPE_FILTER_INVALID);
        assertThatThrownBy(() -> service.list(null, "next", 0, null, null)).hasMessage(Messages.PAGE_SIZE_INVALID);
        assertThatThrownBy(() -> service.list(null, "sideways", 7, null, null))
                .hasMessage(Messages.DIRECTION_INVALID);
        assertThatThrownBy(() -> service.list("1", "next", 7, null, null))
                .isInstanceOf(FieldEditException.class).hasMessage(Messages.CURSOR_INVALID);
    }

    @Test
    void listDbErrorIsReported() { // REQ-CTLI-032
        when(repository.findPageForward(any(), any(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("down"));

        ApiException e = apiError(() -> service.list(null, "next", 7, null, null));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(e.getMessage()).isEqualTo(Messages.LIST_FAILED);
    }

    // ------------------------------------------------------------- read

    @Test
    void getNormalizesKey() { // REQ-CTTU-005, REQ-CTTU-006
        when(repository.findById("01")).thenReturn(Optional.of(new TransactionType("01", "PURCHASE")));

        assertThat(service.get("1")).isEqualTo(new TransactionTypeDto("01", "PURCHASE"));
    }

    @Test
    void getMissing() { // REQ-CTTU-007
        when(repository.findById("42")).thenReturn(Optional.empty());

        ApiException e = apiError(() -> service.get("42"));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(e.getMessage()).isEqualTo(Messages.RECORD_NOT_FOUND);
    }

    @Test
    void getDbError() { // REQ-CTTU-008
        when(repository.findById("42")).thenThrow(new DataAccessResourceFailureException("down"));

        assertThat(apiError(() -> service.get("42")).getMessage()).isEqualTo(Messages.READ_FAILED);
    }

    // ------------------------------------------------------------- create

    @Test
    void createInsertsTrimmedValues() { // REQ-CTTU-013
        TransactionTypeDto created = service.create("8", "  FEES  ");

        assertThat(created).isEqualTo(new TransactionTypeDto("08", "FEES"));
        verify(repository).insert("08", "FEES");
    }

    @Test
    void createDuplicatePropagatesIntegrityViolation() { // REQ-CTTU-014
        when(repository.insert("01", "X")).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.create("01", "X")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void createOtherDbError() {
        when(repository.insert("08", "X")).thenThrow(new DataAccessResourceFailureException("down"));

        assertThat(apiError(() -> service.create("08", "X")).getMessage()).isEqualTo(Messages.INSERT_FAILED);
    }

    // ------------------------------------------------------------- update

    @Test
    void updateExisting() { // REQ-CTLI-023
        when(repository.findById("01")).thenReturn(Optional.of(new TransactionType("01", "PURCHASE")));
        when(repository.updateDescription("01", "PURCHASES")).thenReturn(1);

        SaveResult result = service.update("01", "PURCHASES", false);

        assertThat(result.inserted()).isFalse();
        assertThat(result.transactionType()).isEqualTo(new TransactionTypeDto("01", "PURCHASES"));
    }

    @Test
    void updateNoChange() { // REQ-CTLI-022, REQ-CTTU-011
        when(repository.findById("01")).thenReturn(Optional.of(new TransactionType("01", "PURCHASE")));

        ApiException ctli = apiError(() -> service.update("01", "purchase", false));
        ApiException cttu = apiError(() -> service.update("01", "purchase", true));

        assertThat(ctli.getCode()).isEqualTo(ErrorCode.NO_CHANGE);
        assertThat(ctli.getMessage()).isEqualTo(Messages.NO_CHANGE_DATABASE);
        assertThat(cttu.getMessage()).isEqualTo(Messages.NO_CHANGE_FETCHED);
        verify(repository, never()).updateDescription(anyString(), anyString());
    }

    @Test
    void updateMissingWithoutUpsert() { // REQ-CTLI-024
        when(repository.findById("42")).thenReturn(Optional.empty());
        when(repository.updateDescription("42", "X")).thenReturn(0);

        ApiException e = apiError(() -> service.update("42", "X", false));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(e.getMessage()).isEqualTo(Messages.UPDATE_NOT_FOUND);
        verify(repository, never()).insert(anyString(), anyString());
    }

    @Test
    void upsertInsertsWhenUpdateFindsNothing() { // REQ-CTTU-013 (9600 +100 -> 9700)
        when(repository.findById("42")).thenReturn(Optional.empty());
        when(repository.updateDescription("42", "NEW TYPE")).thenReturn(0);

        SaveResult result = service.update("42", "NEW TYPE", true);

        assertThat(result.inserted()).isTrue();
        verify(repository).insert("42", "NEW TYPE");
    }

    @Test
    void updateLockConflictMessages() { // REQ-CTLI-025, REQ-CTTU-015
        when(repository.findById("01")).thenReturn(Optional.empty());
        when(repository.updateDescription("01", "X")).thenThrow(new CannotAcquireLockException("lock"));

        ApiException ctli = apiError(() -> service.update("01", "X", false));
        ApiException cttu = apiError(() -> service.update("01", "X", true));

        assertThat(ctli.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ctli.getCode()).isEqualTo(ErrorCode.LOCK_CONFLICT);
        assertThat(ctli.getMessage()).isEqualTo(Messages.UPDATE_DEADLOCK);
        assertThat(cttu.getMessage()).isEqualTo(Messages.COULD_NOT_LOCK);
    }

    @Test
    void updateOtherDbErrorMessages() { // REQ-CTLI-026, REQ-CTTU-016
        when(repository.findById("01")).thenReturn(Optional.empty());
        when(repository.updateDescription("01", "X")).thenThrow(new DataAccessResourceFailureException("down"));

        assertThat(apiError(() -> service.update("01", "X", false)).getMessage()).isEqualTo(Messages.UPDATE_FAILED);
        assertThat(apiError(() -> service.update("01", "X", true)).getMessage())
                .isEqualTo(Messages.CTTU_UPDATE_FAILED);
    }

    @Test
    void updateValidatesBeforeDatabase() { // REQ-CTLI-021
        assertThatThrownBy(() -> service.update("01", "BAD-DESC", false)).hasMessage(Messages.DESC_ALPHANUM);
        assertThatThrownBy(() -> service.update("0", "OK", false)).hasMessage(Messages.CODE_NOT_ZERO);
        verify(repository, never()).findById(anyString());
    }

    // ------------------------------------------------------------- delete

    @Test
    void deleteExisting() { // REQ-CTLI-017, REQ-CTTU-018
        when(repository.deleteByTrType("09")).thenReturn(1);

        service.delete("9");

        verify(repository).deleteByTrType("09");
    }

    @Test
    void deleteMissing() { // REQ-EDGE-014
        when(repository.deleteByTrType("42")).thenReturn(0);

        ApiException e = apiError(() -> service.delete("42"));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(e.getMessage()).isEqualTo(Messages.DELETE_NOT_FOUND);
    }

    @Test
    void deleteWithChildrenPropagatesIntegrityViolation() { // REQ-CTLI-018
        when(repository.deleteByTrType("01")).thenThrow(new DataIntegrityViolationException("fk"));

        assertThatThrownBy(() -> service.delete("01")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deleteOtherDbError() { // REQ-CTLI-019, REQ-CTTU-020
        when(repository.deleteByTrType("01")).thenThrow(new DataAccessResourceFailureException("down"));

        assertThat(apiError(() -> service.delete("01")).getMessage()).isEqualTo(Messages.DELETE_FAILED);
    }
}
