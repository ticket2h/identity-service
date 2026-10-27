package com.ticket2h.identity.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ticket2h.identity.repository.AuditLogRepository.AuditRow;

/**
 * Một trang lịch sử thao tác (ADR-023). {@code nextCursor} null: trang cuối. {@code actor.type}: USER (có {@code id},
 * {@code role}, {@code email}, {@code fullName} nếu tài khoản còn) hoặc SYSTEM (có {@code name}).
 * {@code targetEmail}: email của tài khoản bị tác động, nếu đối tượng là tài khoản và còn tồn tại.
 */
public record AuditLogPageResponse(List<Item> items, int limit, String nextCursor) {

    public record Item(UUID id, Instant occurredAt, Actor actor, String action, String targetType, UUID targetId,
            String targetEmail, Map<String, Object> details) {

        public static Item of(AuditRow row) {
            return new Item(row.id(), row.occurredAt(), new Actor(row.actorType(), row.actorId(), row.actorRole(),
                    row.actorName(), row.actorEmail(), row.actorFullName()), row.action(), row.targetType(),
                    row.targetId(), row.targetEmail(), row.details());
        }
    }

    public record Actor(String type, UUID id, String role, String name, String email, String fullName) {}
}
