# Backend scaffold

Java 21 + Gradle multi-project + Spring Boot 3.3.5. Chưa có API nghiệp vụ/security. Auth/User đã chuẩn bị JPA/JDBC/Flyway và datasource riêng; chưa có entity hoặc migration. Xem [runbook PostgreSQL](../docker/README.md) để provision database và nạp runtime credentials trước `bootRun`. Port trong application.yml là port phát triển cục bộ.

Từ thư mục backend:
```bash
./gradlew :auth-service:bootRun
./gradlew build
```
Máy cần cài JDK 21 và đặt `JAVA_HOME` trỏ tới JDK đó. Gradle Wrapper ghim Gradle 8.10, tự tải bản phân phối và kiểm tra SHA-256; không cần cài Gradle toàn máy. Trên Windows PowerShell dùng `.\gradlew.bat` thay cho `./gradlew`. Lần đầu cần mạng để tải Gradle và dependency.

Tất cả package mã nguồn hiện theo convention com.projecthk5.<module>. Dependency persistence chỉ bật ở Auth/User; Security/JOSE mới được audit và smoke test ở Auth test classpath, chưa bật security runtime. Không bật dependency tùy tiện ở toàn bộ subprojects. `ddl-auto=validate`, Flyway mặc định disabled; profile `migration` dành cho PR persistence tiếp theo.

Owner xem tại ../docs/OWNERSHIP.md.
