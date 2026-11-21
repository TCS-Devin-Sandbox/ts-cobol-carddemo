package com.carddemo.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

class TransactionControllerTest {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    TransactionController controller = new TransactionController(jdbc);

    @Test void CT00_PROCESS_PAGE_BACKWARD_READPREV_TRANSACT_FILE() {
        when(jdbc.queryForList(anyString(), any(Object.class))).thenReturn(List.of(Map.of("transaction_id", "2"), Map.of("transaction_id", "1")));
        var page = controller.backward("3", 2);
        assertThat(page.rows().get(0)).containsEntry("transaction_id", "1");
        assertThat(page.message()).isEqualTo("You have reached the top of the page...");
        assertThat(page.pageNumber()).isEqualTo(1);
    }
    @Test void CT00_READPREV_TRANSACT_FILE_empty_error() {
        when(jdbc.queryForList(anyString(), any(Object.class))).thenReturn(List.of());
        assertThat(controller.backward("1", 2).rows()).isEmpty();
        when(jdbc.queryForList(anyString(), any(Object.class))).thenThrow(new DataAccessResourceFailureException("offline"));
        assertThat(controller.backward("1", 2).message()).isEqualTo("Unable to lookup transaction...");
    }

    @Test void CT00_PROCESS_ENTER_KEY_numeric() {
        assertThat(controller.list("ABC").message()).isEqualTo("Tran ID must be Numeric ...");
        verifyNoInteractions(jdbc);
    }
    @Test void CT00_STARTBR_TRANSACT_FILE_notfound() {
        when(jdbc.queryForList(anyString(), any(Object.class))).thenReturn(List.of());
        assertThat(controller.list("").message()).isEqualTo("You are at the top of the page...");
    }
    @Test void CT00_PROCESS_PAGE_FORWARD_READNEXT_POPULATE_TRAN_DATA() {
        var rows = IntStream.range(0, 11).mapToObj(i -> Map.<String, Object>of("transaction_id", i)).toList();
        when(jdbc.queryForList(anyString(), any(Object.class))).thenReturn(rows);
        var result = controller.list("0000000000000001");
        assertThat(result.rows()).hasSize(10);
        assertThat(result.pageNumber()).isEqualTo(1);
        assertThat(result.message()).isEmpty();
    }
    @Test void CT00_READNEXT_TRANSACT_FILE_endfile() {
        when(jdbc.queryForList(anyString(), any(Object.class))).thenReturn(List.of(Map.of("transaction_id", "1")));
        assertThat(controller.list("").message()).isEqualTo("You have reached the bottom of the page...");
    }
    @Test void CT00_STARTBR_TRANSACT_FILE_error() {
        when(jdbc.queryForList(anyString(), any(Object.class))).thenThrow(new DataAccessResourceFailureException("offline"));
        assertThat(controller.list("").message()).isEqualTo("Unable to lookup transaction...");
    }
    @Test void CT01_PROCESS_ENTER_KEY_empty() {
        assertThat(controller.view("").message()).isEqualTo("Tran ID can NOT be empty...");
        verifyNoInteractions(jdbc);
    }
    @Test void CT01_READ_TRANSACT_FILE_missing() {
        when(jdbc.queryForList(anyString(), any(Object.class))).thenReturn(List.of());
        assertThat(controller.view("ABC").message()).isEqualTo("Transaction ID NOT found...");
    }
    @Test void CT01_PROCESS_ENTER_KEY_READ_TRANSACT_FILE() {
        when(jdbc.queryForList(anyString(), any(Object.class))).thenReturn(List.of(Map.of("description", "SYNTHETIC")));
        assertThat(controller.view("1").transaction()).containsEntry("description", "SYNTHETIC");
    }
    @Test void CT01_READ_TRANSACT_FILE_error() {
        when(jdbc.queryForList(anyString(), any(Object.class))).thenThrow(new DataAccessResourceFailureException("offline"));
        assertThat(controller.view("1").message()).isEqualTo("Unable to lookup Transaction...");
    }
}
