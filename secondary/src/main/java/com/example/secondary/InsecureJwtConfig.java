package com.example.secondary;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * DEMO-ONLY escape hatch. Activated by {@code security.skip-signature=true}
 * (env {@code SECURITY_SKIP_SIGNATURE=true}).
 *
 * <p>Some local AWS emulators (e.g. robotocore) issue tokens with the correct
 * claim shape but a <em>placeholder signature</em>. A real resource server
 * rightly rejects those. This decoder parses the JWT and checks expiry, but
 * deliberately <em>does not verify the signature</em>, so you can exercise the
 * request <em>flow</em> end to end and still see scope-based authorization work
 * (a token lacking {@code secondary/read} still gets a 403).
 *
 * <p><strong>Never enable this against real tokens.</strong> It exists purely to
 * reach a 200 in the robotocore flow demo. See
 * docs/running/testing-with-robotocore.md.
 */
@Configuration
@ConditionalOnProperty(name = "security.skip-signature", havingValue = "true")
public class InsecureJwtConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Bean
    JwtDecoder jwtDecoder() {
        return InsecureJwtConfig::decodeWithoutSignatureCheck;
    }

    @SuppressWarnings("unchecked")
    private static Jwt decodeWithoutSignatureCheck(String token) throws JwtException {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new JwtException("Not a JWT: expected 3 dot-separated parts");
        }
        try {
            Base64.Decoder b64 = Base64.getUrlDecoder();
            Map<String, Object> headers = MAPPER.readValue(b64.decode(parts[0]), Map.class);
            Map<String, Object> claims = MAPPER.readValue(b64.decode(parts[1]), Map.class);

            Instant iat = claims.containsKey("iat")
                ? Instant.ofEpochSecond(((Number) claims.get("iat")).longValue()) : null;
            Instant exp = claims.containsKey("exp")
                ? Instant.ofEpochSecond(((Number) claims.get("exp")).longValue()) : null;

            // We skip the signature, but still refuse an expired token.
            if (exp != null && exp.isBefore(Instant.now())) {
                throw new JwtException("Token is expired");
            }

            return new Jwt(token, iat, exp, headers, claims);
        } catch (JwtException e) {
            throw e;
        } catch (Exception e) {
            throw new JwtException("Failed to parse JWT: " + e.getMessage(), e);
        }
    }
}
