package com.ticket2h.identity.service;

import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticket2h.identity.entity.Organizer;
import com.ticket2h.identity.entity.Role;
import com.ticket2h.identity.entity.User;
import com.ticket2h.identity.exception.BusinessException;
import com.ticket2h.identity.exception.ErrorCode;
import com.ticket2h.identity.exception.ValidationException;
import com.ticket2h.identity.repository.Constraints;
import com.ticket2h.identity.repository.OrganizerRepository;
import com.ticket2h.identity.repository.UserRepository;

/** Tài khoản, ban tổ chức, đăng nhập. Không ghi log mật khẩu hay token. */
@Service
public class AccountService {

    private final UserRepository users;
    private final OrganizerRepository organizers;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final Clock clock;

    /** So khớp với hash giả khi email không tồn tại, để thời gian trả lời không lộ email nào đã đăng ký. */
    private final String dummyHash;

    AccountService(UserRepository users, OrganizerRepository organizers, PasswordEncoder passwordEncoder,
            TokenService tokens, Clock clock) {
        this.users = users;
        this.organizers = organizers;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    /** Khách tự đăng ký: luôn là CUSTOMER. */
    @Transactional
    public User register(String email, String password, String fullName) {
        return createUser(UUID.randomUUID(), email, password, fullName, Role.CUSTOMER, null);
    }

    /** Admin tạo tài khoản với vai trò bất kỳ. ORGANIZER phải gắn với một ban tổ chức. */
    @Transactional
    public User createUser(String email, String password, String fullName, Role role, UUID organizerId) {
        return createUser(UUID.randomUUID(), email, password, fullName, role, organizerId);
    }

    /** Như trên nhưng tự chọn id. Dùng cho dữ liệu mẫu có id cố định (DevAccounts). */
    @Transactional
    public User createUser(UUID id, String email, String password, String fullName, Role role, UUID organizerId) {
        if (role == Role.ORGANIZER && organizerId == null) {
            throw new ValidationException("Tài khoản ORGANIZER cần organizerId");
        }
        if (role != Role.ORGANIZER && organizerId != null) {
            throw new ValidationException("Chỉ tài khoản ORGANIZER mới gắn với ban tổ chức");
        }
        if (organizerId != null && !organizers.existsById(organizerId)) {
            throw new ValidationException("Ban tổ chức không tồn tại");
        }
        String normalized = Emails.normalize(email);
        if (users.existsByEmail(normalized)) {
            throw emailTaken();
        }
        User user = new User(id, normalized, passwordEncoder.encode(password), fullName.trim(), role, organizerId,
                clock.instant());
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Hai request đăng ký cùng email chạy song song: ràng buộc uq_users_email chặn request thứ hai
            if (Constraints.isUniqueViolation(e, "uq_users_email")) {
                throw emailTaken();
            }
            throw e;
        }
    }

    @Transactional
    public Organizer createOrganizer(String name) {
        return createOrganizer(UUID.randomUUID(), name);
    }

    /** Như trên nhưng tự chọn id. Dùng cho dữ liệu mẫu có id cố định (DevAccounts). */
    @Transactional
    public Organizer createOrganizer(UUID id, String name) {
        return organizers.saveAndFlush(new Organizer(id, name.trim(), clock.instant()));
    }

    /** Mọi ban tổ chức, theo tên (không phân biệt hoa thường). Số ban tổ chức nhỏ nên không phân trang. */
    @Transactional(readOnly = true)
    public List<Organizer> listOrganizers() {
        return organizers.findAllOrderByName();
    }

    /**
     * Một trang tài khoản cho trang quản trị, mới tạo trước, phân trang keyset. {@code role} null: mọi vai trò.
     * {@code query} (bỏ khoảng trắng hai đầu; trống thì bỏ qua): chuỗi con của email hoặc họ tên, không phân biệt hoa
     * thường; {@code %} và {@code _} trong đó là ký tự thường, không phải ký tự đại diện. {@code cursor} null: trang
     * đầu; còn lại là {@code nextCursor} của trang trước. {@code nextCursor} của kết quả là null khi hết danh sách.
     */
    @Transactional(readOnly = true)
    public UserSlice searchUsers(Role role, String query, String cursor, int limit) {
        UserCursor after = cursor == null || cursor.isBlank() ? null : UserCursor.decode(cursor);
        // Lấy thừa một dòng để biết còn trang sau hay không, khỏi phải đếm tổng
        List<User> found = users.searchPage(role, containsPattern(query), after == null ? null : after.createdAt(),
                after == null ? null : after.id(), limit + 1);
        if (found.size() <= limit) {
            return new UserSlice(found, null);
        }
        List<User> page = found.subList(0, limit);
        User last = page.getLast();
        return new UserSlice(List.copyOf(page), new UserCursor(last.getCreatedAt(), last.getId()).encode());
    }

    @Transactional(readOnly = true)
    public LoginResult login(String email, String password) {
        User user = users.findByEmail(Emails.normalize(email)).orElse(null);
        if (user == null) {
            passwordEncoder.matches(password, dummyHash);
            throw invalidCredentials();
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw invalidCredentials();
        }
        TokenService.IssuedToken token = tokens.issue(user);
        return new LoginResult(token.value(), token.expiresInSeconds(), user);
    }

    /** Tài khoản của người đang đăng nhập (GET /me). Token còn hạn nhưng tài khoản đã bị xoá thì coi như hết đăng nhập. */
    @Transactional(readOnly = true)
    public User get(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new BusinessException(
                ErrorCode.UNAUTHENTICATED, "Tài khoản không còn tồn tại"));
    }

    @Transactional(readOnly = true)
    public Optional<User> find(UUID userId) {
        return users.findById(userId);
    }

    /** Tìm theo email đã chuẩn hoá. Email sai dạng thì 400, để phân biệt với "không có tài khoản" (404). */
    /** Một câu {@code WHERE id IN (...)}; id trùng nhau trả một lần, id không có thì bỏ qua. */
    @Transactional(readOnly = true)
    public List<User> findAll(Collection<UUID> userIds) {
        return users.findAllById(new LinkedHashSet<>(userIds));
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        String normalized = Emails.normalize(email);
        if (!Emails.looksValid(normalized)) {
            throw new ValidationException("Email không hợp lệ");
        }
        return users.findByEmail(normalized);
    }

    @Transactional(readOnly = true)
    public boolean emailExists(String email) {
        return users.existsByEmail(Emails.normalize(email));
    }

    /** Mẫu LIKE "chứa chuỗi". Escape {@code \ % _} bằng {@code \}, khớp {@code ESCAPE} trong UserRepository. */
    private static String containsPattern(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String escaped = query.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static BusinessException invalidCredentials() {
        return new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Sai email hoặc mật khẩu");
    }

    private static BusinessException emailTaken() {
        return new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED, "Email này đã được đăng ký");
    }

    public record LoginResult(String accessToken, long expiresInSeconds, User user) {}

    /** {@code nextCursor} null: đây là trang cuối. */
    public record UserSlice(List<User> users, String nextCursor) {}
}
