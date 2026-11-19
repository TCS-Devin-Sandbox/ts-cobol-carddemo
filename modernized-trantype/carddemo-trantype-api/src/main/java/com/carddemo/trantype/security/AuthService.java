package com.carddemo.trantype.security;

import com.carddemo.trantype.Messages;
import com.carddemo.trantype.validation.FieldEditException;
import com.carddemo.trantype.web.ApiException;
import com.carddemo.trantype.web.ErrorCode;
import com.carddemo.trantype.web.dto.LoginResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** Sign-on stand-in for COSGN00C: user id and password are upper-cased before comparison. */
@Service
public class AuthService {

    private final SecurityProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final Clock clock;

    public AuthService(SecurityProperties properties, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder) {
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.clock = Clock.systemUTC();
    }

    public LoginResponse login(String userId, String password) {
        if (userId == null || userId.isBlank()) {
            throw new FieldEditException(Messages.ENTER_USER_ID);
        }
        if (password == null || password.isBlank()) {
            throw new FieldEditException(Messages.ENTER_PASSWORD);
        }
        String id = userId.trim().toUpperCase(Locale.ROOT);
        SecurityProperties.User user = properties.users().stream()
                .filter(u -> u.id().equalsIgnoreCase(id))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED,
                        Messages.USER_NOT_FOUND));
        if (!passwordEncoder.matches(password.trim().toUpperCase(Locale.ROOT), user.password())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, Messages.WRONG_PASSWORD);
        }
        Instant now = clock.instant();
        Duration ttl = properties.tokenTtl() == null ? Duration.ofHours(1) : properties.tokenTtl();
        Instant expiresAt = now.plus(ttl);
        String userType = user.isAdmin() ? "A" : "U";
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("carddemo-trantype-api")
                .subject(id)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(SecurityConfig.ROLES_CLAIM, List.of(user.isAdmin() ? "ADMIN" : "USER"))
                .claim("userType", userType)
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new LoginResponse(token, id, userType, expiresAt);
    }
}
