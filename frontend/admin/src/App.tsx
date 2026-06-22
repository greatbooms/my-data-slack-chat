import { Database, LayoutDashboard, RefreshCw, ShieldCheck, Users } from 'lucide-react';
import { NavLink, Route, Routes } from 'react-router-dom';

const navigation = [
  { to: '/', label: '대시보드', icon: LayoutDashboard },
  { to: '/data-sources', label: '데이터소스', icon: Database },
  { to: '/users', label: '유저', icon: Users }
];

const metrics = [
  { label: '연결된 데이터소스', value: '0', hint: 'Notion, Slack, Google Drive 준비 중' },
  { label: '진행 중 수집', value: '0', hint: '수동 수집 요청 대기' },
  { label: '관리 대상 유저', value: '0', hint: '권한 범위 기반 답변 준비' }
];

function App() {
  return (
    <div className="admin-shell">
      <aside className="sidebar" aria-label="관리자 메뉴">
        <div className="brand">
          <ShieldCheck size={22} aria-hidden="true" />
          <div>
            <strong>My Data</strong>
            <span>관리자</span>
          </div>
        </div>
        <nav className="nav-list">
          {navigation.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) => (isActive ? 'nav-item active' : 'nav-item')}
            >
              <item.icon size={18} aria-hidden="true" />
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>
      </aside>

      <main className="content">
        <header className="topbar">
          <div>
            <p>관리자 콘솔</p>
            <h1>개인 데이터 수집과 권한 관리</h1>
          </div>
          <button className="icon-button" type="button" aria-label="새로고침">
            <RefreshCw size={18} aria-hidden="true" />
          </button>
        </header>

        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/data-sources" element={<Placeholder title="데이터소스" />} />
          <Route path="/users" element={<Placeholder title="유저" />} />
          <Route path="*" element={<Dashboard />} />
        </Routes>
      </main>
    </div>
  );
}

function Dashboard() {
  return (
    <section className="dashboard" aria-label="관리자 대시보드">
      <div className="metric-grid">
        {metrics.map((metric) => (
          <article className="metric" key={metric.label}>
            <span>{metric.label}</span>
            <strong>{metric.value}</strong>
            <p>{metric.hint}</p>
          </article>
        ))}
      </div>

      <section className="work-panel">
        <div>
          <h2>다음 작업</h2>
          <p>GraphQL Codegen 기반 API 타입과 정적 서빙 파이프라인이 준비되면 실제 목록 화면을 연결합니다.</p>
        </div>
        <span className="status-badge">빌드 파이프라인</span>
      </section>
    </section>
  );
}

function Placeholder({ title }: { title: string }) {
  return (
    <section className="work-panel">
      <div>
        <h2>{title}</h2>
        <p>다음 단계에서 GraphQL query와 mutation을 연결합니다.</p>
      </div>
      <span className="status-badge">준비 중</span>
    </section>
  );
}

export default App;
