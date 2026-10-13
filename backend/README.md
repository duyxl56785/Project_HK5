# Backend scaffold

Java 21 + Gradle multi-project + Spring Boot 3.3.5. Các service hiện chỉ chứa **Spring Boot entry point và Actuator**, chưa có nghiệp vụ, bảo mật, migration hoặc kết nối CSDL. Port trong application.yml là **port phát triển cục bộ được đề xuất**; khi đưa code V2 vào phải giữ cấu hình gốc đã được xác minh.

Từ thư mục backend:
```bash
gradle :auth-service:bootRun
gradle test
```
Máy cần cài Java 21 và Gradle 8.x tương thích Spring Boot 3.3.5 (khung chưa có Gradle Wrapper; người phụ trách TV1 cần bổ sung wrapper từ môi trường đáng tin cậy). Không coi ứng dụng đã chạy nếu chưa thực thi lệnh trên.

Tất cả package mã nguồn hiện theo convention com.projecthk5.<module>. Service nào cần PostgreSQL/JPA/Flyway, Kafka hoặc Spring Security phải thêm dependency cùng cấu hình theo API contract đã chốt, không bật tùy tiện ở toàn bộ subprojects.

Owner xem tại ../docs/OWNERSHIP.md.
