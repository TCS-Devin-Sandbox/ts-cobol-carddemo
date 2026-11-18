package com.carddemo.trantype.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carddemo.trantype.repository.TransactionTypeCategoryRepository;
import com.carddemo.trantype.repository.TransactionTypeRepository;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** End-to-end against PostgreSQL 15 with the real Flyway migrations (V1 + V2 seed). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TransactionTypeApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    private static final RequestPostProcessor ADMIN = user("admin").roles("ADMIN");
    private static final String BASE = "/api/transaction-types";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TransactionTypeRepository types;

    @Autowired
    private TransactionTypeCategoryRepository categories;

    @BeforeEach
    void resetToSeed() {
        jdbc.execute("DELETE FROM transaction_type_category");
        jdbc.execute("DELETE FROM transaction_type");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V2__seed_data.sql")).execute(dataSource);
    }

    private void addTypes(int from, int to) {
        for (int i = from; i <= to; i++) {
            jdbc.update("INSERT INTO transaction_type VALUES (?, ?)", "%02d".formatted(i), "TYPE " + i);
        }
    }

    private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder b, String description) {
        return b.with(ADMIN).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"" + description + "\"}");
    }

    @Test
    void seedDataLoadedByFlyway() {
        assertThat(types.count()).isEqualTo(7);
        assertThat(categories.count()).isEqualTo(18);
        assertThat(categories.countByIdTypeCode("01")).isEqualTo(5);
    }

    @Test
    void firstPageOfSeedData() throws Exception {
        mvc.perform(get(BASE).with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows", hasSize(7)))
                .andExpect(jsonPath("$.rows[0].trType").value("01"))
                .andExpect(jsonPath("$.rows[0].description").value("PURCHASE"))
                .andExpect(jsonPath("$.rows[5].description").value("REVERAL"))
                .andExpect(jsonPath("$.firstKey").value("01"))
                .andExpect(jsonPath("$.lastKey").value("07"))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrev").value(false))
                .andExpect(jsonPath("$.pageNumber").value(1));
    }

    @Test
    void pagesForwardAndBackwardSevenAtATime() throws Exception {
        addTypes(8, 16);
        mvc.perform(get(BASE).with(ADMIN))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor").value("08"));
        mvc.perform(get(BASE).param("cursor", "08").param("page", "2").with(ADMIN))
                .andExpect(jsonPath("$.rows[*].trType", contains("08", "09", "10", "11", "12", "13", "14")))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.hasPrev").value(true))
                .andExpect(jsonPath("$.nextCursor").value("15"))
                .andExpect(jsonPath("$.pageNumber").value(2));
        mvc.perform(get(BASE).param("cursor", "15").param("page", "3").with(ADMIN))
                .andExpect(jsonPath("$.rows[*].trType", contains("15", "16")))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.pageNumber").value(3));
        mvc.perform(get(BASE).param("cursor", "15").param("direction", "backward").param("page", "2").with(ADMIN))
                .andExpect(jsonPath("$.rows[*].trType", contains("08", "09", "10", "11", "12", "13", "14")))
                .andExpect(jsonPath("$.firstKey").value("08"))
                .andExpect(jsonPath("$.nextCursor").value("15"));
        mvc.perform(get(BASE).param("cursor", "08").param("direction", "backward").param("page", "1").with(ADMIN))
                .andExpect(jsonPath("$.rows[*].trType", contains("01", "02", "03", "04", "05", "06", "07")))
                .andExpect(jsonPath("$.hasPrev").value(false))
                .andExpect(jsonPath("$.pageNumber").value(1));
    }

    @Test
    void emptyTable() throws Exception {
        jdbc.execute("DELETE FROM transaction_type_category");
        jdbc.execute("DELETE FROM transaction_type");
        mvc.perform(get(BASE).with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows", hasSize(0)))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrev").value(false))
                .andExpect(jsonPath("$.message").value("No records found for this search condition."));
    }

    @Test
    void filters() throws Exception {
        mvc.perform(get(BASE).param("typeFilter", "03").with(ADMIN))
                .andExpect(jsonPath("$.rows[*].trType", contains("03")));
        mvc.perform(get(BASE).param("descFilter", "RE").with(ADMIN))
                .andExpect(jsonPath("$.rows[*].description", contains("CREDIT", "REFUND", "REVERAL")));
        mvc.perform(get(BASE).param("descFilter", "re").with(ADMIN))
                .andExpect(jsonPath("$.message").value("No Records found for these filter conditions"));
        mvc.perform(get(BASE).param("typeFilter", "5").with(ADMIN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER"));
    }

    @Test
    void getExistingAndMissing() throws Exception {
        mvc.perform(get(BASE + "/5").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trType").value("05"))
                .andExpect(jsonPath("$.description").value("REFUND"));
        mvc.perform(get(BASE + "/99").with(ADMIN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No record found for this key in database"));
    }

    @Test
    void addThenDuplicate() throws Exception {
        mvc.perform(body(post(BASE + "/8"), "CASHBACK"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trType").value("08"));
        mvc.perform(body(post(BASE + "/08"), "AGAIN"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_KEY"))
                .andExpect(jsonPath("$.message").value("Error inserting record into: TRANSACTION_TYPE Table. SQLCODE:"))
                .andExpect(jsonPath("$.sqlcode").value(-803));
        assertThat(types.findById("08")).get().extracting(t -> t.getDescription()).isEqualTo("CASHBACK");
    }

    @Test
    void upsertUpdatesThenInserts() throws Exception {
        mvc.perform(body(put(BASE + "/01"), "PURCHASE NEW"))
                .andExpect(status().isOk());
        assertThat(types.findById("01")).get().extracting(t -> t.getDescription()).isEqualTo("PURCHASE NEW");
        mvc.perform(body(put(BASE + "/50"), "FIFTY"))
                .andExpect(status().isCreated());
        assertThat(types.existsById("50")).isTrue();
    }

    @Test
    void ctliUpdateDoesNotInsert() throws Exception {
        mvc.perform(body(put(BASE + "/51").param("upsert", "false"), "NOPE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Record not found. Deleted by others ? "));
        assertThat(types.existsById("51")).isFalse();
    }

    @Test
    void invalidInputsRejectedWithSourceText() throws Exception {
        mvc.perform(body(put(BASE + "/01"), ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Transaction Desc must be supplied."));
        mvc.perform(body(put(BASE + "/01"), "BAD_DESC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Transaction Desc can have numbers or alphabets only."));
        mvc.perform(get(BASE + "/0").with(ADMIN))
                .andExpect(jsonPath("$.message").value("Tran Type code must not be zero."));
    }

    @Test
    void deleteWithChildrenRejectedAndRowKept() throws Exception {
        mvc.perform(delete(BASE + "/01").with(ADMIN).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHILD_RECORDS_EXIST"))
                .andExpect(jsonPath("$.message").value("Please delete associated child records first:"))
                .andExpect(jsonPath("$.sqlcode").value(-532));
        assertThat(types.existsById("01")).isTrue();
        assertThat(categories.countByIdTypeCode("01")).isEqualTo(5);
    }

    @Test
    void deleteWithoutChildren() throws Exception {
        addTypes(8, 8);
        mvc.perform(delete(BASE + "/08").with(ADMIN).with(csrf())).andExpect(status().isNoContent());
        assertThat(types.existsById("08")).isFalse();
        mvc.perform(delete(BASE + "/08").with(ADMIN).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Delete failed with message:"));
    }

    @Test
    void nonAdminAndAnonymousRejected() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE).with(user("user").roles("USER"))).andExpect(status().isForbidden());
    }
}
