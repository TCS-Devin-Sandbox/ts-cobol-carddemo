package com.carddemo.trantype.security;

import com.carddemo.trantype.service.ErrorCode;
import com.carddemo.trantype.service.SourceMessages;
import com.carddemo.trantype.web.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/** Session-based security. ROLE_ADMIN corresponds to CardDemo user type 'A' (CDEMO-USRTYP-ADMIN). */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/transaction-types/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .securityContext(ctx -> ctx.securityContextRepository(securityContextRepository()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> write(mapper, res, HttpStatus.UNAUTHORIZED,
                                ErrorCode.UNAUTHORIZED, SourceMessages.SIGN_ON_REQUIRED))
                        .accessDeniedHandler((req, res, e) -> write(mapper, res, HttpStatus.FORBIDDEN,
                                ErrorCode.FORBIDDEN,
                                e instanceof CsrfException ? "Invalid or missing CSRF token" : SourceMessages.ADMIN_ONLY)));
        return http.build();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(PasswordEncoder encoder,
                                          @Value("${carddemo.security.admin-username}") String adminUser,
                                          @Value("${carddemo.security.admin-password}") String adminPassword,
                                          @Value("${carddemo.security.user-username}") String regularUser,
                                          @Value("${carddemo.security.user-password}") String regularPassword) {
        requirePassword("carddemo.security.admin-password (CARDDEMO_ADMIN_PASSWORD)", adminPassword);
        requirePassword("carddemo.security.user-password (CARDDEMO_USER_PASSWORD)", regularPassword);
        return new InMemoryUserDetailsManager(
                User.withUsername(adminUser).password(encoder.encode(adminPassword)).roles("ADMIN").build(),
                User.withUsername(regularUser).password(encoder.encode(regularPassword)).roles("USER").build());
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(encoder);
        provider.setUserDetailsService(users);
        return new ProviderManager(provider);
    }

    static void requirePassword(String property, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " must be set, or run with the 'local' profile for development defaults");
        }
    }

    private static void write(ObjectMapper mapper, HttpServletResponse res, HttpStatus status, ErrorCode code,
                              String message) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), new ErrorResponse(code, message, null));
    }
}
