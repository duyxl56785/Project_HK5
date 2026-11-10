# Review độc lập PR #187, 10/11/2026

- Source: `bc82486315c2135ae48112887da7a4b48538ed43`, base `main` tại thời điểm review.
- Môi trường: Windows, Java/Gradle của repository, Docker Engine với PostgreSQL Testcontainers cho suite đầu.
- Lệnh 1: `./backend/gradlew.bat -p backend :order-service:postgresIntegrationTest --tests '*ReturnConstraintPostgresIT' --rerun-tasks --no-daemon --console=plain`; exit `0`; XML **5 PASS, 0 FAIL/ERROR/SKIP**; SHA256 `15E692CE1CD0CB3A4C212C51350B0D9AA489407BEE2CB6B48371C990DA6D2BB6`.
- Lệnh 2: `./backend/gradlew.bat -p backend :order-service:test --tests '*ReturnRequestServiceRulesTest' --rerun-tasks --no-daemon --console=plain`; exit `0`; XML **5 PASS, 0 FAIL/ERROR/SKIP**; SHA256 `06C53CB3F6AEAA7F13A0D9FFB8830F5D8DA8F7E9812F78E392F727911B539209`.
- XML là bản sao nguyên byte từ Gradle. Không lưu raw console log. Rà soát mẫu Bearer/JWT, credential assignment và email trong XML: không phát hiện.
- [Formal review APPROVED scoped](https://github.com/duyxl56785/Project_HK5/pull/187#pullrequestreview-5835076508) đã được GitHub ghi nhận ở đúng HEAD. Chỉ xác nhận mapping PostgreSQL constraint và rules đã kiểm; không xác nhận UNKNOWN, refund, Finance production hay final UAT.
