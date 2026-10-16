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
| GET | `/me` | Đã đăng nhập | Tài khoản của người gọi |
| POST | `/admin/organizers` | ADMIN | `{name}` → 201 `{id, name}` |
| POST | `/admin/users` | ADMIN | `{email, password, fullName, role, organizerId?}`; ORGANIZER cần `organizerId` có thật, vai trò khác không được có |
| GET | `/.well-known/jwks.json` | Mọi người (service khác) | Khoá công khai (JWKS), `Cache-Control: public, max-age=300` |
| GET | `/internal/users/{userId}` | core-service, notification-service | Header `X-Internal-Api-Key`. Không có → 404 |
| POST | `/internal/users/lookup` | core-service | `{email}` (chuẩn hoá hoa thường, khoảng trắng). Không có → 404; email sai dạng → 400 |

Tài khoản trả ra (công khai lẫn nội bộ) có đúng các trường `id, email, fullName, role, organizerId`
(`organizerId` là `null` trừ ORGANIZER). API nội bộ trả `Cache-Control: no-store`; JWT của người dùng, kể cả ADMIN,
không mở được `/internal/**`. Tìm theo email dùng POST để email không lọt vào URL hay access log.

Lỗi trả theo RFC 9457 (ProblemDetail) kèm trường `code`, cùng bộ mã với core-service.

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
├── dto/request/  RegisterRequest, LoginRequest, CreateUserRequest, CreateOrganizerRequest, UserLookupRequest
├── dto/response/ UserResponse, LoginResponse, OrganizerResponse
├── entity/       User, Organizer, Role, AssignedIdEntity
├── repository/   UserRepository, OrganizerRepository, Constraints
├── service/      AccountService (đăng ký, đăng nhập, tạo tài khoản), TokenService (phát JWT), Emails
├── security/     InternalApiKeyFilter, AuthUser, AuthUserArgumentResolver, JwtClaims
├── exception/    ErrorCode, BusinessException..., ProblemDetails, ApiExceptionHandler
└── dev/          DevAccounts (id cố định), DevDataSeeder (profile dev)
src/main/resources/db/migration/V1__identity.sql   organizers, users
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

## Test

```bash
./mvnw verify      # 4 unit test + 23 integration test
```

Test chạy PostgreSQL thật (Testcontainers, cần Docker). Khoá RSA sinh lúc chạy test, không commit khoá nào.

| Test | Kiểm tra |
|---|---|
| `AuthApiIT` | Đăng ký → đăng nhập → `/me`; cùng 401 cho sai mật khẩu và email lạ; trùng email 409; token thiếu/hết hạn 401; token cũ không chặn đăng nhập; chỉ ADMIN tạo được STAFF |
| `JwtAndJwksIT` | Token RS256 có `kid`; claim; ORGANIZER có `organizerId`; token kiểm tra được chỉ bằng JWKS (như core-service); JWKS không lộ khoá riêng; token ký bằng khoá khác (cùng `kid`) hay HS256 bị từ chối |
| `InternalUserApiIT` | Thiếu/sai khoá 401; JWT ADMIN không mở được; cả hai khoá client dùng được; đúng tập trường; tìm theo email chuẩn hoá; 404, 400 |
| `AdminApiIT` | Tạo ban tổ chức rồi tài khoản ORGANIZER; thiếu hoặc sai `organizerId` 400; vai trò khác không gắn ban tổ chức; người không phải ADMIN 403 |
| `DevDataSeederIT` | Tài khoản mẫu có đúng id cố định |
| `JwtConfigTest` | Khoá sai base64, không phải RSA, dưới 2048 bit bị từ chối với thông báo không chứa khoá |
