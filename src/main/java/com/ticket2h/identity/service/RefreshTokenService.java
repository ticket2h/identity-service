package com.ticket2h.identity.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket2h.identity.config.IdentityProperties;
import com.ticket2h.identity.entity.RefreshToken;
import com.ticket2h.identity.entity.User;
import com.ticket2h.identity.repository.RefreshTokenRepository;
import com.ticket2h.identity.repository.UserRepository;

/**
 * Refresh token: chuỗi ngẫu nhiên 256 bit, chỉ lưu SHA-256. Mỗi lần dùng thì xoay vòng (thu hồi token cũ, phát token
 * mới cùng family). Token đã thu hồi mà bị gửi lại thì thu hồi cả family. Không ghi log token.
 */
@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository tokens;
    private final UserRepository users;
    private final IdentityProperties properties;
    private final Clock clock;

    RefreshTokenService(RefreshTokenRepository tokens, UserRepository users, IdentityProperties properties,
            Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.properties = properties;
        this.clock = clock;
    }

    /** Token đầu tiên của một lần đăng nhập (family mới). */
    @Transactional
    public String issue(User user) {
        return issue(user.getId(), UUID.randomUUID());
    }

    /**
     * Đổi token cũ lấy token mới. Rỗng nếu token không có, hết hạn, đã thu hồi hoặc tài khoản đã bị xoá. Không ném
     * exception để việc thu hồi cả family (khi token cũ bị dùng lại) vẫn được commit.
     */
    @Transactional
    public Optional<Rotated> rotate(String rawToken) {
        Optional<RefreshToken> found = tokens.findByTokenHashForUpdate(hash(rawToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        RefreshToken current = found.get();
        Instant now = clock.instant();
        if (current.isRevoked()) {
            tokens.revokeFamily(current.getFamilyId(), now);
            return Optional.empty();
        }
        current.revoke(now);
        if (!now.isBefore(current.getExpiresAt())) {
            return Optional.empty();
        }
        return users.findById(current.getUserId())
                .map(user -> new Rotated(issue(user.getId(), current.getFamilyId()), user));
    }

    /** Đăng xuất: thu hồi cả family của token. Token lạ thì bỏ qua. */
    @Transactional
    public void revoke(String rawToken) {
        tokens.findByTokenHashForUpdate(hash(rawToken))
                .ifPresent(token -> tokens.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    private String issue(UUID userId, UUID familyId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        tokens.save(new RefreshToken(UUID.randomUUID(), userId, familyId, hash(raw), now,
                now.plus(properties.refreshToken().ttl())));
        return raw;
    }

    private static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record Rotated(String refreshToken, User user) {}
}
