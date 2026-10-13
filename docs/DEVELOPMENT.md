# Hướng dẫn phát triển cho 5 thành viên

## Yêu cầu máy

- Git, JDK 21, Gradle 8.x, Node.js 20.19+ hoặc 22.12+, npm, Docker Desktop/Engine.
- Trước khi sử dụng cần kiểm tra phiên bản thực tế trong source V2 gốc nếu được import.

## Lấy code

```bash
git clone https://github.com/duyxl56785/Project_HK5.git
cd Project_HK5
git switch main
git pull --ff-only origin main
```

## Backend scaffold

```bash
cd backend
gradle :auth-service:bootRun
# ở terminal khác:
curl http://localhost:8101/actuator/health
```

Lệnh này **chưa được kiểm thử tại thời điểm tạo scaffold** và mới áp dụng cho service mẫu, không phải xác nhận luồng nghiệp vụ hoạt động. Gradle Wrapper hiện chưa có; TV1 cần bổ sung `gradlew` để thống nhất phiên bản cho nhóm.

## Frontend scaffold

```bash
cd frontend
npm install
npm run dev
```

Giao diện chỉ có shell giới thiệu các miền nghiệp vụ, chưa gọi Backend.

## Hạ tầng mẫu local

```bash
docker compose -f docker/compose.dev.yml up -d
docker compose -f docker/compose.dev.yml ps
```

Compose này chỉ có Postgres/Redis/Kafka, chưa tạo 8 service DB và không chạy FE/BE.

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
