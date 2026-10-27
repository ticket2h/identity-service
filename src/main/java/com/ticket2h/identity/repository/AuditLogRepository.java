package com.ticket2h.identity.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ticket2h.identity.service.AuditAction;
import com.ticket2h.identity.service.AuditActor;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Bảng {@code audit_logs} (chỉ ghi thêm). SQL bằng JDBC: không cần entity cho bảng chỉ ghi thêm và đọc theo trang. */
@Repository
public class AuditLogRepository {

    private static final TypeReference<Map<String, Object>> DETAILS = new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper json;

    AuditLogRepository(NamedParameterJdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void insert(AuditActor actor, AuditAction action, UUID targetId, Map<String, Object> details,
            Instant now) {
        jdbc.update("""
                INSERT INTO audit_logs (id, occurred_at, actor_type, actor_id, actor_role, actor_name, action,
                                        target_type, target_id, details)
                VALUES (:id, :at, :actorType, :actorId, :actorRole, :actorName, :action, :targetType, :targetId,
                        CAST(:details AS jsonb))
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("at", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .addValue("actorType", actor.type().name())
                .addValue("actorId", actor.id())
                .addValue("actorRole", actor.role())
                .addValue("actorName", actor.name())
                .addValue("action", action.name())
                .addValue("targetType", action.target().name())
                .addValue("targetId", targetId)
                .addValue("details", json.writeValueAsString(details)));
    }

    /**
     * Một trang, mới nhất trước, keyset {@code (occurred_at, id) < (:at, :id)}. Kèm email, họ tên của người làm và của
     * tài khoản bị tác động (nếu còn), tra ngay trong database này.
     */
    public List<AuditRow> page(AuditAction action, Instant afterAt, UUID afterId, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", limit);
        StringBuilder where = new StringBuilder("true");
        if (action != null) {
            where.append(" AND a.action = :action");
            params.addValue("action", action.name());
        }
        if (afterAt != null) {
            where.append(" AND (a.occurred_at, a.id) < (:afterAt, :afterId)");
            params.addValue("afterAt", OffsetDateTime.ofInstant(afterAt, ZoneOffset.UTC)).addValue("afterId", afterId);
        }
        return jdbc.query("""
                SELECT a.id, a.occurred_at, a.actor_type, a.actor_id, a.actor_role, a.actor_name, a.action,
                       a.target_type, a.target_id, a.details::text AS details,
                       actor.email AS actor_email, actor.full_name AS actor_full_name,
                       target.email AS target_email
                FROM audit_logs a
                LEFT JOIN users actor ON actor.id = a.actor_id
                LEFT JOIN users target ON a.target_type = 'USER' AND target.id = a.target_id
                WHERE %s
                ORDER BY a.occurred_at DESC, a.id DESC
                LIMIT :limit
                """.formatted(where), params, (rs, i) -> new AuditRow(rs.getObject("id", UUID.class),
                        rs.getObject("occurred_at", OffsetDateTime.class).toInstant(), rs.getString("actor_type"),
                        rs.getObject("actor_id", UUID.class), rs.getString("actor_role"), rs.getString("actor_name"),
                        rs.getString("actor_email"), rs.getString("actor_full_name"), rs.getString("action"),
                        rs.getString("target_type"), rs.getObject("target_id", UUID.class),
                        rs.getString("target_email"), json.readValue(rs.getString("details"), DETAILS)));
    }

    public record AuditRow(UUID id, Instant occurredAt, String actorType, UUID actorId, String actorRole,
            String actorName, String actorEmail, String actorFullName, String action, String targetType, UUID targetId,
            String targetEmail, Map<String, Object> details) {}
}
