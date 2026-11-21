package com.carddemo.trantype.security;

import com.carddemo.trantype.web.ErrorResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserSecurity users;
    private final JwtEncoder encoder;
    private final SecurityProperties props;

    public AuthController(UserSecurity users, JwtEncoder encoder, SecurityProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
    }

    public record LoginRequest(String userId, String password) {
    }

    public record LoginResponse(String token, String userId, String userType, List<String> roles, String nextScreen) {
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        if (request.userId() == null || request.userId().isBlank())
            return error(400, "Please enter User ID ...");
        if (request.password() == null || request.password().isBlank())
            return error(400, "Please enter Password ...");
        UserSecurity.User user;
        try {
            var found = users.find(request.userId().stripTrailing().toUpperCase(java.util.Locale.ROOT));
            if (found.isEmpty()) return error(401, "User not found. Try again ...");
            user = found.get();
            if (!users.matches(request.password(), user)) return error(401, "Wrong Password. Try again ...");
        } catch (org.springframework.dao.DataAccessException e) {
            return error(500, "Unable to verify the User ...");
        }
        List<String> roles = List.of("A".equals(user.userType()) ? "ADMIN" : "USER");
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("carddemo-online-api")
                .subject(user.userId())
                .claim("user_type", user.userType())
                .claim("from_tranid", "CC00")
                .claim("from_program", "COSGN00C")
                .issuedAt(now)
                .expiresAt(now.plus(props.tokenTtlMinutes(), ChronoUnit.MINUTES))
                .claim(SecurityConfig.ROLES_CLAIM, roles)
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return ResponseEntity.ok(new LoginResponse(token, user.userId(), user.userType(), roles,
                "A".equals(user.userType()) ? "CA00" : "CM00"));
    }

    private ResponseEntity<?> error(int status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse("SIGNON", message));
    }
}
