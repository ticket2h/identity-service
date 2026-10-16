# Ticket2h identity-service

Tài khoản, ban tổ chức, đăng ký, đăng nhập, phát JWT RS256 và công bố khoá công khai (JWKS). Spring Boot 4, Java 25,
PostgreSQL. Một trong các repo của hệ thống (cạnh `../infra`, `../core-service`, `../notification-service`); kiến trúc
và hợp đồng giữa các service: `../infra/docs/`.

## Lệnh
- Build và chạy toàn bộ test: `./mvnw verify` (cần Docker cho Testcontainers)
- Chạy với tài khoản mẫu: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` (cổng 8082; cần `../infra` đang chạy)

## Quy ước bắt buộc
- Không log mật khẩu, hash mật khẩu, access token, khoá riêng JWT, header `Authorization` hay `X-Internal-Api-Key`.
  Thông báo lỗi khi đọc khoá không được chứa nội dung khoá.
- Không trả entity `User` ra API: luôn đổi sang `UserResponse` (không có `passwordHash`).
- Claim trong JWT (`iss`, `sub`, `role`, `organizerId`), thuật toán RS256, `kid`, đường dẫn JWKS và API `/internal/**`
  là hợp đồng với service khác (`../infra/docs/contracts.md`): đổi thì sửa tài liệu và các service dùng nó cùng lúc.
- Id trong `dev/DevAccounts` được core-service dùng cho dữ liệu mẫu: không đổi nếu chưa sửa bên core.
- Email chuẩn hoá (`Emails.normalize`) trước khi lưu và trước khi tìm.
- Thời gian: inject `Clock`.
- Exception nghiệp vụ kế thừa `BusinessException`, có `ErrorCode`. Lỗi API là ProblemDetail có trường `code`.
- Migration Flyway: không sửa file đã merge, chỉ thêm file mới. Đặt tên cho mọi constraint.
- `Dockerfile`, `.github/workflows/ci.yml` giống nhau ở cả 4 repo service (ADR-014 trong `../infra/docs/adr/`): sửa một thì sửa cả bốn.
  Thêm biến môi trường mới thì thêm vào `.env.example` ở đây và `../infra` (`.env.example`, `scripts/init-env.sh`, `docker-compose.yml`).

## Cấm
- Không sửa hoặc xoá test trong `src/test/**/acceptance/` nếu chưa hỏi. Test đỏ thì sửa code, không sửa test.
- Không thêm dependency mới nếu chưa hỏi.
- Không đọc database của service khác; không cho service khác đọc database này.

## Cấu trúc
Package theo tầng dưới `com.ticket2h.identity`: config, controller, dto/request, dto/response, entity, repository,
service, security, exception, dev. Test: `support/` (IntegrationTest, TestData, TestKeys, MutableClock, Api,
TestcontainersConfig).
