package com.ticket2h.identity.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.ticket2h.identity.config.IdentityProperties;
import com.ticket2h.identity.entity.Role;
import com.ticket2h.identity.entity.User;
import com.ticket2h.identity.security.JwtClaims;

/**
 * Phát access token (JWT RS256). Tên và ý nghĩa từng claim là hợp đồng với mọi service kiểm tra token
 * (infra/docs/contracts.md): đổi ở đây thì core-service phải đổi theo.
 */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final IdentityProperties properties;
    private final Clock clock;

    TokenService(JwtEncoder encoder, IdentityProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedToken issue(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.jwt().ttl());
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(JwtClaims.ROLE, user.getRole().name());
        if (user.getRole() == Role.ORGANIZER) {
            // core-service đọc ban tổ chức thẳng từ token, không phải hỏi lại service này mỗi request
            claims.claim(JwtClaims.ORGANIZER_ID, user.getOrganizerId().toString());
        }
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(properties.jwt().keyId()).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new IssuedToken(token, properties.jwt().ttl().toSeconds());
    }

    public record IssuedToken(String value, long expiresInSeconds) {}
}
