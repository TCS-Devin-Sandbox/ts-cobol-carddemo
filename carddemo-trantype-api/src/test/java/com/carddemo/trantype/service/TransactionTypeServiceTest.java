package com.carddemo.trantype.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carddemo.trantype.domain.TransactionType;
import com.carddemo.trantype.repository.TransactionTypeRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionTypeServiceTest {

    @Mock
    private TransactionTypeRepository repository;

    private TransactionTypeService service;

    /** In-memory table driving the mocked keyset queries. */
    private final List<TransactionType> table = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new TransactionTypeService(repository);
        when(repository.findForward(anyString(), anyBoolean(), anyString(), anyBoolean(), anyString(), any(Limit.class)))
                .thenAnswer(inv -> table.stream()
                        .filter(t -> t.getTrType().compareTo(inv.getArgument(0)) >= 0)
                        .filter(t -> !(boolean) inv.getArgument(1) || t.getTrType().equals(inv.getArgument(2)))
                        .limit(((Limit) inv.getArgument(5)).max())
                        .toList());
        when(repository.findBackward(anyString(), anyBoolean(), anyString(), anyBoolean(), anyString(), any(Limit.class)))
                .thenAnswer(inv -> table.stream()
                        .sorted(java.util.Comparator.comparing(TransactionType::getTrType).reversed())
                        .filter(t -> t.getTrType().compareTo(inv.getArgument(0)) < 0)
                        .filter(t -> !(boolean) inv.getArgument(1) || t.getTrType().equals(inv.getArgument(2)))
                        .limit(((Limit) inv.getArgument(5)).max())
                        .toList());
    }

    private void rows(int count) {
        IntStream.rangeClosed(1, count).forEach(i -> table.add(new TransactionType("%02d".formatted(i), "DESC " + i)));
    }

    @Test
    void firstPageReadsSevenRowsWithLookAhead() {
        rows(16);
        TransactionTypePage page = service.list(null, PageDirection.FORWARD, 7, 1, null, null);
        assertThat(page.rows()).extracting(TransactionTypeDto::trType)
                .containsExactly("01", "02", "03", "04", "05", "06", "07");
        assertThat(page.hasNext()).isTrue();
        assertThat(page.nextCursor()).isEqualTo("08");
        assertThat(page.firstKey()).isEqualTo("01");
        assertThat(page.lastKey()).isEqualTo("07");
        assertThat(page.hasPrev()).isFalse();
        assertThat(page.pageNumber()).isEqualTo(1);
        verify(repository).findForward(eq(""), eq(false), eq(""), eq(false), eq(""), eq(Limit.of(8)));
    }

    @Test
    void exactlySevenRowsHasNoNextPage() {
        rows(7);
        TransactionTypePage page = service.list("", PageDirection.FORWARD, 7, 1, null, null);
        assertThat(page.rows()).hasSize(7);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void forwardFromCursorIsInclusive() {
        rows(16);
        TransactionTypePage page = service.list("15", PageDirection.FORWARD, 7, 3, null, null);
        assertThat(page.rows()).extracting(TransactionTypeDto::trType).containsExactly("15", "16");
        assertThat(page.hasNext()).isFalse();
        assertThat(page.hasPrev()).isTrue();
        assertThat(page.pageNumber()).isEqualTo(3);
    }

    @Test
    void backwardReadsDescendingAndReverses() {
        rows(16);
        TransactionTypePage page = service.list("15", PageDirection.BACKWARD, 7, 2, null, null);
        assertThat(page.rows()).extracting(TransactionTypeDto::trType)
                .containsExactly("08", "09", "10", "11", "12", "13", "14");
        assertThat(page.hasNext()).isTrue();
        assertThat(page.nextCursor()).isEqualTo("15");
        assertThat(page.hasPrev()).isTrue();
        assertThat(page.pageNumber()).isEqualTo(2);
    }

    @Test
    void backwardWithFewerThanPageSizeReturnsFirstPage() {
        rows(16);
        TransactionTypePage page = service.list("05", PageDirection.BACKWARD, 7, 2, null, null);
        assertThat(page.rows()).extracting(TransactionTypeDto::trType).startsWith("01").hasSize(7);
        assertThat(page.pageNumber()).isEqualTo(1);
        assertThat(page.hasPrev()).isFalse();
    }

    @Test
    void emptyTableReturnsSourceMessage() {
        TransactionTypePage page = service.list(null, PageDirection.FORWARD, 7, 1, null, null);
        assertThat(page.rows()).isEmpty();
        assertThat(page.message()).isEqualTo("No records found for this search condition.");
        assertThat(page.hasNext()).isFalse();
        assertThat(page.hasPrev()).isFalse();
    }

    @Test
    void filterWithNoMatchReturnsFilterMessage() {
        rows(3);
        TransactionTypePage page = service.list(null, PageDirection.FORWARD, 7, 1, "09", null);
        assertThat(page.message()).isEqualTo("No Records found for these filter conditions");
    }

    @Test
    void typeFilterExactMatch() {
        rows(9);
        TransactionTypePage page = service.list(null, PageDirection.FORWARD, 7, 1, "03", null);
        assertThat(page.rows()).extracting(TransactionTypeDto::trType).containsExactly("03");
    }

    @Test
    void zeroOrBlankTypeFilterMeansNoFilter() {
        rows(3);
        assertThat(service.list(null, PageDirection.FORWARD, 7, 1, "00", " ").rows()).hasSize(3);
    }

    @Test
    void descFilterWrappedInPercent() {
        rows(3);
        service.list(null, PageDirection.FORWARD, 7, 1, null, "  CRED ");
        verify(repository).findForward(eq(""), eq(false), eq(""), eq(true), eq("%CRED%"), eq(Limit.of(8)));
    }

    @Test
    void invalidTypeFilterUsesSourceLiteral() {
        for (String bad : new String[] {"5", "5a", "ab", "123"}) {
            assertThatThrownBy(() -> service.list(null, PageDirection.FORWARD, 7, 1, bad, null))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER");
        }
    }

    @Test
    void sizeAboveSevenRejected() {
        assertThatThrownBy(() -> service.list(null, PageDirection.FORWARD, 8, 1, null, null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void getPadsKeyAndReturnsRow() {
        when(repository.findById("05")).thenReturn(Optional.of(new TransactionType("05", "REFUND")));
        assertThat(service.get("5")).isEqualTo(new TransactionTypeDto("05", "REFUND"));
    }

    @Test
    void getMissingKeyIsNotFound() {
        when(repository.findById("99")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get("99"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getMessage()).isEqualTo("No record found for this key in database");
                    assertThat(e.getSqlcode()).isEqualTo(100);
                });
    }

    @Test
    void upsertUpdatesExistingRow() {
        when(repository.updateDescription("01", "NEW DESC")).thenReturn(1);
        SaveResult result = service.save("1", "  NEW DESC ", true);
        assertThat(result.created()).isFalse();
        assertThat(result.transactionType()).isEqualTo(new TransactionTypeDto("01", "NEW DESC"));
        verify(repository, never()).insert(anyString(), anyString());
    }

    @Test
    void upsertInsertsWhenUpdateFindsNoRow() {
        when(repository.updateDescription("42", "ANSWER")).thenReturn(0);
        SaveResult result = service.save("42", "ANSWER", true);
        assertThat(result.created()).isTrue();
        verify(repository).insert("42", "ANSWER");
    }

    @Test
    void ctliUpdateWithoutRowIsDeletedByOthers() {
        when(repository.updateDescription("42", "ANSWER")).thenReturn(0);
        assertThatThrownBy(() -> service.save("42", "ANSWER", false))
                .isInstanceOf(ApiException.class)
                .hasMessage("Record not found. Deleted by others ? ");
        verify(repository, never()).insert(anyString(), anyString());
    }

    @Test
    void createInsertsTrimmedValue() {
        assertThat(service.create("8", " NEW ")).isEqualTo(new TransactionTypeDto("08", "NEW"));
        verify(repository).insert("08", "NEW");
    }

    @Test
    void deleteMissingRowFails() {
        when(repository.deleteByTrType("77")).thenReturn(0);
        assertThatThrownBy(() -> service.delete("77"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Delete failed with message:");
    }

    @Test
    void deleteExistingRow() {
        when(repository.deleteByTrType("08")).thenReturn(1);
        service.delete("08");
        verify(repository).deleteByTrType("08");
    }

    @Test
    void forwardFromCursorWithNoRemainingRowsRestartsAtFirstPage() {
        rows(9);
        TransactionTypePage page = service.list("50", PageDirection.FORWARD, 7, 2, null, null);
        assertThat(page.rows()).extracting(TransactionTypeDto::trType).startsWith("01").hasSize(7);
        assertThat(page.pageNumber()).isEqualTo(1);
        assertThat(page.message()).isNull();
    }
}
