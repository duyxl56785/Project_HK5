# Hướng dẫn phát triển cho 5 thành viên

## Yêu cầu máy

- Git, JDK 21, Node.js 20.19+ hoặc 22.12+, npm, Docker Desktop/Engine. Backend dùng Gradle Wrapper 8.10, không cần cài Gradle toàn máy.
- Trước khi sử dụng cần kiểm tra phiên bản thực tế trong source V2 gốc nếu được import.

## Lấy code

```bash
git clone https://github.com/duyxl56785/Project_HK5.git
cd Project_HK5
git switch main
git pull --ff-only origin main
```

## Backend scaffold

Auth/User cần provision PostgreSQL và nạp biến runtime theo [runbook TV1](../docker/README.md) trước `bootRun`. Build thông thường không cần DB; test DB chỉ chạy khi `TV1_DATABASE_TESTS=true`.

```bash
cd backend
./gradlew --version
./gradlew build
./gradlew :auth-service:bootRun
# ở terminal khác:
curl http://localhost:8101/actuator/health
```

Đặt `JAVA_HOME` trỏ tới JDK 21 trước khi chạy. Trên Windows PowerShell, thay `./gradlew` bằng `.\gradlew.bat`:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
cd backend
.\gradlew.bat --version
.\gradlew.bat build
.\gradlew.bat :auth-service:bootRun
```

Chạy `cd backend` từ thư mục gốc repository; bỏ lệnh này nếu đã ở `backend`. Wrapper và dependency cần mạng trong lần tải đầu; bản phân phối được xác minh bằng `distributionSha256Sum` trong `backend/gradle/wrapper/gradle-wrapper.properties`. Các service mới là scaffold, health `UP` không xác nhận Auth/User hay tích hợp đã hoạt động. Task `test` báo `NO-SOURCE` nghĩa là chưa có test, không phải unit test PASS.

## Frontend scaffold

```bash
cd frontend
npm install
npm run dev
```

Giao diện chỉ có shell giới thiệu các miền nghiệp vụ, chưa gọi Backend.

## PostgreSQL và kiểm chứng readiness

Làm theo [runbook provisioning](../docker/README.md) để kiểm kê volume cũ, cấu hình `.env`, nạp biến vào tiến trình và tạo `auth_db`/`user_db`. Sau đó từ gốc repository:

```powershell
docker compose --env-file .env -f docker/compose.dev.yml config --quiet
docker compose --env-file .env -f docker/compose.dev.yml up -d --no-deps --wait --wait-timeout 60 postgres
docker compose -f docker/compose.dev.yml ps
./docker/provision-tv1.ps1
./docker/test-tv1-databases.ps1
$env:TV1_DATABASE_TESTS = 'true'
cd backend
.\gradlew.bat build --console=plain
```

Auth có 1 smoke test HS256 và 1 test JPA/JDBC/Flyway; User có 1 test JPA/JDBC/Flyway. Khi bật biến trên, test DB không lấy kết quả từ cache/up-to-date. Không bật biến thì 2 test DB SKIPPED; 9 module khác vẫn `test NO-SOURCE`. Validate DB chưa có migration không thay migration/business tests. Kết quả và giới hạn: [PHASE B readiness](TV1_TECHNICAL_READINESS.md).

Compose không chạy FE/BE. Không bật profile `migration` cho PHASE B; runtime Auth/User vẫn là scaffold chưa có endpoint nghiệp vụ/security. Không dùng health UP để kết luận API an toàn. Secret quản trị volume cũ không được tự đổi bằng cách thay `.env`.

## Làm việc theo Issue

```bash
git switch main
git pull --ff-only origin main
git switch -c feat/tv3-checkout-api
# code, test
git add backend/order-service
git commit -m "feat(order): add checkout API (refs #3)"
git push -u origin feat/tv3-checkout-api
```

Mở PR vào `main`, nêu `Refs #3` và test thực chạy, xin review chủ sở hữu vùng code. Nếu thay interface chung phải thống nhất trước.

## Import source V2

Không copy đè toàn repo. Chủ trì TV1 tạo PR riêng để import từng module thật, giữ nguyên migration/contract/secret policy, loại bỏ scaffold placeholder tương ứng rồi chạy kiểm thử. Mọi module import cần người phụ trách kiểm tra và ghi thay đổi rõ ràng.
