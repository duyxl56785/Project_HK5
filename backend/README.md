# Backend scaffold

Java 21 + Gradle multi-project + Spring Boot 3.3.5. Các service hiện chỉ chứa **Spring Boot entry point và Actuator**, chưa có nghiệp vụ, bảo mật, migration hoặc kết nối CSDL. Port trong application.yml là **port phát triển cục bộ được đề xuất**; khi đưa code V2 vào phải giữ cấu hình gốc đã được xác minh.

Từ thư mục backend:
```bash
./gradlew :auth-service:bootRun
./gradlew build
```
Máy cần cài JDK 21 và đặt `JAVA_HOME` trỏ tới JDK đó. Gradle Wrapper ghim Gradle 8.10, tự tải bản phân phối và kiểm tra SHA-256; không cần cài Gradle toàn máy. Trên Windows PowerShell dùng `.\gradlew.bat` thay cho `./gradlew`. Lần đầu cần mạng để tải Gradle và dependency.

Tất cả package mã nguồn hiện theo convention com.projecthk5.<module>. Service nào cần PostgreSQL/JPA/Flyway, Kafka hoặc Spring Security phải thêm dependency cùng cấu hình theo API contract đã chốt, không bật tùy tiện ở toàn bộ subprojects.

Owner xem tại ../docs/OWNERSHIP.md.
