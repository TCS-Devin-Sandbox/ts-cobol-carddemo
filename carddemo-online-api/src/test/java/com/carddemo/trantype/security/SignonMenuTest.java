package com.carddemo.trantype.security;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@WebMvcTest({AuthController.class, MenuController.class})
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
@ActiveProfiles("dev")
class SignonMenuTest {
    @Autowired MockMvc mvc;
    @MockBean UserSecurity users;

    private void login(String body, int status, String message) throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(status)).andExpect(jsonPath("$.message").value(message));
    }

    @Test void COSGN00C_PROCESS_ENTER_KEY_SEND_SIGNON_SCREEN() throws Exception {
        login("{}", 400, "Please enter User ID ...");
        login("{\"userId\":\"A\"}", 400, "Please enter Password ...");
        verifyNoInteractions(users);
    }

    @Test void COSGN00C_READ_USER_SEC_FILE() throws Exception {
        when(users.find("TEST")).thenReturn(Optional.empty());
        login("{\"userId\":\"test\",\"password\":\"x\"}",401,"User not found. Try again ...");
        var user = new UserSecurity.User("TEST", "hash", "U");
        when(users.find("TEST")).thenReturn(Optional.of(user));
        login("{\"userId\":\"test\",\"password\":\"x\"}",401,"Wrong Password. Try again ...");
        when(users.find("TEST")).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("read"));
        login("{\"userId\":\"test\",\"password\":\"x\"}",500,"Unable to verify the User ...");
    }

    @Test void COMEN01C_COADM01C_MAIN_PARA_BUILD_MENU_OPTIONS_POPULATE_HEADER_INFO() throws Exception {
        mvc.perform(get("/api/menus/main")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/menus/main").with(jwt())).andExpect(status().isOk())
                .andExpect(jsonPath("$.tranId").value("CM00")).andExpect(jsonPath("$.program").value("COMEN01C"))
                .andExpect(jsonPath("$.options.length()").value(11))
                .andExpect(jsonPath("$.options[0]").value("01. Account View                       "));
        mvc.perform(get("/api/menus/admin").with(jwt())).andExpect(status().isForbidden());
        mvc.perform(get("/api/menus/admin").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(jsonPath("$.tranId").value("CA00")).andExpect(jsonPath("$.options.length()").value(6));
    }

    @Test void COMEN01C_PROCESS_ENTER_KEY_PGMIDERR_ERR_PARA() throws Exception {
        for (String option : new String[]{"", "0", "12", "a", "99"})
            mvc.perform(post("/api/menus/main/select").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"option\":\"" + option + "\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.severity").value("error"));
        mvc.perform(post("/api/menus/main/select").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"option\":\"1 \"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("This option is not installed ..."));
        mvc.perform(post("/api/menus/main/select").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"option\":\"11\"}")).andExpect(jsonPath("$.severity").value("error"))
                .andExpect(jsonPath("$.message").value("This option Pending Authorization View is not installed..."));
    }

    @Test void COADM01C_PROCESS_ENTER_KEY_XCTL() throws Exception {
        mvc.perform(post("/api/menus/admin/select").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"option\":\" 5\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.route").value("/transaction-types"));
    }

    @Test void COMEN01C_PROCESS_ENTER_KEY_DUMMY_STRING() {
        org.assertj.core.api.Assertions.assertThat(new MenuController().target(
                new MenuController.Option("Future Option", "DUMMY01", "U")).getBody().toString())
                .contains("This option Futureis coming soon ...");
    }
}
