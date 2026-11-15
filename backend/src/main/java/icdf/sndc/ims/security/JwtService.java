package icdf.sndc.ims.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import icdf.sndc.ims.config.AuthProperties;
import icdf.sndc.ims.user.User;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/** 簽發 HS256 JWT；驗證由 Spring Security Resource Server（SecurityConfig 的 JwtDecoder）負責。 */
@Service
public class JwtService {

    public static final String ISSUER = "sndc-ims";
    public static final String CLAIM_USERNAME = "username";

    private final AuthProperties props;

    public JwtService(AuthProperties props) {
        this.props = props;
    }

    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(props.jwtExpiration());
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(user.getId().toString())
                .claim(CLAIM_USERNAME, user.getUsername())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString())
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(new MACSigner(props.jwtSecretBytes()));
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
        return new IssuedToken(jwt.serialize(), expiresAt);
    }

    public record IssuedToken(String value, Instant expiresAt) {}
}
