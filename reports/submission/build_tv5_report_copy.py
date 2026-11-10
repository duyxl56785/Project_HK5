"""Append a clearly scoped TV5 evidence section to a copy of the group report.

Usage: bundled-python build_tv5_report_copy.py SOURCE.docx OUTPUT.docx
The source is opened read-only; the original is never overwritten.
"""

from pathlib import Path
import hashlib
import sys

from docx import Document
from docx.enum.table import WD_CELL_VERTICAL_ALIGNMENT
from docx.oxml import OxmlElement
from docx.shared import Cm, Pt


def add_body(doc, text):
    p = doc.add_paragraph(text)
    p.style = doc.styles["Normal"]
    # Scope compact spacing to the appended TV5 section; never restyle the source.
    p.paragraph_format.space_before = Pt(0)
    p.paragraph_format.space_after = Pt(4)
    p.paragraph_format.line_spacing = 1.15
    for run in p.runs:
        run.font.name = "Times New Roman"
        run.font.size = Pt(11)
    return p


def add_table(doc, headers, rows):
    table = doc.add_table(rows=1, cols=len(headers))
    table.style = "Table Grid"
    table.autofit = True
    for j, title in enumerate(headers):
        table.rows[0].cells[j].text = title
    # Word may split the evidence matrix over pages; repeat the column labels.
    header_properties = table.rows[0]._tr.get_or_add_trPr()
    header_properties.append(OxmlElement("w:tblHeader"))
    for values in rows:
        cells = table.add_row().cells
        for j, value in enumerate(values):
            cells[j].text = value
    for row in table.rows:
        for cell in row.cells:
            cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
            for p in cell.paragraphs:
                for run in p.runs:
                    run.font.name = "Times New Roman"
                    run.font.size = Pt(10)
    doc.add_paragraph()


def main(source: Path, target: Path):
    if source.resolve() == target.resolve():
        raise ValueError("Source and target must differ")
    target.parent.mkdir(parents=True, exist_ok=True)
    doc = Document(source)
    source_sha = hashlib.sha256(source.read_bytes()).hexdigest()

    # Preserve all existing group material and its explicit TODO placeholders.
    doc.add_page_break()
    doc.add_heading("Phụ lục D Đóng góp và bằng chứng TV5", level=1)
    add_body(doc, "Nguyễn Minh Duy phụ trách Product Review F15, Finance F16, QA và đối chiếu bằng chứng. Phụ lục này là phần bổ sung cho bản báo cáo nhóm, không thay các kết quả lịch sử ở Chương 11. Mỗi kết quả dưới đây gắn với đúng mã nguồn và loại kiểm thử; final UAT hai Store chưa được thực hiện.")
    add_body(doc, f"Nguồn báo cáo nhóm được sao chép không sửa: SHA256 {source_sha}. Bản sao này bổ sung nội dung TV5 ngày 07/11/2026; các vị trí ảnh B.1–B.5 tiếp tục đánh dấu TODO cho tới khi có ảnh thật.")

    doc.add_heading("D.1 Product Review F15", level=2)
    add_body(doc, "Quy tắc cần kiểm: chỉ buyer của đơn DELIVERED được đánh giá sản phẩm thuộc đơn, rating 1–5, mỗi sản phẩm trong một đơn chỉ một review. Admin có thể ẩn hoặc hiện; review ẩn không góp vào rating công khai. R06 giới hạn comment mới theo 1000 đơn vị UTF-16 nhưng vẫn phải quản trị được review legacy dài hơn. Backend quyết định quyền; hiển thị UI không thay kiểm quyền. Chưa có receipt final live hai Store bao phủ toàn bộ các điều kiện này.")
    add_body(doc, "Bằng chứng hẹp: main c1ac4d4e5dd450d5d3fc3d6a8267cb5b45f32d48 chạy ReviewCommentLimitTest 2 PASS và ReviewCommentMigrationPostgresTest 1 PASS, có JUnit XML/SHA256 tại reports/minh-chung-kiem-thu/issue18-main-c1ac-r06/. Các lượt branch khác không được cộng thành tổng nghiệm thu release.")

    doc.add_heading("D.2 Finance F16 và Return", level=2)
    add_body(doc, "Commission, seller net và số tiền phải lấy từ snapshot của SellerOrder. Phí 5% và House Store 0% chỉ áp dụng đúng phạm vi policy được phê duyệt; House operation thuộc TV2 và business owner, ngoài phạm vi vận hành TV5. TV5 kiểm tác động Finance và đối soát. Settlement cần đúng trạng thái DELIVERED/COD collected, một lần duy nhất và dữ liệu tách theo Store. Không dùng unit arithmetic để tuyên Finance production PASS.")
    add_body(doc, "Return MVP theo quyết định ở Issue 155: buyer trả nguyên SellerOrder COD đã giao, chưa settled trong 7 ngày UTC; Return REQUESTED/APPROVED/RECEIVED chặn settlement, REJECTED bỏ chặn. Collect COD có thể ghi nhận tiền thực thu. Không tự refund/reversal hoặc RELEASE hàng đã CONSUME. Main 618f9ea8f723d607454397012cd5613ca199f660 chạy FulfillmentPostgres16IT 20 PASS, 0 FAIL, 0 SKIP, PostgreSQL 16 Testcontainers + MockMvc; Store/provider vẫn là mocks. JUnit XML và SHA256 ở reports/minh-chung-kiem-thu/tv5-main-return-settlement-20261107/. Đây không phải Gateway live hoặc final UAT.")

    doc.add_heading("D.3 Ma trận kiểm thử và giới hạn", level=2)
    add_table(doc,
        ["ID", "Mục tiêu và expected result", "Actual/evidence đã có", "Trạng thái"],
        [
            ("F15 R06", "Comment mới <=1000 UTF-16; legacy vẫn quản trị", "Main c1ac4d4: 2 unit + 1 PostgreSQL; XML/SHA256", "PASS hẹp"),
            ("F16 RET-04", "Return đang mở chặn settle; race không cùng commit", "Main 618f9ea: FulfillmentPostgres16IT 20/20; MockMvc + PG", "PASS hẹp"),
            ("U03/U08", "Hai Store, đúng owner, tiền và settlement", "Chưa có final HTTP/UI/DB receipt trên main tích hợp", "BLOCKED"),
            ("U11/U12", "UNKNOWN claim conflict và mất response, không side effect kép", "Fixture thật/runner/COMMIT receipt chưa được duyệt đủ", "NOT_RUN"),
            ("B.1–B.5", "Ảnh UI thật cùng run SHA và oracle", "Năm vị trí ở Phụ lục B còn TODO", "NOT_RUN"),
        ])
    add_body(doc, "PASS ở bảng là kết quả hẹp của lượt đã chạy, không phải trạng thái nghiệm thu toàn chức năng. CI xanh, demo browser hoặc ảnh mock không thay bằng chứng production. Main quan sát sau đó 868fd8deb7abd789cea93bbd71fc6f8e3d641a71 chưa được dùng cho final UAT trong phụ lục này.")

    doc.add_heading("D.4 QA Security và công cụ Soopi", level=2)
    add_body(doc, "QA kiểm JWT thiếu/sai/hết hạn, Customer/Seller/Admin RBAC, Store isolation, replay/idempotency, XSS input và session revoke bằng ca cố định trên localhost với dữ liệu giả. Kết quả demo hoặc mock phải ghi rõ phạm vi. Soopi TV5 QA Tester tại tools/tv5-qa-tester có runner allowlist, timeout/cancel, lịch sử, chẩn đoán, ảnh/log/JUnit và xuất HTML/JSON/CSV/ZIP. PR 179 chờ TV2 review HEAD 2a2c4e4; approval ở head cũ không xác nhận code hiện tại. Không đưa tester vào giao diện khách hàng production.")

    doc.add_heading("D.5 Việc còn chờ và tự đánh giá", level=2)
    add_body(doc, "TV5 đã nối yêu cầu F15/F16 với regression và bằng chứng, tìm sai lệch mà số CI tổng không thể hiện, và bàn giao owner theo phạm vi. Cần TV2 xác nhận tester và E2E Return, TV4 hoàn tất Seller Return/receipt/restock, TV3 xác nhận contract consumer, và các owner phê duyệt hai fixture UNKNOWN trước khi chạy. Ảnh B.1–B.5, report/deck cuối, final UAT và kết luận Finance production còn thiếu bằng chứng; Issue 18 và 162 giữ PARTIAL/BLOCKED. Không tự chấm điểm hoặc ký nghiệm thu thay nhóm.")
    add_body(doc, "Nguồn truy vết trong repository: docs/project-report/TV5_REPORT_CONTENT.md; docs/project-report/TV5_APPENDIX_B_CAPTURE_SHEET.md; tests/TV5_UAT_ACCEPTANCE_CURRENT.md; reports/TV5_PR185_PR182_NEXT_HEAD_REVIEW_PLAN.md. Khi nộp, cần mở lại nguồn ở đúng commit và đối chiếu mọi thay đổi mới trước khi cập nhật số liệu.")

    doc.save(target)
    print(f"OUTPUT={target}")
    print(f"SOURCE_SHA256={source_sha}")
    print(f"OUTPUT_SHA256={hashlib.sha256(target.read_bytes()).hexdigest()}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: build_tv5_report_copy.py SOURCE.docx OUTPUT.docx")
    main(Path(sys.argv[1]), Path(sys.argv[2]))
