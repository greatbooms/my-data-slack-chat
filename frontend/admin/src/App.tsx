import { Route, Routes } from 'react-router-dom';
import AdminLayout from './routes/AdminLayout';
import DataSourcesPage from './routes/DataSourcesPage';
import DashboardPage from './routes/DashboardPage';
import LoginPage from './routes/LoginPage';
import UsersPage from './routes/UsersPage';

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<AdminLayout />}>
        <Route path="/" element={<DashboardPage />} />
        <Route path="/data-sources" element={<DataSourcesPage />} />
        <Route path="/users" element={<UsersPage />} />
        <Route path="*" element={<DashboardPage />} />
      </Route>
    </Routes>
  );
}

export default App;
