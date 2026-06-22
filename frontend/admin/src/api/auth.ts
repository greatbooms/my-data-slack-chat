import { fetchCsrfToken } from './csrf';

export type AdminSession = {
  id: string;
  email: string;
  displayName: string;
  authorities: string[];
};

export async function loginAdmin(email: string, password: string): Promise<AdminSession> {
  const csrf = await fetchCsrfToken();
  const response = await fetch('/admin/auth/login', {
    body: JSON.stringify({ email, password }),
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      [csrf.headerName]: csrf.token
    },
    method: 'POST'
  });
  if (!response.ok) {
    throw new Error('로그인에 실패했습니다');
  }

  return await response.json() as AdminSession;
}
