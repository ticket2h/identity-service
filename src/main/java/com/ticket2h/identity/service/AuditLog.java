package com.ticket2h.identity.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ticket2h.identity.repository.AuditLogRepository;
import com.ticket2h.identity.repository.AuditLogRepository.AuditRow;

/**
 * Ghi lịch sử thao tác (ADR-023 ở infra/docs/adr). {@code MANDATORY}: trong CÙNG transaction với thay đổi được
 * ghi lại. Không đưa email, mật khẩu, token vào {@code details}: lịch sử giữ lâu, trang quản trị tự tra email theo id.
 */
@Service
public class AuditLog {

    private final AuditLogRepository repository;
    private final Clock clock;

    AuditLog(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditAction action, UUID targetId, Map<String, Object> details) {
        repository.insert(AuditActor.current(), action, targetId, details, clock.instant());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditActor actor, AuditAction action, UUID targetId, Map<String, Object> details) {
        repository.insert(actor, action, targetId, details, clock.instant());
    }

    /** Một trang cho admin, mới nhất trước. {@code cursor} null: trang đầu; hỏng thì 400. */
    @Transactional(readOnly = true)
    public AuditSlice page(AuditAction action, String cursor, int limit) {
        KeysetCursor after = cursor == null || cursor.isBlank() ? null : KeysetCursor.decode(cursor);
        List<AuditRow> rows = repository.page(action, after == null ? null : after.at(),
                after == null ? null : after.id(), limit + 1);
        if (rows.size() <= limit) {
            return new AuditSlice(rows, null);
        }
        List<AuditRow> page = rows.subList(0, limit);
        AuditRow last = page.getLast();
        return new AuditSlice(List.copyOf(page), new KeysetCursor(last.occurredAt(), last.id()).encode());
    }

    /** {@code nextCursor} null: trang cuối. */
    public record AuditSlice(List<AuditRow> rows, String nextCursor) {}
}
