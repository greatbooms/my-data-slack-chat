import '@testing-library/jest-dom/vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from './App';
import { clearCsrfToken } from './api/csrf';

function renderApp(path: string) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false
      }
    }
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe('관리자 앱 인증 흐름', () => {
  afterEach(() => {
    cleanup();
    clearCsrfToken();
    vi.unstubAllGlobals();
  });

  it('로그인 경로에서 관리자 로그인 화면을 보여준다', () => {
    renderApp('/login');

    expect(screen.getByRole('heading', { name: '관리자 로그인' })).toBeVisible();
    expect(screen.getByLabelText('이메일')).toBeVisible();
    expect(screen.getByLabelText('비밀번호')).toBeVisible();
  });

  it('CSRF 토큰으로 로그인 요청을 보내고 성공하면 대시보드로 이동한다', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({
        headerName: 'X-CSRF-TOKEN',
        parameterName: '_csrf',
        token: 'csrf-token'
      }))
      .mockResolvedValueOnce(jsonResponse({
        id: 'admin-id',
        email: 'admin@example.com',
        displayName: '관리자',
        authorities: ['ROLE_ADMIN']
      }));
    vi.stubGlobal('fetch', fetchMock);

    renderApp('/login');

    fireEvent.change(screen.getByLabelText('이메일'), {
      target: { value: 'admin@example.com' }
    });
    fireEvent.change(screen.getByLabelText('비밀번호'), {
      target: { value: 'secret1234' }
    });
    fireEvent.click(screen.getByRole('button', { name: '로그인' }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledTimes(2);
    });
    expect(fetchMock).toHaveBeenNthCalledWith(1, '/admin/auth/csrf', {
      credentials: 'include'
    });
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/admin/auth/login', {
      body: JSON.stringify({
        email: 'admin@example.com',
        password: 'secret1234'
      }),
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'csrf-token'
      },
      method: 'POST'
    });
    expect(await screen.findByRole('heading', { name: '개인 데이터 수집과 권한 관리' })).toBeVisible();
  });

  it('대시보드에서 관리자 정보와 요약 지표를 GraphQL로 불러온다', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({
        headerName: 'X-CSRF-TOKEN',
        parameterName: '_csrf',
        token: 'csrf-token'
      }))
      .mockResolvedValueOnce(dashboardResponse({ dataSourceCount: 3, runningJobCount: 1, userCount: 7 }));
    vi.stubGlobal('fetch', fetchMock);

    renderApp('/');

    expect(await screen.findByText(/admin@example\.com/)).toBeVisible();
    expect(screen.getByText(/관리자 ·/)).toBeVisible();
    expect(screen.getByLabelText('연결된 데이터소스').textContent).toContain('3');
    expect(screen.getByLabelText('진행 중 수집').textContent).toContain('1');
    expect(screen.getByLabelText('관리 대상 유저').textContent).toContain('7');
    expect(fetchMock).toHaveBeenNthCalledWith(1, '/admin/auth/csrf', {
      credentials: 'include'
    });
    const [graphqlUrl, graphqlRequestInit] = fetchMock.mock.calls[1] as [URL, RequestInit];
    const graphqlHeaders = graphqlRequestInit.headers as Headers;

    expect(graphqlUrl.toString()).toMatch(/\/admin\/graphql$/);
    expect(graphqlRequestInit.credentials).toBe('include');
    expect(graphqlRequestInit.method).toBe('POST');
    expect(graphqlHeaders.get('X-CSRF-TOKEN')).toBe('csrf-token');
    expect(JSON.parse(graphqlRequestInit.body as string).query).toContain('ViewerAndDashboard');
  });

  it('새로고침 버튼을 누르면 대시보드 GraphQL 데이터를 다시 불러온다', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({
        headerName: 'X-CSRF-TOKEN',
        parameterName: '_csrf',
        token: 'csrf-token'
      }))
      .mockResolvedValueOnce(dashboardResponse({ dataSourceCount: 3, runningJobCount: 1, userCount: 7 }))
      .mockResolvedValueOnce(dashboardResponse({ dataSourceCount: 4, runningJobCount: 0, userCount: 8 }));
    vi.stubGlobal('fetch', fetchMock);

    renderApp('/');

    expect(await screen.findByText(/admin@example\.com/)).toBeVisible();
    expect(screen.getByLabelText('연결된 데이터소스').textContent).toContain('3');

    fireEvent.click(screen.getByRole('button', { name: '새로고침' }));

    await waitFor(() => {
      expect(screen.getByLabelText('연결된 데이터소스').textContent).toContain('4');
    });
    expect(screen.getByLabelText('진행 중 수집').textContent).toContain('0');
    expect(screen.getByLabelText('관리 대상 유저').textContent).toContain('8');
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });
});

function jsonResponse(body: unknown, init: ResponseInit = {}) {
  return new Response(JSON.stringify(body), {
    headers: {
      'Content-Type': 'application/json'
    },
    status: 200,
    ...init
  });
}

function dashboardResponse(summary: {
  dataSourceCount: number;
  runningJobCount: number;
  userCount: number;
}) {
  return jsonResponse({
    data: {
      viewer: {
        id: 'admin-id',
        email: 'admin@example.com',
        displayName: '관리자',
        role: 'ADMIN'
      },
      dashboardSummary: summary
    }
  });
}
