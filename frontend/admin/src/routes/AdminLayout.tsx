import { useQueryClient } from '@tanstack/react-query';
import { Building2, Database, KeyRound, LayoutDashboard, RefreshCw, ShieldCheck, Users } from 'lucide-react';
import { NavLink, Outlet } from 'react-router-dom';

const navigation = [
  { to: '/', label: '대시보드', icon: LayoutDashboard },
  { to: '/data-sources', label: '데이터소스', icon: Database },
  { to: '/external-identities', label: '외부 계정', icon: KeyRound },
  { to: '/workspaces', label: '워크스페이스', icon: Building2 },
  { to: '/users', label: '유저', icon: Users }
];

function AdminLayout() {
  const queryClient = useQueryClient();

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
          <button
            className="icon-button"
            type="button"
            aria-label="새로고침"
            onClick={() => queryClient.invalidateQueries()}
          >
            <RefreshCw size={18} aria-hidden="true" />
          </button>
        </header>
        <Outlet />
      </main>
    </div>
  );
}

export default AdminLayout;
