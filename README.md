# Ticket2h identity-service

[![CI](https://github.com/ticket2h/identity-service/actions/workflows/ci.yml/badge.svg)](https://github.com/ticket2h/identity-service/actions/workflows/ci.yml)

Tài khoản, ban tổ chức, đăng ký, đăng nhập, phát access token (JWT). Là microservice thứ hai tách khỏi lõi
(core-service). Spring Boot 4.1, Java 25, PostgreSQL 17, Flyway. Cổng 8082.

Token ký **RS256** bằng khoá riêng chỉ service này giữ. Service khác (core-service...) kiểm tra token bằng khoá công
khai lấy ở `GET /.well-known/jwks.json`, không cần chia sẻ secret. Hợp đồng với các service khác (claim trong JWT,
API nội bộ): [infra/docs/contracts.md](https://github.com/ticket2h/infra/blob/main/docs/contracts.md).

## API

| Method | Đường dẫn | Ai gọi | Ghi chú |
|---|---|---|---|
| POST | `/auth/register` | Mọi người | `{email, password, fullName}` → 201 tài khoản CUSTOMER. Email lưu chữ thường; trùng → 409 `EMAIL_ALREADY_REGISTERED` |
| POST | `/auth/login` | Mọi người | `{email, password}` → `{accessToken, tokenType: "Bearer", expiresIn, user}`. Sai email hay sai mật khẩu đều 401 `INVALID_CREDENTIALS`, cùng thông báo |
| POST | `/auth/refresh` | Trình duyệt (cookie) | Đổi refresh token trong cookie `ticket2h_refresh` lấy body giống `/auth/login` và cookie mới. Token sai, hết hạn, đã thu hồi → 401 `UNAUTHENTICATED` và xoá cookie |
| POST | `/auth/logout` | Trình duyệt (cookie) | Thu hồi refresh token (cả family), xoá cookie → 204 |
| GET | `/me` | Đã đăng nhập | Tài khoản của người gọi |
| POST | `/admin/organizers` | ADMIN | `{name}` → 201 `{id, name}` |
| GET | `/admin/organizers` | ADMIN | Mảng `[{id, name}]`, theo tên không phân biệt hoa thường. Không phân trang |
| POST | `/admin/users` | ADMIN | `{email, password, fullName, role, organizerId?}`; ORGANIZER cần `organizerId` có thật, vai trò khác không được có |
| GET | `/admin/users?role=&q=&limit=&cursor=` | ADMIN | `{items: [tài khoản], limit, nextCursor}`, mới tạo trước, phân trang keyset. Chi tiết bên dưới |
| GET | `/v3/api-docs`, `/swagger-ui.html` | Mọi người | Mô tả API (OpenAPI 3, springdoc). Web sinh kiểu TypeScript từ đây |
| GET | `/.well-known/jwks.json` | Mọi người (service khác) | Khoá công khai (JWKS), `Cache-Control: public, max-age=300` |
| GET | `/internal/users/{userId}` | notification-service | Header `X-Internal-Api-Key`. Không có → 404. Email, tên người nhận |
| POST | `/internal/users/batch` | core-service | `{ids: [...]}` tối đa 1.000 id → `{users: [...]}`; id không có tài khoản thì không có trong kết quả (không 404). Nhân viên soát vé, người mua trong danh sách đơn và file danh sách khách |
| POST | `/internal/users/lookup` | core-service | `{email}` (chuẩn hoá hoa thường, khoảng trắng). Không có → 404; email sai dạng → 400 |

Tài khoản trả ra (công khai lẫn nội bộ) có đúng các trường `id, email, fullName, role, organizerId`
(`organizerId` là `null` trừ ORGANIZER). API nội bộ trả `Cache-Control: no-store`; JWT của người dùng, kể cả ADMIN,
không mở được `/internal/**`. Tìm theo email dùng POST để email không lọt vào URL hay access log.

Lỗi trả theo RFC 9457 (ProblemDetail) kèm trường `code`, cùng bộ mã với core-service.

Người không phải ADMIN gọi `/admin/**` → 403 `FORBIDDEN`; không có token → 401 `UNAUTHENTICATED`.

### `GET /admin/users`

Màn hình quản trị của web-app. Phân trang **keyset** (M4, ADR-018 ở core-service): với 2 triệu tài khoản, `OFFSET`
ở trang sâu phải đọc rồi bỏ qua mọi dòng phía trước, keyset thì nhảy thẳng tới đúng chỗ trong index.

```json
{ "items": [ { "id": "uuid", "email": "staff1@test.local", "fullName": "Nhân viên 1",
               "role": "STAFF", "organizerId": null } ],
  "limit": 20, "nextCursor": "MjAyNi0xMC0yMVQwNzozMDowMFp8..." }
```

| Tham số | Mặc định | Ý nghĩa |
|---|---|---|
| `role` | (mọi vai trò) | `CUSTOMER`, `ORGANIZER`, `STAFF`, `ADMIN`, viết hoa đúng như vậy. Giá trị khác → 400 `VALIDATION_FAILED`; `role=` rỗng coi như không lọc |
| `q` | (không lọc) | Bỏ khoảng trắng hai đầu, tìm chuỗi con trong email **hoặc** họ tên, không phân biệt hoa thường (cả chữ có dấu). `%`, `_` là ký tự thường. Từ 3 ký tự thì dùng index trigram (`pg_trgm`) |
| `cursor` | (trang đầu) | `nextCursor` của trang trước, gửi lại nguyên văn. Chuỗi hỏng → 400 `VALIDATION_FAILED` |
| `limit` | `20` | Nhỏ hơn 1 → 400; lớn hơn 100 thì dùng 100. `limit` trong kết quả là số đã dùng |

Sắp theo `created_at` giảm dần rồi `id` giảm dần (`id` phá hoà khi nhiều tài khoản tạo cùng một micro giây).
`nextCursor` là `null` ở trang cuối. Không có tổng số tài khoản: đếm 2 triệu dòng ở mỗi trang tốn hơn chính việc lấy
trang. Tài khoản tạo thêm trong lúc đang xem không làm trang sau bị lặp hay sót (OFFSET thì có). Câu SQL ở
`UserSearchImpl` (phần tự viết của `UserRepository`), index ở `V3__m4_admin_user_list.sql`.
`q` nằm trên URL nên có thể lọt vào access log của gateway: chấp nhận được vì chỉ ADMIN gọi và chỉ là đoạn tìm kiếm;
tìm đúng một email (dữ liệu cá nhân) thì service khác vẫn dùng `POST /internal/users/lookup`.

## Refresh token

`/auth/login` vẫn trả access token trong body như trước (curl, `demo.sh` không đổi), và đặt thêm cookie
`ticket2h_refresh`: `HttpOnly; Secure; SameSite=Strict; Path=/auth`, sống `ticket2h.refresh-token.ttl` (mặc định 14 ngày).
Web giữ access token trong bộ nhớ, hết hạn thì gọi `/auth/refresh`.

- Bảng `refresh_tokens` chỉ lưu SHA-256 của token. Khoá dòng (`FOR UPDATE`) khi đổi, nên hai request dùng cùng token
  chạy lần lượt.
- **Xoay vòng**: mỗi lần `/auth/refresh`, token cũ bị thu hồi, token mới cùng `family_id`. Token đã thu hồi mà còn bị
  gửi lại nghĩa là có người giữ bản sao: thu hồi cả family (mọi thiết bị khác không bị ảnh hưởng vì mỗi lần đăng nhập là
  một family riêng). Vì vậy web phải gọi refresh **tuần tự giữa các tab** (Web Locks API).
- Gọi qua gateway (nginx của repo web-app), cookie đi ở `/api/identity/auth`: gateway đổi `Path` bằng `proxy_cookie_path`,
  service không cần biết. Gọi thẳng cổng 8082 thì giữ `/auth`.
- `SameSite=Strict` chặn trang khác gửi kèm cookie, nên CSRF vẫn tắt.
- Chưa có job dọn token hết hạn (bảng chỉ lớn dần theo số lần đăng nhập/refresh): làm cùng rate limit ở M5.

| Cấu hình | Mặc định | Ý nghĩa |
|---|---|---|
| `ticket2h.refresh-token.ttl` | `P14D` | Thời hạn mỗi refresh token (tính lại sau mỗi lần xoay) |
| `ticket2h.refresh-token.cookie-path` | `/auth` | `Path` của cookie |
| `ticket2h.refresh-token.cookie-secure` | `true` | Thuộc tính `Secure`; trình duyệt vẫn nhận trên `http://localhost` |

## JWT

| Phần | Giá trị |
|---|---|
| Header | `alg: RS256`, `kid` = `ticket2h.jwt.key-id` (mặc định `k1`) |
| `iss` | `ticket2h` (chuỗi, không phải URL: bên kiểm tra đọc bằng `getClaimAsString("iss")`, đừng gọi `Jwt.getIssuer()`) |
| `sub` | id tài khoản (UUID) |
| `iat`, `exp` | Thời hạn `ticket2h.jwt.ttl` (mặc định 1 giờ) |
| `role` | `CUSTOMER`, `ORGANIZER`, `STAFF`, `ADMIN` |
| `organizerId` | Chỉ có với ORGANIZER: id ban tổ chức (UUID dạng chuỗi) |

## Cấu trúc

Service nhỏ, nên package chia theo **tầng kỹ thuật**:

```
src/main/java/com/ticket2h/identity/
├── IdentityServiceApplication.java
├── config/       IdentityProperties, JwtConfig (khoá RSA, JWKS, encoder/decoder), SecurityConfig, PasswordConfig, ClockConfig
├── controller/   AuthController, AdminController, InternalUserController, JwksController
├── dto/request/  RegisterRequest, LoginRequest, CreateUserRequest, CreateOrganizerRequest, UserLookupRequest,
│                 UserBatchRequest
├── dto/response/ UserResponse, UserPageResponse, UserBatchResponse, LoginResponse, OrganizerResponse
├── entity/       User, Organizer, Role, AssignedIdEntity
├── repository/   UserRepository (+ UserSearch/UserSearchImpl: danh sách keyset), OrganizerRepository, Constraints
├── service/      AccountService (đăng ký, đăng nhập, tạo tài khoản), TokenService (phát JWT), Emails, UserCursor
├── security/     InternalApiKeyFilter, AuthUser, AuthUserArgumentResolver, JwtClaims
├── exception/    ErrorCode, BusinessException..., ProblemDetails, ApiExceptionHandler
└── dev/          DevAccounts (id cố định), DevDataSeeder (profile dev), DataGenerator + DatagenIds (profile datagen)
src/main/resources/db/migration/V1__identity.sql   organizers, users
src/main/resources/db/migration/V2__refresh_tokens.sql   refresh_tokens
src/main/resources/db/migration/V3__m4_admin_user_list.sql   index keyset (created_at, id) và trigram cho tìm kiếm
```

## Chạy

Cần PostgreSQL có database `identity` và các biến ở [`.env.example`](.env.example). Cách nhanh nhất: clone repo
[infra](https://github.com/ticket2h/infra) cạnh repo này (hướng dẫn từ máy trống: [infra/docs/chay-tren-may-moi.md](https://github.com/ticket2h/infra/blob/main/docs/chay-tren-may-moi.md)):

```bash
(cd ../infra && scripts/init-env.sh && docker compose up -d)    # hạ tầng và secret dùng chung, làm một lần
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev           # :8082, profile dev tạo tài khoản mẫu
```

Service đọc secret từ `../infra/.env` nếu repo [infra](https://github.com/ticket2h/infra) nằm cạnh repo này, từ `.env` của chính repo
(mẫu: [`.env.example`](.env.example), chỉ gồm biến service này cần), hoặc từ biến môi trường của máy.

Image Docker: `ghcr.io/ticket2h/identity-service` (CI build mỗi lần push lên `main`). Build tại máy: `docker build -t identity-service .`
(không cần JDK). Chạy cả hệ thống từ image: `docker compose --profile apps up -d` trong repo infra.

| Cấu hình | Mặc định | Ý nghĩa |
|---|---|---|
| `spring.datasource.*` | `localhost:5432/identity`, user `identity` | Database riêng |
| `ticket2h.jwt.private-key` | `${JWT_PRIVATE_KEY}` | Khoá riêng RSA ≥ 2048 bit, PKCS#8 DER, base64 một dòng. Khoá công khai tự tính ra |
| `ticket2h.jwt.key-id` | `k1` | `kid` trong header token và trong JWKS |
| `ticket2h.jwt.issuer`, `ttl` | `ticket2h`, `PT1H` | |
| `ticket2h.internal-api.clients.core-service` | `${INTERNAL_API_KEY_CORE}` | Khoá của core-service khi gọi `/internal/**` |
| `ticket2h.internal-api.clients.notification-service` | `${INTERNAL_API_KEY_NOTIFICATION}` | Khoá của notification-service |

Ba biến môi trường trên được liệt kê trong [`.env.example`](.env.example).

### Tài khoản mẫu (profile dev, mật khẩu `Passw0rd!`)

Id cố định, vì core-service tạo sự kiện mẫu cho đúng ban tổ chức và nhân viên này (`DevAccounts`):

| Email | Vai trò | Id |
|---|---|---|
| (ban tổ chức "Sky Music Entertainment") | | `0a000000-0000-4000-8000-000000000001` |
| `admin@test.local` | ADMIN | `0b000000-0000-4000-8000-000000000001` |
| `organizer1@test.local` | ORGANIZER (Sky Music) | `0b000000-0000-4000-8000-000000000002` |
| `staff1@test.local` | STAFF | `0b000000-0000-4000-8000-000000000003` |
| `buyer1..3@test.local` | CUSTOMER | `0b000000-0000-4000-8000-000000000004` … `…006` |

### Dữ liệu lớn (M4, profile datagen)

`DataGenerator` sinh 200 ban tổ chức (kèm tài khoản `btc{i}@datagen.ticket2h.local`), 600 nhân viên soát vé
(`soatve{i}@…`) và 2 triệu khách (`khach{i}@…`) đăng ký dần trong 2 năm, cùng mật khẩu `Passw0rd!` (chung một hash
BCrypt: băm 2 triệu lần mất hàng giờ). Nạp bằng `COPY` trong một transaction, khoảng 40 giây. Id theo `DatagenIds`,
hợp đồng với công cụ sinh của core-service ([infra/docs/contracts.md](https://github.com/ticket2h/infra/blob/main/docs/contracts.md), mục 6). Cả hệ
thống: `../infra/scripts/datagen.sh`. Chạy riêng:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,datagen -Dspring-boot.run.arguments=--ticket2h.datagen.customers=200000
```

Chạy xong thì app tự tắt; database đã có tài khoản sinh thì không sinh chồng.

## Test

```bash
./mvnw verify      # 5 unit test + 45 integration test
```

Test chạy PostgreSQL thật (Testcontainers, cần Docker). Khoá RSA sinh lúc chạy test, không commit khoá nào.

| Test | Kiểm tra |
|---|---|
| `RefreshTokenApiIT` | Cookie httpOnly/Strict; xoay vòng; dùng lại token cũ thu hồi cả family nhưng không đụng lần đăng nhập khác; hết hạn, thiếu cookie 401; đăng xuất; `/v3/api-docs` công khai |
| `AuthApiIT` | Đăng ký → đăng nhập → `/me`; cùng 401 cho sai mật khẩu và email lạ; trùng email 409; token thiếu/hết hạn 401; token cũ không chặn đăng nhập; chỉ ADMIN tạo được STAFF |
| `JwtAndJwksIT` | Token RS256 có `kid`; claim; ORGANIZER có `organizerId`; token kiểm tra được chỉ bằng JWKS (như core-service); JWKS không lộ khoá riêng; token ký bằng khoá khác (cùng `kid`) hay HS256 bị từ chối |
| `InternalUserApiIT` | Thiếu/sai khoá 401; JWT ADMIN không mở được; cả hai khoá client dùng được; đúng tập trường; tìm theo email chuẩn hoá; 404, 400 |
| `AdminApiIT` | Tạo ban tổ chức rồi tài khoản ORGANIZER; thiếu hoặc sai `organizerId` 400; vai trò khác không gắn ban tổ chức; người không phải ADMIN 403 |
| `AdminListApiIT` | Danh sách ban tổ chức theo tên (không phân biệt hoa thường); tài khoản mới trước, đúng dạng trang, không lộ `passwordHash`; lọc `role`; `q` theo email hoặc tên có dấu, không phân biệt hoa thường; trang không trùng, `total` đúng; `size` > 100 thành 100; `role`/`page`/`size` sai 400; vai trò khác 403, chưa đăng nhập 401 |
| `DevDataSeederIT` | Tài khoản mẫu có đúng id cố định |
| `JwtConfigTest` | Khoá sai base64, không phải RSA, dưới 2048 bit bị từ chối với thông báo không chứa khoá |
