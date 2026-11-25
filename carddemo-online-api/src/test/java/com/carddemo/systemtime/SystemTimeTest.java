package com.carddemo.systemtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carddemo.trantype.security.SecurityConfig;
import com.carddemo.trantype.security.SecurityProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SystemTimeController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
@ActiveProfiles("dev")
class SystemTimeTest {
    private static final ZoneId ZONE = ZoneId.of("America/New_York");
    // Frozen estate clock (AGENTS.md: COB_CURRENT_DATE=2022-07-05 10:00:00.00).
    private static final String FROZEN_REPLY = "SYSTEM DATE : 07-05-2022SYSTEM TIME : 10:00:00" + " ".repeat(954);

    @TestConfiguration
    static class FrozenClock {
        @Bean SystemTimeService systemTimeService() {
            return new SystemTimeService(at(LocalDateTime.of(2022, 7, 5, 10, 0, 0, 990_000_000)));
        }
    }

    @Autowired MockMvc mvc;

    private static Clock at(LocalDateTime local) {
        return Clock.fixed(local.atZone(ZONE).toInstant(), ZONE);
    }

    private static String reply(LocalDateTime local) {
        return new SystemTimeService(at(local)).now().reply();
    }

    @Test void CODATE01_4000_PROCESS_REQUEST_REPLY_frozen_clock_exact_1000_byte_reply() {
        String reply = reply(LocalDateTime.of(2022, 7, 5, 10, 0));
        assertThat(reply).isEqualTo(FROZEN_REPLY).hasSize(1000);
        assertThat(reply.getBytes(java.nio.charset.Charset.forName("IBM037"))).hasSize(1000);
    }

    @Test void CODATE01_FORMATTIME_MMDDYYYY_DATESEP_TIME_TIMESEP_zero_padding_24_hour_local_time() {
        assertThat(reply(LocalDateTime.of(2026, 1, 2, 3, 4, 5)))
                .startsWith("SYSTEM DATE : 01-02-2026SYSTEM TIME : 03:04:05 ").hasSize(1000);
        assertThat(reply(LocalDateTime.of(1999, 12, 31, 23, 59, 59, 999_999_999)))
                .startsWith("SYSTEM DATE : 12-31-1999SYSTEM TIME : 23:59:59 ");
        assertThat(reply(LocalDateTime.of(2024, 2, 29, 0, 0)))
                .startsWith("SYSTEM DATE : 02-29-2024SYSTEM TIME : 00:00:00 ");
    }

    @Test void CDRD_requires_sign_on() throws Exception {
        mvc.perform(get("/api/system-time")).andExpect(status().isUnauthorized());
    }

    @Test void CDRD_json_reply_ignores_request_input() throws Exception {
        mvc.perform(get("/api/system-time?function=INQA&key=00000000001").with(jwt())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tranId").value("CDRD"))
                .andExpect(jsonPath("$.program").value("CODATE01"))
                .andExpect(jsonPath("$.date").value("07-05-2022"))
                .andExpect(jsonPath("$.time").value("10:00:00"))
                .andExpect(jsonPath("$.reply").value(FROZEN_REPLY));
    }

    @Test void CODATE01_4100_PUT_REPLY_plain_text_is_the_1000_byte_buffer() throws Exception {
        byte[] body = mvc.perform(get("/api/system-time").with(jwt()).accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(body).hasSize(1000);
        assertThat(new String(body, StandardCharsets.US_ASCII)).isEqualTo(FROZEN_REPLY);
    }

    @Test void CODATE01_4100_PUT_REPLY_stays_1000_bytes_whatever_charset_is_requested() throws Exception {
        var response = mvc.perform(get("/api/system-time").with(jwt())
                        .accept(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_16)))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        assertThat(response.getContentType()).isEqualTo("text/plain;charset=US-ASCII");
        assertThat(response.getContentAsByteArray()).hasSize(1000);
    }
}
