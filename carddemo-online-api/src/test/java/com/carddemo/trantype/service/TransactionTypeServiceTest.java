package com.carddemo.trantype.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carddemo.trantype.domain.TransactionType;
import com.carddemo.trantype.repository.TransactionTypeRepository;
import com.carddemo.trantype.repository.TypeFilters;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class TransactionTypeServiceTest {

    @Mock
    TransactionTypeRepository repository;

    @InjectMocks
    TransactionTypeService service;

    private static List<TransactionType> types(int from, int count) {
        return IntStream.range(from, from + count)
                .mapToObj(i -> new TransactionType(String.format("%02d", i), "DESC " + i)).toList();
    }

    private static List<TransactionType> reversed(List<TransactionType> list) {
        List<TransactionType> copy = new java.util.ArrayList<>(list);
        java.util.Collections.reverse(copy);
        return copy;
    }

    @Test
    void firstPageUsesLookaheadForHasNext() {
        when(repository.findForward("", TypeFilters.NONE, 8)).thenReturn(types(1, 8));
        when(repository.existsBefore("01", TypeFilters.NONE)).thenReturn(false);

        TransactionTypePage page = service.page(null, PageDirection.FORWARD, 7, 1, null, null);

        assertThat(page.rows()).hasSize(7);
        assertThat(page.firstKey()).isEqualTo("01");
        assertThat(page.lastKey()).isEqualTo("07");
        assertThat(page.nextCursor()).isEqualTo("08");
        assertThat(page.hasNext()).isTrue();
        assertThat(page.hasPrev()).isFalse();
        assertThat(page.pageNumber()).isEqualTo(1);
    }

    @Test
    void lastPageHasNoNext() {
        when(repository.findForward("08", TypeFilters.NONE, 8)).thenReturn(types(8, 2));
        when(repository.existsBefore("08", TypeFilters.NONE)).thenReturn(true);

        TransactionTypePage page = service.page("08", PageDirection.FORWARD, 7, 2, null, null);

        assertThat(page.rows()).extracting(TransactionTypeDto::trType).containsExactly("08", "09");
        assertThat(page.hasNext()).isFalse();
        assertThat(page.hasPrev()).isTrue();
        assertThat(page.pageNumber()).isEqualTo(2);
    }

    @Test
    void backwardPageIsReversedToAscending() {
        when(repository.findBackward("08", TypeFilters.NONE, 7)).thenReturn(reversed(types(1, 7)));
        when(repository.firstKeyAfter("07", TypeFilters.NONE)).thenReturn(Optional.of("08"));
        when(repository.existsBefore("01", TypeFilters.NONE)).thenReturn(false);

        TransactionTypePage page = service.page("08", PageDirection.BACKWARD, 7, 1, null, null);

        assertThat(page.rows()).extracting(TransactionTypeDto::trType)
                .containsExactly("01", "02", "03", "04", "05", "06", "07");
        assertThat(page.hasNext()).isTrue();
        assertThat(page.nextCursor()).isEqualTo("08");
        assertThat(page.pageNumber()).isEqualTo(1);
    }

    @Test
    void emptyTableMessage() {
        when(repository.findForward(anyString(), any(), anyInt())).thenReturn(List.of());
        TransactionTypePage page = service.page(null, PageDirection.FORWARD, 7, 1, null, null);
        assertThat(page.rows()).isEmpty();
        assertThat(page.message()).isEqualTo("No records found for this search condition.");
    }

    @Test
    void filterWithNoMatchesMessage() {
        when(repository.countMatching(new TypeFilters("09", null))).thenReturn(0L);
        TransactionTypePage page = service.page(null, PageDirection.FORWARD, 7, 1, "9", null);
        assertThat(page.message()).isEqualTo("No Records found for these filter conditions");
    }

    @Test
    void filtersArePassedThrough() {
        TypeFilters filters = new TypeFilters(null, "%PAY%");
        when(repository.countMatching(filters)).thenReturn(1L);
        when(repository.findForward("", filters, 8)).thenReturn(types(2, 1));
        service.page(null, PageDirection.FORWARD, 7, 1, "", "PAY");
        verify(repository).findForward("", filters, 8);
    }

    @Test
    void getNotFound() {
        when(repository.findById("05")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get("5"))
                .isInstanceOf(ApiException.class)
                .hasMessage("No record found for this key in database")
                .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void saveInsertsWhenMissing() {
        when(repository.findById("08")).thenReturn(Optional.empty());
        SaveResult result = service.save("8", " NEW TYPE ");
        assertThat(result.created()).isTrue();
        assertThat(result.transactionType()).isEqualTo(new TransactionTypeDto("08", "NEW TYPE"));
        verify(repository).insert(any(TransactionType.class));
    }

    @Test
    void saveUpdatesExisting() {
        TransactionType existing = new TransactionType("01", "PURCHASE");
        when(repository.findById("01")).thenReturn(Optional.of(existing));
        SaveResult result = service.save("01", "PURCHASES");
        assertThat(result.created()).isFalse();
        assertThat(existing.getTrDescription()).isEqualTo("PURCHASES");
        verify(repository, never()).insert(any());
    }

    @Test
    void saveUnchangedIsRejected() {
        when(repository.findById("01")).thenReturn(Optional.of(new TransactionType("01", "PURCHASE")));
        assertThatThrownBy(() -> service.save("01", "purchase"))
                .hasMessage("No change detected with respect to values fetched.");
    }

    @Test
    void saveLockFailureMapsToCouldNotLock() {
        when(repository.findById("01")).thenReturn(Optional.of(new TransactionType("01", "PURCHASE")));
        doThrow(new CannotAcquireLockException("lock")).when(repository).flush();
        assertThatThrownBy(() -> service.save("01", "OTHER"))
                .hasMessage("Could not lock record for update")
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void inlineUpdateMissingRow() {
        when(repository.findById("03")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateDescription("03", "X"))
                .hasMessage("Record not found. Deleted by others ?");
    }

    @Test
    void inlineUpdateUnchanged() {
        when(repository.findById("03")).thenReturn(Optional.of(new TransactionType("03", "CREDIT")));
        assertThatThrownBy(() -> service.updateDescription("03", "CREDIT"))
                .hasMessage("No change detected with respect to database values.");
    }

    @Test
    void inlineUpdateDeadlock() {
        when(repository.findById("03")).thenReturn(Optional.of(new TransactionType("03", "CREDIT")));
        doThrow(new CannotAcquireLockException("deadlock")).when(repository).flush();
        assertThatThrownBy(() -> service.updateDescription("03", "CREDITS"))
                .hasMessage("Deadlock. Someone else updating ?");
    }

    @Test
    void deleteMissingRow() {
        when(repository.findById("09")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete("09")).hasMessage("Delete failed with message:SQLCODE :+100");
    }

    @Test
    void deleteExisting() {
        TransactionType existing = new TransactionType("09", "X");
        when(repository.findById("09")).thenReturn(Optional.of(existing));
        service.delete("09");
        verify(repository).delete(eq(existing));
        verify(repository).flush();
    }
}
