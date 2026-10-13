import { Link, Route, Routes } from 'react-router-dom';
import { BrowserRouter } from 'react-router-dom';
import { domains } from '../shared/domains';

function Landing() {
  return (
    <main className="container">
      <span className="tag">PROJECT SCAFFOLD — CHƯA CÓ NGHIỆP VỤ</span>
      <h1>Smart E-Commerce Marketplace V2</h1>
      <p>Khung giao diện để nhóm 5 thành viên phát triển các tính năng theo Issue. Chưa kết nối API.</p>
      <section className="grid" aria-label="Danh sách khu vực phát triển">
        {domains.map((d) => (
          <article key={d.path} className="card">
            <small>{d.owner}</small>
            <h2>{d.label}</h2>
            <p>{d.note}</p>
            <Link to={d.path}>Xem vùng phụ trách →</Link>
          </article>
        ))}
      </section>
    </main>
  );
}

function Placeholder({ name }: { name: string }) {
  return <main className="container"><Link to="/">← Quay lại</Link><h1>{name}</h1><p>Chưa triển khai chức năng. Tạo branch và PR theo CONTRIBUTING.md.</p></main>;
}

export function App() {
  return <BrowserRouter><Routes>
    <Route path="/" element={<Landing />} />
    {domains.map((d) => <Route key={d.path} path={d.path} element={<Placeholder name={d.label} />} />)}
  </Routes></BrowserRouter>;
}
