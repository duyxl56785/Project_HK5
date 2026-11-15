import { useState } from 'react';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';

interface Question { readonly q: string; readonly a: ReactNode }
interface Topic { readonly id: string; readonly title: string; readonly questions: readonly Question[] }

const TOPICS: readonly Topic[] = [
  { id: 'mua-hang', title: 'Mua hàng và đặt đơn', questions: [
    { q: 'Làm sao để đặt hàng trên Soopi?', a: <>Đăng nhập bằng tài khoản người mua, thêm sản phẩm vào <Link to="/cart">Giỏ hàng</Link>, chọn các sản phẩm muốn mua rồi bấm Thanh toán. Nhập địa chỉ nhận hàng, kiểm tra lại tổng tiền và xác nhận đặt đơn.</> },
    { q: 'Vì sao một lần đặt lại thành nhiều đơn?', a: 'Giỏ hàng có sản phẩm của nhiều cửa hàng sẽ được tách thành một đơn riêng cho từng cửa hàng. Mỗi cửa hàng tự xác nhận, đóng gói và giao phần của mình, nên trạng thái từng đơn có thể khác nhau.' },
    { q: 'Vì sao lúc thanh toán báo giá đã thay đổi?', a: 'Giá hoặc chương trình khuyến mãi của sản phẩm đã đổi sau khi bạn thêm vào giỏ. Soopi không đặt đơn theo giá cũ. Hãy quay lại giỏ hàng để xem giá mới rồi thanh toán lại.' },
    { q: 'Vì sao báo "Sàn đang tạm dừng nhận đơn"?', a: 'Sàn đang tạm ngừng nhận đơn mới để bảo trì hoặc đối soát. Giỏ hàng của bạn vẫn được giữ nguyên. Hãy thử thanh toán lại sau.' },
    { q: 'Tôi hủy đơn được không?', a: <>Bạn hủy được khi đơn còn ở trạng thái "Chờ xác nhận", ngay trong trang <Link to="/orders">Đơn hàng</Link>. Sau khi cửa hàng đã xác nhận, bạn không tự hủy được nữa. Khi đơn bị hủy, số lượng hàng đã giữ được trả lại kho của cửa hàng.</> },
    { q: 'Tôi dùng mã giảm giá (voucher) được chưa?', a: 'Chưa. Hiện cửa hàng mới tạo và quản lý được chương trình voucher; bước thanh toán chưa áp dụng mã giảm giá. Giá ưu đãi của Flash Sale, Super Sale và xả kho thì được tính sẵn vào giá sản phẩm.' },
  ] },
  { id: 'thanh-toan', title: 'Thanh toán và giao hàng', questions: [
    { q: 'Soopi hỗ trợ những hình thức thanh toán nào?', a: 'Soopi chỉ hỗ trợ thanh toán khi nhận hàng (COD). Bạn trả tiền mặt cho bên giao hàng khi nhận đơn. Soopi không yêu cầu chuyển khoản trước.' },
    { q: 'Làm sao theo dõi đơn hàng?', a: <>Mở trang <Link to="/orders">Đơn hàng</Link>. Mỗi đơn đi qua các bước: Chờ xác nhận, Đã xác nhận, Đang chuẩn bị, Sẵn sàng bàn giao, Đang giao, Đã giao. Bạn cũng nhận thông báo khi đơn đổi trạng thái.</> },
    { q: 'Đơn của tôi bị cửa hàng từ chối thì sao?', a: 'Cửa hàng có thể từ chối đơn khi không thể phục vụ, kèm lý do. Đơn bị từ chối không thu tiền. Bạn có thể đặt lại ở cửa hàng khác.' },
  ] },
  { id: 'tra-hang', title: 'Trả hàng', questions: [
    { q: 'Tôi được trả hàng trong bao lâu?', a: 'Trong 7 ngày kể từ khi đơn được giao. Quá thời hạn này, hệ thống không nhận yêu cầu trả hàng.' },
    { q: 'Gửi yêu cầu trả hàng ở đâu?', a: <>Mở đơn đã giao trong trang <Link to="/orders">Đơn hàng</Link>, chọn yêu cầu trả hàng và ghi rõ lý do. Cửa hàng sẽ duyệt hoặc từ chối yêu cầu.</> },
    { q: 'Yêu cầu trả hàng bị từ chối, tôi làm gì được?', a: 'Bạn có thể gửi yêu cầu mới nếu còn trong thời hạn 7 ngày. Hãy ghi lý do cụ thể hơn để cửa hàng xem xét.' },
    { q: 'Tiền được hoàn như thế nào?', a: 'Soopi không hoàn tiền tự động. Sau khi duyệt yêu cầu và nhận lại hàng, cửa hàng xử lý việc hoàn tiền với bạn.' },
  ] },
  { id: 'tai-khoan', title: 'Tài khoản', questions: [
    { q: 'Đăng ký tài khoản cần gì?', a: <>Một email và mật khẩu tối thiểu 12 ký tự. Đăng ký tại trang <Link to="/register">Đăng ký</Link>.</> },
    { q: 'Vì sao tôi không đăng nhập được?', a: 'Hãy kiểm tra lại email và mật khẩu. Nếu thử sai quá nhiều lần, hệ thống tạm chặn đăng nhập; hãy đợi khoảng 5 phút rồi thử lại. Tài khoản bị quản trị viên khóa thì không đăng nhập được.' },
    { q: 'Xem thông báo ở đâu?', a: <>Bấm biểu tượng chuông ở đầu trang, hoặc mở trang <Link to="/notifications">Thông báo</Link>.</> },
  ] },
  { id: 'nguoi-ban', title: 'Người bán', questions: [
    { q: 'Làm sao để bán hàng trên Soopi?', a: <>Đăng nhập, mở <Link to="/seller">Kênh người bán</Link> và gửi hồ sơ. Sau khi quản trị viên duyệt, bạn tạo cửa hàng và đăng sản phẩm. Điều kiện chi tiết ở <Link to="/quy-dinh#nguoi-ban">mục 11 của Quy định</Link>.</> },
    { q: 'Vì sao sản phẩm của tôi chưa hiển thị?', a: 'Sản phẩm mới phải được quản trị viên duyệt trước khi bán. Sản phẩm bị từ chối hoặc đình chỉ có ghi lý do; hãy sửa rồi gửi lại.' },
    { q: 'Phí bán hàng là bao nhiêu?', a: 'Sàn thu 5% trên giá trị mỗi đơn, làm tròn đến đồng. Không có phí đăng ký hay phí đăng sản phẩm.' },
    { q: 'Khi nào tôi nhận được tiền?', a: 'Sau khi đơn giao thành công và tiền COD đã được thu, sàn đối soát và chuyển phần còn lại vào tài khoản ngân hàng bạn đã đăng ký. Đơn đang có yêu cầu trả hàng chưa được đối soát.' },
    { q: 'Tôi tạo được bao nhiêu voucher?', a: 'Mặc định mỗi cửa hàng có tối đa 20 chương trình voucher. Bạn có thể xin quản trị viên nâng lên tối đa 50 chương trình, trong tối đa 30 ngày. Lưu ý: bước thanh toán chưa áp dụng voucher cho người mua.' },
  ] },
];

const fold = (text: string) => text.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/gi, 'd').toLowerCase();

export function SupportPage() {
  const [term, setTerm] = useState('');
  const needle = fold(term.trim());
  const topics = TOPICS.map((topic) => ({ ...topic, questions: topic.questions.filter((item) => fold(item.q).includes(needle)) }))
    .filter((topic) => topic.questions.length > 0);
  return <div className="stack stack--page help">
    <section className="help__hero">
      <h1>Xin chào, Soopi có thể giúp gì cho bạn?</h1>
      <input type="search" aria-label="Tìm câu hỏi" placeholder="Nhập từ khóa, ví dụ: trả hàng" maxLength={100}
        value={term} onChange={(e) => setTerm(e.currentTarget.value)} />
    </section>
    <nav className="help__cards" aria-label="Nội dung hỗ trợ">
      <a className="panel" href="#cau-hoi"><strong>Câu hỏi thường gặp</strong><span>Đặt hàng, thanh toán, trả hàng, tài khoản, bán hàng</span></a>
      <Link className="panel" to="/quy-dinh"><strong>Quy định chung của sàn</strong><span>Quyền và nghĩa vụ của người mua, người bán và Soopi</span></Link>
    </nav>
    <section className="stack" id="cau-hoi">
      <h2>Câu hỏi thường gặp</h2>
      <p role="status">{topics.length ? '' : 'Không có câu hỏi nào khớp. Hãy thử từ khóa khác hoặc xem Quy định chung.'}</p>
      {topics.map((topic) => <section key={topic.id} className="panel stack faq" aria-labelledby={`faq-${topic.id}`}>
        <h3 id={`faq-${topic.id}`}>{topic.title}</h3>
        {topic.questions.map((item) => <details key={item.q}><summary>{item.q}</summary><p>{item.a}</p></details>)}
      </section>)}
    </section>
    <p className="note">Chưa tìm thấy câu trả lời? Xem <Link to="/quy-dinh">Quy định chung của sàn</Link>. Soopi là dự án học tập nên chưa có tổng đài hay kênh chat trực tiếp.</p>
  </div>;
}
