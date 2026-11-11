import { Link } from 'react-router-dom';
import { TERMS_VERSION } from './terms';

export function PolicyPage() {
  return <article className="panel stack policy">
    <h1>Quy định sàn Soopi</h1>
    <p className="muted">Phiên bản {TERMS_VERSION}. Khi gửi hồ sơ người bán, bạn đồng ý với phiên bản này.</p>

    <section className="stack">
      <h2>1. Tài khoản</h2>
      <ul>
        <li>Mỗi người dùng đăng ký một tài khoản bằng email, mật khẩu tối thiểu 12 ký tự.</li>
        <li>Chủ tài khoản chịu trách nhiệm về mọi hoạt động trên tài khoản của mình.</li>
        <li>Quản trị viên có thể khóa tài khoản vi phạm; tài khoản bị khóa không thể đăng nhập.</li>
      </ul>
    </section>

    <section className="stack">
      <h2>2. Hàng hóa bị cấm</h2>
      <ul>
        <li>Hàng giả, hàng nhái, hàng xâm phạm quyền sở hữu trí tuệ.</li>
        <li>Vũ khí, chất nổ, chất cấm, thuốc kê đơn.</li>
        <li>Động vật hoang dã và sản phẩm từ động vật hoang dã.</li>
        <li>Hàng không rõ nguồn gốc, hàng hết hạn sử dụng.</li>
        <li>Nội dung đồi trụy, bạo lực hoặc trái pháp luật.</li>
      </ul>
    </section>

    <section className="stack">
      <h2>3. Đăng sản phẩm</h2>
      <ul>
        <li>Tên, mô tả và hình ảnh phải đúng với hàng thật.</li>
        <li>Giá niêm yết bằng VND, là số nguyên; số tồn kho phải đúng thực tế.</li>
        <li>Sản phẩm chỉ được bán sau khi quản trị viên duyệt. Sản phẩm vi phạm bị từ chối hoặc đình chỉ kèm lý do.</li>
      </ul>
    </section>

    <section className="stack">
      <h2>4. Đơn hàng và giao hàng</h2>
      <ul>
        <li>Soopi chỉ hỗ trợ thanh toán khi nhận hàng (COD).</li>
        <li>Giỏ hàng có sản phẩm của nhiều cửa hàng được tách thành đơn riêng cho từng cửa hàng.</li>
        <li>Người bán xác nhận, đóng gói và giao đơn đúng thời gian; chỉ hủy hoặc từ chối đơn khi có lý do chính đáng.</li>
      </ul>
    </section>

    <section className="stack">
      <h2>5. Trả hàng</h2>
      <ul>
        <li>Người mua gửi yêu cầu trả hàng cho đơn đã giao, kèm lý do.</li>
        <li>Người bán duyệt hoặc từ chối yêu cầu, sau đó xác nhận đã nhận lại hàng.</li>
        <li>Đơn đang có yêu cầu trả hàng chưa được đối soát tiền cho người bán.</li>
      </ul>
    </section>

    <section className="stack">
      <h2>6. Đánh giá</h2>
      <ul>
        <li>Chỉ người mua đã nhận hàng mới được đánh giá sản phẩm, từ 1 đến 5 sao, không đánh giá trùng.</li>
        <li>Đánh giá sai sự thật, xúc phạm hoặc quảng cáo sẽ bị quản trị viên ẩn.</li>
      </ul>
    </section>

    <section className="stack">
      <h2>7. Phí và đối soát</h2>
      <ul>
        <li>Sàn thu phí 5% trên giá trị mỗi đơn của người bán, làm tròn đến đồng.</li>
        <li>Sau khi đơn giao thành công và tiền COD đã thu, sàn đối soát và chuyển phần còn lại vào tài khoản ngân hàng người bán đã đăng ký.</li>
      </ul>
    </section>

    <section className="stack">
      <h2>8. Xử lý vi phạm</h2>
      <p>Tùy mức độ, quản trị viên có thể ẩn đánh giá, đình chỉ sản phẩm, tạm ngừng người bán hoặc khóa tài khoản. Mỗi quyết định đều ghi lý do.</p>
    </section>

    <section className="stack" id="nguoi-ban">
      <h2>9. Điều kiện trở thành người bán</h2>
      <p>Người dùng đã đăng nhập gửi hồ sơ tại trang <Link to="/seller">Bán hàng</Link> với đủ các thông tin sau:</p>
      <ul>
        <li>Loại hình kinh doanh: cá nhân, hộ kinh doanh hoặc công ty.</li>
        <li>Tên người đại diện và số CCCD gồm 12 chữ số.</li>
        <li>Mã số thuế (10 chữ số, hoặc 10 chữ số kèm mã chi nhánh 3 chữ số). Bắt buộc với hộ kinh doanh và công ty.</li>
        <li>Số điện thoại, email liên hệ và địa chỉ lấy hàng.</li>
        <li>Tài khoản ngân hàng nhận tiền: tên ngân hàng, số tài khoản và tên chủ tài khoản.</li>
        <li>Đồng ý với Quy định sàn phiên bản hiện hành.</li>
      </ul>
      <p>Quản trị viên xét duyệt hồ sơ. Hồ sơ bị từ chối có ghi lý do; người nộp sửa và gửi lại. Sau khi được duyệt, người bán tạo cửa hàng và đăng sản phẩm.</p>
      <p className="note">Soopi là dự án học tập: hệ thống chỉ kiểm tra định dạng, không đối chiếu với cơ quan nhà nước hay ngân hàng. Hãy dùng dữ liệu giả, không nhập giấy tờ thật. Quản trị viên chỉ thấy 4 số cuối của CCCD và số tài khoản.</p>
    </section>
  </article>;
}
