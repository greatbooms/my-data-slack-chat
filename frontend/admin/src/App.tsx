import { Route, Routes } from 'react-router-dom';
import AdminLayout from './routes/AdminLayout';
import DashboardPage from './routes/DashboardPage';
import LoginPage from './routes/LoginPage';

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<AdminLayout />}>
        <Route path="/" element={<DashboardPage />} />
        <Route path="/data-sources" element={<Placeholder title="데이터소스" />} />
        <Route path="/users" element={<Placeholder title="유저" />} />
        <Route path="*" element={<DashboardPage />} />
      </Route>
    </Routes>
  );
}

function Placeholder({ title }: { title: string }) {
  return (
    <section className="work-panel">
      <div>
        <h2>{title}</h2>
        <p>관리 기능을 연결하는 중입니다.</p>
      </div>
      <span className="status-badge">준비 중</span>
    </section>
  );
}

export default App;
