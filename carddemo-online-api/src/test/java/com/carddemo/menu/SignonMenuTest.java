package com.carddemo.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.Optional;
import java.util.List;
import java.util.Map;
import com.carddemo.signon.AuthController;
import com.carddemo.signon.UserSecurity;
import com.carddemo.trantype.security.SecurityConfig;
import com.carddemo.trantype.security.SecurityProperties;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.oauth2.jwt.Jwt;
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
        login("{\"userId\":\"A\",\"password\":\"   \"}", 400, "Please enter Password ...");
        verifyNoInteractions(users);
    }

    @Test void COSGN00C_READ_USER_SEC_FILE() throws Exception {
        when(users.find("TEST")).thenReturn(Optional.empty());
        login("{\"userId\":\"test\",\"password\":\"x\"}",401,"User not found. Try again ...");
        var user = new UserSecurity.User("TEST", "hash", "U");
        when(users.find("TEST")).thenReturn(Optional.of(user));
        login("{\"userId\":\"test\",\"password\":\"x\"}",401,"Wrong Password. Try again ...");
        when(users.find("TEST")).thenThrow(new DataAccessResourceFailureException("read"));
        login("{\"userId\":\"test\",\"password\":\"x\"}",500,"Unable to verify the User ...");
    }

    @Test void COMEN01C_COADM01C_MAIN_PARA_BUILD_MENU_OPTIONS_POPULATE_HEADER_INFO() throws Exception {
        mvc.perform(get("/api/menus/main")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/menus/main").with(jwt())).andExpect(status().isOk())
                .andExpect(jsonPath("$.tranId").value("CM00")).andExpect(jsonPath("$.program").value("COMEN01C"))
                .andExpect(jsonPath("$.options.length()").value(11))
                .andExpect(jsonPath("$.options[0]").value("01. Account View                       "));
        mvc.perform(get("/api/menus/admin").with(jwt().jwt(j -> j.claim("user_type", "U"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access restricted to administrators"));
        mvc.perform(get("/api/menus/admin").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(jsonPath("$.tranId").value("CA00")).andExpect(jsonPath("$.options.length()").value(6));
    }

    @Test void COMEN01C_PROCESS_ENTER_KEY_PGMIDERR_ERR_PARA() throws Exception {
        for (String option : new String[]{"", "  ", "0", "12", "x", "99"}) {
            mvc.perform(post("/api/menus/main/select").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"option\":\"" + option + "\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.severity").value("error"))
                    .andExpect(jsonPath("$.message").value("Please enter a valid option number..."));
        }
        for (String option : new String[]{" 2", "2 "}) {
            mvc.perform(post("/api/menus/main/select").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"option\":\"" + option + "\"}")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.program").value("COACTUPC"))
                    .andExpect(jsonPath("$.tranId").value("CAUP"))
                    .andExpect(jsonPath("$.route").value("/accounts/update"));
        }
    }

    @Test
    void COMEN01C_PROCESS_ENTER_KEY_ROUTES_TO_CAVW() throws Exception {
        mvc.perform(post("/api/menus/main/select").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"option\":\" 1\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.program").value("COACTVWC"))
                .andExpect(jsonPath("$.tranId").value("CAVW"))
                .andExpect(jsonPath("$.route").value("/accounts/view"));
    }

    @Test
    void COMEN01C_PROCESS_ENTER_KEY_COPAUS0C() throws Exception {
        mvc.perform(post("/api/menus/main/select").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"option\":\"11\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.tranId").value("CPVS"))
                .andExpect(jsonPath("$.route").value("/authorizations"));
    }

    @Test void COADM01C_PROCESS_ENTER_KEY_XCTL() throws Exception {
        mvc.perform(post("/api/menus/admin/select").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"option\":\" 5\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.route").value("/transaction-types"));
    }

    @Test void COMEN01C_PROCESS_ENTER_KEY_DUMMY_STRING() {
        assertThat(new MenuController().target(
                new MenuController.Option("Future Option", "DUMMY01", "U")).getBody().toString())
                .contains("This option Futureis coming soon ...");
    }

    @Test
    void COMEN01C_PROCESS_ENTER_KEY_ADMIN_ONLY_OPTION() {
        Jwt user = Jwt.withTokenValue("test").header("alg", "HS256").subject("USER0001")
                .claim("user_type", "U").claim("roles", List.of("USER")).build();
        var response = new MenuController().select("main", new MenuController.Selection("1"), user,
                List.of(new MenuController.Option("Admin only", "COTRTLIC", "A")));
        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody()).isEqualTo(Map.of("message", "No access - Admin Only option... ",
                "severity", "error"));
    }
}
