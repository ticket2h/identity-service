package com.ticket2h.identity.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Ban tổ chức. Sự kiện ở core-service lưu {@code organizer_id} này (không có khoá ngoại vì khác database). */
@Entity
@Table(name = "organizers")
public class Organizer extends AssignedIdEntity {

    private String name;

    @Column(updatable = false)
    private Instant createdAt;

    protected Organizer() {}

    public Organizer(UUID id, String name, Instant now) {
        super(id);
        this.name = name;
        this.createdAt = now;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
