package com.ticket2h.identity.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Tài khoản. Không bao giờ trả thẳng entity này ra API (có {@code passwordHash}): luôn đổi sang DTO. */
@Entity
@Table(name = "users")
public class User extends AssignedIdEntity {

    private String email;
    private String passwordHash;
    private String fullName;

    @Enumerated(EnumType.STRING)
    private Role role;

    private UUID organizerId;

    @Column(updatable = false)
    private Instant createdAt;

    protected User() {}

    public User(UUID id, String email, String passwordHash, String fullName, Role role, UUID organizerId,
            Instant now) {
        super(id);
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.organizerId = organizerId;
        this.createdAt = now;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    public UUID getOrganizerId() {
        return organizerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
