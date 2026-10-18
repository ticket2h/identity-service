package com.ticket2h.identity.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Một refresh token. Chỉ lưu {@code tokenHash} (SHA-256), token gốc chỉ nằm trong cookie của trình duyệt.
 * Các token sinh ra từ cùng một lần đăng nhập có chung {@code familyId}.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends AssignedIdEntity {

    private UUID userId;
    private UUID familyId;
    private String tokenHash;

    @Column(updatable = false)
    private Instant createdAt;

    private Instant expiresAt;
    private Instant revokedAt;

    protected RefreshToken() {}

    public RefreshToken(UUID id, UUID userId, UUID familyId, String tokenHash, Instant now, Instant expiresAt) {
        super(id);
        this.userId = userId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public void revoke(Instant now) {
        this.revokedAt = now;
    }
}
