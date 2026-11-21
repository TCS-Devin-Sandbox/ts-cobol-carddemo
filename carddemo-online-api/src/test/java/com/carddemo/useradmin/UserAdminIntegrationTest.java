package com.carddemo.useradmin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carddemo.signon.UserSecurity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("dev")
@Transactional
class UserAdminIntegrationTest {
    private static final String BASE = "/api/admin/users";
    private static final String RECORD = BASE + "/record";
    private static final RequestPostProcessor ADMIN = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    private static final RequestPostProcessor USER = jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"));
    private static final String NEW_USER = """
            {"userId":"test01","firstName":"First","lastName":"Last","password":"initial","userType":"u"}
            """;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserSecurity signonUsers;

    @ParameterizedTest
    @CsvSource({"1,CU00,COUSR00C,/admin/users", "2,CU01,COUSR01C,/admin/users/add",
            "3,CU02,COUSR02C,/admin/users/update", "4,CU03,COUSR03C,/admin/users/delete"})
    void MAIN_PARA_admin_menu_XCTL(String option, String tranId, String program, String route) throws Exception {
        mvc.perform(post("/api/menus/admin/select").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"option\":\"" + option + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tranId").value(tranId))
                .andExpect(jsonPath("$.program").value(program)).andExpect(jsonPath("$.route").value(route));
    }

    @Test
    void MAIN_PARA_requires_admin_COMMAREA() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE).with(USER)).andExpect(status().isForbidden());
        mvc.perform(get(RECORD).with(USER).param("userId", "TEST01")).andExpect(status().isForbidden());
        mvc.perform(post(BASE).with(USER).contentType(MediaType.APPLICATION_JSON).content(NEW_USER))
                .andExpect(status().isForbidden());
        mvc.perform(put(RECORD).with(USER).param("userId", "TEST01")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete(RECORD).with(USER).param("userId", "TEST01")).andExpect(status().isForbidden());
    }

    @Test
    void CU00_PROCESS_ENTER_KEY_PROCESS_PF7_KEY_PROCESS_PF8_KEY_STARTBR_READNEXT_READPREV_ENDBR() throws Exception {
        jdbc.update("""
                INSERT INTO user_security (user_id, first_name, last_name, password_hash, user_type, filler)
                SELECT 'BROWSE' || lpad(i::text, 2, '0'), 'First', 'Last', 'unused', 'U', ''
                FROM generate_series(1, 21) AS i
                """);
        mvc.perform(get(BASE).with(ADMIN).param("startId", "browse01 "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows.length()").value(10))
                .andExpect(jsonPath("$.rows[0].firstName").value("First"))
                .andExpect(jsonPath("$.rows[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.firstKey").value("BROWSE01")).andExpect(jsonPath("$.lastKey").value("BROWSE10"))
                .andExpect(jsonPath("$.hasNext").value(true));
        mvc.perform(get(BASE).with(ADMIN).param("startId", "BROWSE01").param("after", "BROWSE10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.firstKey").value("BROWSE11"))
                .andExpect(jsonPath("$.lastKey").value("BROWSE20"));
        mvc.perform(get(BASE).with(ADMIN).param("startId", "BROWSE01").param("before", "BROWSE11"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.firstKey").value("BROWSE01"))
                .andExpect(jsonPath("$.lastKey").value("BROWSE10"));
        mvc.perform(get(BASE).with(ADMIN).param("after", "BROWSE20").param("before", "BROWSE11"))
                .andExpect(status().isBadRequest());
        jdbc.update("DELETE FROM user_security WHERE user_id NOT LIKE 'BROWSE%'");
        mvc.perform(get(BASE).with(ADMIN).param("after", "BROWSE20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.firstKey").value("BROWSE21")).andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.message").value("You have reached the bottom of the page..."));
        mvc.perform(get(BASE).with(ADMIN).param("startId", "ZZZ"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows.length()").value(0))
                .andExpect(jsonPath("$.message").value("You are at the top of the page..."));
    }

    @ParameterizedTest
    @CsvSource({"firstName,First Name", "lastName,Last Name", "userId,User ID", "password,Password", "userType,User Type"})
    void CU01_PROCESS_ENTER_KEY_field_validation(String field, String label) throws Exception {
        String body = NEW_USER.replaceFirst("\"" + field + "\":\"[^\"]+\"", "\"" + field + "\":\"  \"");
        mvc.perform(post(BASE).with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(label + " can NOT be empty..."));
    }

    @Test
    void CU01_WRITE_USER_SEC_FILE_duplicate_and_signon() throws Exception {
        add();
        mvc.perform(post(BASE).with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(NEW_USER))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("User ID already exist..."));
        mvc.perform(post(BASE).with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_USER.replace("\"u\"", "\"X\"")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("User Type must be A or U..."));
        assertThat(hash()).startsWith("$2").isNotEqualTo("INITIAL");
        signon("initial");
    }

    @Test
    void CU02_CU03_PROCESS_ENTER_KEY_READ_USER_SEC_FILE_never_returns_password() throws Exception {
        add();
        mvc.perform(get(RECORD).with(ADMIN).param("userId", "test01 "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value("TEST01"))
                .andExpect(jsonPath("$.firstName").value("First")).andExpect(jsonPath("$.lastName").value("Last"))
                .andExpect(jsonPath("$.userType").value("U")).andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get(RECORD).with(ADMIN).param("userId", " "))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("User ID can NOT be empty..."));
        mvc.perform(get(RECORD).with(ADMIN).param("userId", "MISSING"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("User ID NOT found..."));
    }

    @Test
    void CU02_UPDATE_USER_INFO_UPDATE_USER_SEC_FILE_and_no_change() throws Exception {
        add();
        String hash = hash();
        mvc.perform(put(RECORD).with(ADMIN).param("userId", "TEST01").contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_USER))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Please modify to update ..."));
        mvc.perform(put(RECORD).with(ADMIN).param("userId", "TEST01").contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_USER.replace("\"initial\"", "\"\"").replace("First", "Updated")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("User TEST01 has been updated ..."));
        assertThat(hash()).isEqualTo(hash);
        mvc.perform(get(RECORD).with(ADMIN).param("userId", "TEST01"))
                .andExpect(jsonPath("$.firstName").value("Updated"));
        mvc.perform(put(RECORD).with(ADMIN).param("userId", "TEST01").contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_USER.replace("initial", "newpass")))
                .andExpect(status().isOk());
        signon("newpass");
        mvc.perform(put(RECORD).with(ADMIN).param("userId", "MISSING").contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_USER))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("User ID NOT found..."));
    }

    @Test
    void CU03_DELETE_USER_INFO_DELETE_USER_SEC_FILE_and_missing() throws Exception {
        add();
        mvc.perform(delete(RECORD).with(ADMIN).param("userId", ""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("User ID can NOT be empty..."));
        mvc.perform(delete(RECORD).with(ADMIN).param("userId", "test01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("User TEST01 has been deleted ..."));
        mvc.perform(delete(RECORD).with(ADMIN).param("userId", "TEST01"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("User ID NOT found..."));
    }

    @Test
    void CU03_DELETE_USER_SEC_FILE_doesNotReseedDeletedConfiguredAdmin() throws Exception {
        mvc.perform(delete(RECORD).with(ADMIN).param("userId", "ADMIN001"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("User ADMIN001 has been deleted ..."));

        signonUsers.run(null);

        assertThat(signonUsers.find("ADMIN001")).isEmpty();
        assertThat(signonUsers.find("USER0001")).isPresent();
    }

    private void add() throws Exception {
        mvc.perform(post(BASE).with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(NEW_USER))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.message").value("User TEST01 has been added ..."));
    }

    private String hash() {
        return jdbc.queryForObject("SELECT password_hash FROM user_security WHERE user_id = 'TEST01'", String.class);
    }

    private void signon(String password) throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"test01\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userType").value("U"));
    }
}
