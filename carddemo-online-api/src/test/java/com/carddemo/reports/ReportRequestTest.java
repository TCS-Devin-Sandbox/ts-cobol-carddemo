package com.carddemo.reports;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;

import com.carddemo.menu.MenuController;
import com.carddemo.trantype.security.SecurityConfig;
import com.carddemo.trantype.security.SecurityProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({ReportRequestController.class, MenuController.class})
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
@ActiveProfiles("dev")
class ReportRequestTest {
    private static final LocalDate LEAP_FEBRUARY = LocalDate.of(2024, 2, 12);
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ReportRequestController reports;
    @MockBean ReportLauncher launcher;

    @Test
    void mainParaRequiresCommareaAndMainMenuOpensCr00ForUser() throws Exception {
        mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monthly\":true,\"confirmation\":\"Y\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/menus/main/select").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"option\":\"09\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.program").value("CORPT00C"))
                .andExpect(jsonPath("$.tranId").value("CR00")).andExpect(jsonPath("$.route").value("/reports"));
        verifyNoInteractions(launcher);
    }

    @Test
    void processEnterKeyChoosesMonthlyBeforeYearlyBeforeCustom() throws Exception {
        when(launcher.submit(LocalDate.parse("2024-02-01"), LocalDate.parse("2024-02-29"))).thenReturn("monthly-id");
        when(launcher.submit(LocalDate.parse("2024-01-01"), LocalDate.parse("2024-12-31"))).thenReturn("yearly-id");
        when(launcher.submit(LocalDate.parse("2024-12-01"), LocalDate.parse("2024-12-31"))).thenReturn("december-id");
        var monthly = new ReportRequestController.Request(true, true, true, null, null, "y");
        var yearly = new ReportRequestController.Request(false, true, true, null, null, "Y");
        assertThat(reports.process(monthly, LEAP_FEBRUARY).getBody().jobId()).isEqualTo("monthly-id");
        assertThat(reports.process(yearly, LEAP_FEBRUARY).getBody().jobId()).isEqualTo("yearly-id");
        assertThat(reports.process(monthly, LocalDate.of(2024, 12, 20)).getBody().jobId()).isEqualTo("december-id");
        edit(Map.of(), "Select a report type to print report...", "monthly");
    }

    @ParameterizedTest
    @CsvSource({
            "start, '',      2,   2024, Month can NOT be empty, Month",
            "start, 2,       '',  2024, Day can NOT be empty,   Day",
            "start, 2,       2,   '',   Year can NOT be empty,  Year",
            "end,   '',      2,   2024, Month can NOT be empty, Month",
            "end,   2,       '',  2024, Day can NOT be empty,   Day",
            "end,   2,       2,   '',   Year can NOT be empty,  Year",
            "start, 13,      2,   2024, Not a valid Month,      Month",
            "start, 2,       32,  2024, Not a valid Day,        Day",
            "start, 2,       2,   test, Not a valid Year,       Year",
            "end,   13,      2,   2024, Not a valid Month,      Month",
            "end,   2,       32,  2024, Not a valid Day,        Day",
            "end,   2,       2,   test, Not a valid Year,       Year",
            "start, 2,       29,  2023, Not a valid date,       Month",
            "end,   4,       31,  2024, Not a valid date,       Month"
    })
    void processEnterKeyAndCsutldtcCheckCustomDateParts(String boundary, String month, String day,
            String year, String message, String focus) throws Exception {
        var good = Map.of("month", "2", "day", "3", "year", "2024");
        var bad = Map.of("month", month, "day", day, "year", year);
        boolean start = "start".equals(boundary);
        edit(Map.of("custom", true, "startDate", start ? bad : good, "endDate", start ? good : bad,
                        "confirmation", "Y"), (start ? "Start" : "End") + " Date - " + message + "...", boundary + focus);
        verifyNoInteractions(launcher);
    }

    @Test
    void submitJobToIntrdrPromptsRejectsOrCancelsWithoutScheduling() throws Exception {
        edit(Map.of("monthly", true), "Please confirm to print the Monthly report...", "confirmation");
        edit(Map.of("yearly", true, "confirmation", "x"), "\"x\" is not a valid value to confirm...", "confirmation");
        for (String no : new String[]{"N", "n"}) {
            mvc.perform(submission(Map.of("monthly", true, "confirmation", no))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.reset").value(true)).andExpect(jsonPath("$.focus").value("monthly"))
                    .andExpect(jsonPath("$.message").doesNotExist());
        }
        verifyNoInteractions(launcher);
    }

    @Test
    void writeJobsubTdqPassesBothDateParametersAndShowsSuccessOnlyOnAcceptance() throws Exception {
        var start = LocalDate.parse("2024-02-09");
        var end = LocalDate.parse("2024-02-29");
        when(launcher.submit(start, end)).thenReturn("request-id").thenThrow(new IOException("not available"));
        var body = Map.of("custom", true, "confirmation", "Y",
                "startDate", Map.of("month", "2", "day", "9", "year", "2024"),
                "endDate", Map.of("month", "2", "day", "29", "year", "2024"));
        mvc.perform(submission(body)).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("Custom report submitted for printing ..."))
                .andExpect(jsonPath("$.severity").value("success")).andExpect(jsonPath("$.jobId").value("request-id"))
                .andExpect(jsonPath("$.reset").value(true)).andExpect(jsonPath("$.focus").value("monthly"));
        mvc.perform(submission(body)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Unable to submit transaction report..."))
                .andExpect(jsonPath("$.reset").value(false)).andExpect(jsonPath("$.focus").value("monthly"));
        verify(launcher, times(2)).submit(start, end);
    }

    private void edit(Map<String, ?> body, String message, String focus) throws Exception {
        mvc.perform(submission(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(message)).andExpect(jsonPath("$.severity").value("error"))
                .andExpect(jsonPath("$.reset").value(false)).andExpect(jsonPath("$.focus").value(focus));
    }

    private MockHttpServletRequestBuilder submission(Map<String, ?> body) throws Exception {
        return post("/api/reports").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }
}
