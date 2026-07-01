import { Route, Routes } from 'react-router-dom';
import AdminLayout from './routes/AdminLayout';
import DataSourcesPage from './routes/DataSourcesPage';
import DashboardPage from './routes/DashboardPage';
import ExternalIdentitiesPage from './routes/ExternalIdentitiesPage';
import LoginPage from './routes/LoginPage';
import UsersPage from './routes/UsersPage';
import WorkspacesPage from './routes/WorkspacesPage';

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<AdminLayout />}>
        <Route path="/" element={<DashboardPage />} />
        <Route path="/data-sources" element={<DataSourcesPage />} />
        <Route path="/external-identities" element={<ExternalIdentitiesPage />} />
        <Route path="/workspaces" element={<WorkspacesPage />} />
        <Route path="/users" element={<UsersPage />} />
        <Route path="*" element={<DashboardPage />} />
      </Route>
    </Routes>
  );
}

export default App;
