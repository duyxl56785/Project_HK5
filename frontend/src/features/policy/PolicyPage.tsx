import { Link } from 'react-router-dom';
import { TERMS_VERSION } from './terms';

const SECTIONS = [
  ['pham-vi', '1. Phạm vi áp dụng'], ['tai-khoan', '2. Tài khoản'], ['hang-cam', '3. Hàng hóa bị cấm'],
  ['dang-san-pham', '4. Đăng sản phẩm'], ['khuyen-mai', '5. Giá, khuyến mãi và voucher'], ['dat-hang', '6. Đặt hàng và thanh toán'],
  ['giao-hang', '7. Xử lý đơn, giao hàng và hủy đơn'], ['tra-hang', '8. Trả hàng'], ['danh-gia', '9. Đánh giá'],
  ['phi', '10. Phí và đối soát'], ['nguoi-ban', '11. Quy định đối với người bán'], ['vi-pham', '12. Xử lý vi phạm'],
  ['du-lieu', '13. Dữ liệu cá nhân'], ['thay-doi', '14. Thay đổi quy định'],
] as const;

export function PolicyPage() {
  return <article className="panel stack policy">
    <h1>Quy định sàn Soopi</h1>
    <p className="muted">Phiên bản {TERMS_VERSION}. Khi gửi hồ sơ người bán, bạn đồng ý với phiên bản này. Cần giải đáp nhanh? Xem <Link to="/ho-tro">Câu hỏi thường gặp</Link>.</p>

    <nav aria-label="Mục lục quy định">
      <ol className="policy__toc">{SECTIONS.map(([id, title]) => <li key={id}><a href={`#${id}`}>{title}</a></li>)}</ol>
    </nav>

    <section className="stack" id="pham-vi">
      <h2>1. Phạm vi áp dụng</h2>
      <ul>
        <li>Soopi là sàn thương mại điện tử nhiều người bán. Sàn cung cấp nơi trưng bày, đặt hàng và đối soát; hàng hóa do từng cửa hàng bán và chịu trách nhiệm.</li>
        <li>Quy định này áp dụng cho ba nhóm: người mua, người bán và quản trị viên của sàn.</li>
        <li>Người mua là tài khoản đặt hàng. Người bán là tài khoản đã được duyệt hồ sơ và có cửa hàng. Quản trị viên duyệt hồ sơ, duyệt sản phẩm, xác nhận thu COD và xử lý vi phạm.</li>
        <li>Mọi giá trị tiền trên sàn tính bằng đồng Việt Nam (VND), là số nguyên.</li>
      </ul>
    </section>

    <section className="stack" id="tai-khoan">
      <h2>2. Tài khoản</h2>
      <ul>
        <li>Mỗi người dùng đăng ký một tài khoản bằng email, mật khẩu tối thiểu 12 ký tự.</li>
        <li>Chủ tài khoản chịu trách nhiệm về mọi hoạt động trên tài khoản của mình và không chia sẻ mật khẩu cho người khác.</li>
        <li>Thông tin đăng ký phải chính xác. Không tạo tài khoản mạo danh người hoặc tổ chức khác.</li>
        <li>Đăng nhập sai quá nhiều lần trong thời gian ngắn sẽ bị tạm chặn; người dùng thử lại sau khoảng 5 phút.</li>
        <li>Quản trị viên có thể khóa tài khoản vi phạm; tài khoản bị khóa không thể đăng nhập.</li>
      </ul>
    </section>

    <section className="stack" id="hang-cam">
      <h2>3. Hàng hóa bị cấm</h2>
      <ul>
        <li>Hàng giả, hàng nhái, hàng xâm phạm quyền sở hữu trí tuệ.</li>
        <li>Vũ khí, chất nổ, chất cấm, thuốc kê đơn.</li>
        <li>Động vật hoang dã và sản phẩm từ động vật hoang dã.</li>
        <li>Hàng không rõ nguồn gốc, hàng hết hạn sử dụng.</li>
        <li>Nội dung đồi trụy, bạo lực hoặc trái pháp luật.</li>
      </ul>
      <p>Sản phẩm thuộc nhóm trên bị từ chối khi duyệt. Nếu phát hiện sau khi đã bán, sản phẩm bị đình chỉ và người bán bị xử lý theo mục 12.</p>
    </section>

    <section className="stack" id="dang-san-pham">
      <h2>4. Đăng sản phẩm</h2>
      <ul>
        <li>Tên, mô tả và hình ảnh phải đúng với hàng thật. Không dùng ảnh của sản phẩm khác hoặc ảnh gây hiểu nhầm.</li>
        <li>Mỗi sản phẩm có tối đa 5 ảnh, định dạng JPEG, PNG hoặc WebP, mỗi ảnh không quá 2 MB.</li>
        <li>Sản phẩm phải được xếp đúng danh mục.</li>
        <li>Giá niêm yết bằng VND, là số nguyên; số tồn kho phải đúng thực tế. Người bán cập nhật tồn kho khi hàng thay đổi.</li>
        <li>Sản phẩm chỉ được bán sau khi quản trị viên duyệt. Sản phẩm vi phạm bị từ chối hoặc đình chỉ kèm lý do; người bán sửa rồi gửi duyệt lại.</li>
        <li>Sản phẩm của cửa hàng đã đóng hoặc của người bán đang bị tạm ngừng không hiển thị để mua.</li>
      </ul>
    </section>

    <section className="stack" id="khuyen-mai">
      <h2>5. Giá, khuyến mãi và voucher</h2>
      <ul>
        <li>Sàn có ba loại chương trình giảm giá: Flash Sale (thứ Bảy hằng tuần), Super Sale (mỗi tháng một đợt) và xả kho do cửa hàng tự tạo.</li>
        <li>Người bán đăng ký sản phẩm và giá ưu đãi cho từng chương trình. Mọi đăng ký và chương trình xả kho chỉ có hiệu lực sau khi quản trị viên duyệt.</li>
        <li>Chương trình xả kho kéo dài từ 1 giờ đến 30 ngày.</li>
        <li>Một sản phẩm không được tham gia hai chương trình trùng thời gian.</li>
        <li>Phần giảm giá do người bán chịu. Sàn không bù giá.</li>
        <li>Giá khi đặt đơn là giá đang có hiệu lực tại thời điểm xác nhận. Nếu giá thay đổi trong lúc thanh toán, hệ thống báo để người mua xem lại, không đặt đơn theo giá cũ.</li>
        <li>Khi chương trình kết thúc hoặc bị tạm dừng, giá ưu đãi trên trang sản phẩm có thể còn hiển thị thêm vài giây. Giá ở bước thanh toán luôn là giá đúng.</li>
        <li>Sắp xếp và lọc theo giá trên trang danh sách dùng giá gốc.</li>
        <li>Mỗi cửa hàng được tạo tối đa 20 chương trình voucher, mỗi chương trình hiệu lực không quá 90 ngày, mức giảm theo phần trăm không quá 90%. Người bán có thể xin quản trị viên nâng hạn mức lên tối đa 50 chương trình trong tối đa 30 ngày.</li>
        <li>Hiện bước thanh toán chưa áp dụng voucher cho người mua.</li>
      </ul>
    </section>

    <section className="stack" id="dat-hang">
      <h2>6. Đặt hàng và thanh toán</h2>
      <ul>
        <li>Soopi chỉ hỗ trợ thanh toán khi nhận hàng (COD). Người mua trả tiền khi nhận đơn; sàn không yêu cầu chuyển khoản trước.</li>
        <li>Giỏ hàng có sản phẩm của nhiều cửa hàng được tách thành đơn riêng cho từng cửa hàng.</li>
        <li>Khi đặt đơn, hệ thống giữ số lượng hàng tương ứng trong kho của cửa hàng. Nếu không đủ hàng, đơn không được tạo.</li>
        <li>Người mua cung cấp đúng tên người nhận, số điện thoại và địa chỉ nhận hàng.</li>
        <li>Sàn có thể tạm dừng nhận đơn mới để bảo trì hoặc đối soát. Trong thời gian này giỏ hàng được giữ nguyên.</li>
        <li>Không đặt đơn ảo, đặt đơn để phá giá hoặc cố ý không nhận hàng.</li>
      </ul>
    </section>

    <section className="stack" id="giao-hang">
      <h2>7. Xử lý đơn, giao hàng và hủy đơn</h2>
      <ul>
        <li>Mỗi đơn đi qua các trạng thái: Chờ xác nhận, Đã xác nhận, Đang chuẩn bị, Sẵn sàng bàn giao, Đang giao, Đã giao.</li>
        <li>Người bán xác nhận, đóng gói và bàn giao đơn đúng thời gian, đúng hàng và đủ số lượng.</li>
        <li>Người mua được hủy đơn khi đơn còn ở trạng thái Chờ xác nhận. Sau khi cửa hàng xác nhận, người mua không tự hủy được.</li>
        <li>Người bán chỉ từ chối đơn khi có lý do chính đáng và phải ghi lý do.</li>
        <li>Đơn bị hủy hoặc bị từ chối không thu tiền; số lượng hàng đã giữ được trả lại kho.</li>
        <li>Người mua và cửa hàng nhận thông báo mỗi khi đơn đổi trạng thái.</li>
      </ul>
    </section>

    <section className="stack" id="tra-hang">
      <h2>8. Trả hàng</h2>
      <ul>
        <li>Người mua gửi yêu cầu trả hàng cho đơn đã giao, kèm lý do, trong 7 ngày kể từ khi giao hàng.</li>
        <li>Người bán duyệt hoặc từ chối yêu cầu và ghi lý do, sau đó xác nhận đã nhận lại hàng.</li>
        <li>Yêu cầu bị từ chối có thể được gửi lại nếu còn trong thời hạn 7 ngày.</li>
        <li>Sàn không hoàn tiền tự động. Cửa hàng xử lý việc hoàn tiền cho người mua sau khi nhận lại hàng.</li>
        <li>Hàng trả về không tự cộng lại vào tồn kho; người bán kiểm hàng rồi tự cập nhật.</li>
        <li>Đơn đang có yêu cầu trả hàng chưa được đối soát tiền cho người bán.</li>
      </ul>
    </section>

    <section className="stack" id="danh-gia">
      <h2>9. Đánh giá</h2>
      <ul>
        <li>Chỉ người mua đã nhận hàng mới được đánh giá sản phẩm, từ 1 đến 5 sao, không đánh giá trùng.</li>
        <li>Đánh giá phải dựa trên trải nghiệm thật với sản phẩm đã mua.</li>
        <li>Không đánh giá để đổi lấy quà, không tự đánh giá sản phẩm của mình, không dùng đánh giá để quảng cáo.</li>
        <li>Đánh giá sai sự thật, xúc phạm hoặc quảng cáo sẽ bị quản trị viên ẩn.</li>
      </ul>
    </section>

    <section className="stack" id="phi">
      <h2>10. Phí và đối soát</h2>
      <ul>
        <li>Sàn thu phí 5% trên giá trị mỗi đơn của người bán, làm tròn đến đồng. Với sản phẩm đang khuyến mãi, phí tính trên giá sau giảm.</li>
        <li>Sàn không thu phí đăng ký người bán và phí đăng sản phẩm.</li>
        <li>Sau khi đơn giao thành công và tiền COD đã thu, sàn đối soát và chuyển phần còn lại vào tài khoản ngân hàng người bán đã đăng ký.</li>
        <li>Đơn bị hủy, bị từ chối hoặc đang có yêu cầu trả hàng không được đối soát.</li>
        <li>Người bán xem các khoản thu, phí và tiền nhận về tại trang <Link to="/seller/finance">Tài chính</Link>.</li>
      </ul>
    </section>

    <section className="stack" id="nguoi-ban">
      <h2>11. Quy định đối với người bán</h2>

      <h3>11.1. Điều kiện đăng ký</h3>
      <ul>
        <li>Người đăng ký phải có tài khoản Soopi đang hoạt động và đã đăng nhập.</li>
        <li>Mỗi tài khoản chỉ có một hồ sơ người bán và chỉ mở một cửa hàng. Không lập nhiều tài khoản để mở nhiều cửa hàng cho cùng một chủ thể.</li>
        <li>Người đăng ký phải là người đại diện hợp pháp của cá nhân, hộ kinh doanh hoặc công ty ghi trong hồ sơ.</li>
        <li>Tài khoản từng bị khóa do vi phạm không được đăng ký bán hàng.</li>
      </ul>

      <h3>11.2. Hồ sơ bắt buộc</h3>
      <p>Hồ sơ gửi tại trang <Link to="/seller">Bán hàng</Link> phải có đủ các thông tin sau. Hồ sơ thiếu hoặc sai định dạng không được tiếp nhận.</p>
      <ul>
        <li>Loại hình kinh doanh: cá nhân, hộ kinh doanh hoặc công ty.</li>
        <li>Tên người đại diện và số CCCD gồm đúng 12 chữ số.</li>
        <li>Mã số thuế gồm 10 chữ số, hoặc 10 chữ số kèm mã chi nhánh 3 chữ số. Bắt buộc với hộ kinh doanh và công ty.</li>
        <li>Số điện thoại, email liên hệ và địa chỉ lấy hàng.</li>
        <li>Tài khoản ngân hàng nhận tiền: tên ngân hàng, số tài khoản và tên chủ tài khoản. Tên chủ tài khoản phải trùng với người đại diện hoặc tên đơn vị kinh doanh.</li>
        <li>Xác nhận đồng ý với Quy định sàn phiên bản hiện hành. Hệ thống ghi lại phiên bản và thời điểm đồng ý.</li>
      </ul>
      <p>Người bán chịu trách nhiệm về tính chính xác của hồ sơ. Khai sai hoặc dùng thông tin của người khác là vi phạm và bị xử lý theo mục 11.6.</p>

      <h3>11.3. Xét duyệt</h3>
      <ul>
        <li>Hồ sơ mới ở trạng thái Chờ duyệt. Trong thời gian này người nộp chưa được tạo cửa hàng hay đăng sản phẩm.</li>
        <li>Quản trị viên duyệt hoặc từ chối hồ sơ. Quyết định từ chối luôn ghi lý do.</li>
        <li>Hồ sơ bị từ chối được sửa và gửi lại. Hồ sơ gửi lại phải khắc phục đúng lý do đã nêu.</li>
        <li>Chỉ sau khi hồ sơ được duyệt, người bán mới tạo cửa hàng. Tên và đường dẫn cửa hàng không được trùng với cửa hàng đã có, không mạo danh thương hiệu hoặc cửa hàng khác.</li>
      </ul>

      <h3>11.4. Nghĩa vụ của người bán</h3>
      <ul>
        <li>Chỉ bán hàng hợp pháp, có nguồn gốc rõ ràng và không thuộc mục 3. Người bán chịu trách nhiệm trước người mua về chất lượng hàng.</li>
        <li>Đăng sản phẩm đúng mục 4 và chỉ bán sản phẩm đã được quản trị viên duyệt.</li>
        <li>Giữ số tồn kho đúng thực tế. Không nhận đơn khi không có hàng để giao.</li>
        <li>Xác nhận, chuẩn bị và bàn giao đơn đúng trình tự ở mục 7. Giao đúng hàng, đủ số lượng như đơn đã đặt.</li>
        <li>Chỉ từ chối đơn khi có lý do chính đáng và phải ghi lý do.</li>
        <li>Xử lý yêu cầu trả hàng theo mục 8: trả lời yêu cầu kèm lý do, xác nhận khi nhận lại hàng và hoàn tiền cho người mua.</li>
        <li>Chịu toàn bộ phần giảm giá của khuyến mãi và voucher do mình đăng ký; bán đúng giá ưu đãi đã được duyệt.</li>
        <li>Trả phí sàn theo mục 10 và giữ thông tin tài khoản ngân hàng nhận tiền luôn chính xác.</li>
        <li>Cập nhật hồ sơ khi thông tin liên hệ, địa chỉ lấy hàng hoặc tài khoản ngân hàng thay đổi.</li>
      </ul>

      <h3>11.5. Hành vi bị cấm</h3>
      <ul>
        <li>Bán hàng thuộc mục 3, hoặc giao hàng khác với sản phẩm đã đăng.</li>
        <li>Đăng sản phẩm trùng lặp, sai danh mục hoặc dùng tên, ảnh gây hiểu nhầm để thu hút người mua.</li>
        <li>Nâng giá gốc trước khi đăng ký khuyến mãi để tạo mức giảm giả.</li>
        <li>Tự đặt đơn, tạo đơn ảo hoặc nhờ người khác đặt đơn để tăng doanh số, tăng đánh giá.</li>
        <li>Tự đánh giá sản phẩm của mình, đổi quà lấy đánh giá hoặc gây sức ép để người mua sửa, xóa đánh giá.</li>
        <li>Dẫn người mua giao dịch, thanh toán ngoài sàn để tránh phí.</li>
        <li>Dùng thông tin nhận hàng của người mua cho mục đích ngoài việc giao đơn.</li>
        <li>Cho thuê, chuyển nhượng tài khoản người bán hoặc cửa hàng cho người khác.</li>
      </ul>

      <h3>11.6. Tạm ngừng và chấm dứt quyền bán</h3>
      <ul>
        <li>Quản trị viên tạm ngừng người bán khi có vi phạm ở mục 11.4 hoặc 11.5. Quyết định luôn ghi lý do.</li>
        <li>Khi bị tạm ngừng, sản phẩm của người bán ngừng hiển thị để mua và người bán không đăng ký được khuyến mãi mới.</li>
        <li>Đơn đã nhận trước khi bị tạm ngừng vẫn phải được xử lý đến cùng, gồm cả yêu cầu trả hàng.</li>
        <li>Người bán được khôi phục khi quản trị viên xác nhận vi phạm đã được khắc phục.</li>
        <li>Vi phạm nghiêm trọng hoặc tái phạm dẫn đến khóa tài khoản theo mục 12.</li>
      </ul>

      <p className="note">Soopi là dự án học tập: hệ thống chỉ kiểm tra định dạng, không đối chiếu với cơ quan nhà nước hay ngân hàng. Hãy dùng dữ liệu giả, không nhập giấy tờ thật. Quản trị viên chỉ thấy 4 số cuối của CCCD và số tài khoản.</p>
    </section>

    <section className="stack" id="vi-pham">
      <h2>12. Xử lý vi phạm</h2>
      <p>Tùy mức độ, quản trị viên áp dụng một trong các biện pháp sau. Mỗi quyết định đều ghi lý do.</p>
      <ul>
        <li>Ẩn đánh giá vi phạm.</li>
        <li>Từ chối hoặc đình chỉ sản phẩm vi phạm.</li>
        <li>Từ chối đăng ký khuyến mãi, tạm dừng chương trình khuyến mãi.</li>
        <li>Tạm ngừng người bán: sản phẩm của người bán ngừng hiển thị để mua.</li>
        <li>Khóa tài khoản: tài khoản không thể đăng nhập.</li>
      </ul>
    </section>

    <section className="stack" id="du-lieu">
      <h2>13. Dữ liệu cá nhân</h2>
      <ul>
        <li>Sàn chỉ dùng thông tin tài khoản, địa chỉ nhận hàng và hồ sơ người bán để xử lý đơn, đối soát và xét duyệt.</li>
        <li>Cửa hàng chỉ thấy thông tin nhận hàng của các đơn thuộc cửa hàng mình.</li>
        <li>Quản trị viên chỉ thấy 4 số cuối của CCCD và số tài khoản ngân hàng của người bán.</li>
        <li>Người dùng không thu thập hoặc dùng thông tin của người khác trên sàn cho mục đích ngoài giao dịch.</li>
      </ul>
    </section>

    <section className="stack" id="thay-doi">
      <h2>14. Thay đổi quy định</h2>
      <p>Mỗi lần sửa quy định, sàn công bố phiên bản mới kèm ngày hiệu lực ở đầu trang này. Hồ sơ người bán ghi lại phiên bản mà người bán đã đồng ý. Tiếp tục dùng sàn sau ngày hiệu lực nghĩa là chấp nhận phiên bản mới.</p>
    </section>
  </article>;
}
