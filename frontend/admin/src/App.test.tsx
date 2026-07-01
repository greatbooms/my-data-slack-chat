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
    const emailInput = screen.getByLabelText('이메일');
    const passwordInput = screen.getByLabelText('비밀번호');
    const loginForm = emailInput.closest('form');

    expect(loginForm).toHaveAttribute('method', 'post');
    expect(loginForm).toHaveAttribute('action', '/admin/auth/login');
    expect(loginForm).toHaveAttribute('autocomplete', 'on');
    expect(emailInput).toBeVisible();
    expect(emailInput).toHaveAttribute('id', 'admin-username');
    expect(emailInput).toHaveAttribute('name', 'username');
    expect(emailInput).toHaveAttribute('autocomplete', 'username');
    expect(emailInput).toBeRequired();
    expect(passwordInput).toBeVisible();
    expect(passwordInput).toHaveAttribute('id', 'admin-password');
    expect(passwordInput).toHaveAttribute('name', 'password');
    expect(passwordInput).toHaveAttribute('autocomplete', 'current-password');
    expect(passwordInput).toBeRequired();
  });

  it('CSRF 토큰으로 로그인 요청을 보내고 성공하면 대시보드로 이동한다', async () => {
    const assignSpy = vi.fn();
    const restoreLocation = stubLocationAssign(assignSpy, '/admin-ui/login');
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

    try {
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
      expect(assignSpy).toHaveBeenCalledWith('/admin-ui/');
    } finally {
      restoreLocation();
    }
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

  it('관리자 GraphQL 인증이 실패하면 로그인 화면으로 이동한다', async () => {
    const assignSpy = vi.fn();
    const restoreLocation = stubLocationAssign(assignSpy, '/admin-ui/');
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({
        headerName: 'X-CSRF-TOKEN',
        parameterName: '_csrf',
        token: 'csrf-token'
      }))
      .mockResolvedValueOnce(jsonResponse({
        timestamp: '2026-06-26T08:51:22.984Z',
        status: 403,
        error: 'Forbidden',
        path: '/admin/graphql'
      }, { status: 403 }));
    vi.stubGlobal('fetch', fetchMock);

    try {
      renderApp('/');

      await waitFor(() => {
        expect(assignSpy).toHaveBeenCalledWith('/admin-ui/login');
      });
    } finally {
      restoreLocation();
    }
  });

  it('유저 화면에서 목록을 보고 유저를 비활성화한다', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({
        headerName: 'X-CSRF-TOKEN',
        parameterName: '_csrf',
        token: 'csrf-token'
      }))
      .mockResolvedValueOnce(adminUsersResponse([
        {
          id: 'user-id',
          email: 'owner@example.com',
          displayName: '데이터 오너',
          role: 'USER',
          status: 'ACTIVE',
          deletedAt: null
        }
      ]))
      .mockResolvedValueOnce(graphqlResponse('disableUser', {
        id: 'user-id',
        email: 'owner@example.com',
        displayName: '데이터 오너',
        role: 'USER',
        status: 'DISABLED',
        deletedAt: null
      }))
      .mockResolvedValueOnce(adminUsersResponse([
        {
          id: 'user-id',
          email: 'owner@example.com',
          displayName: '데이터 오너',
          role: 'USER',
          status: 'DISABLED',
          deletedAt: null
        }
      ]));
    vi.stubGlobal('fetch', fetchMock);

    renderApp('/users');

    expect(await screen.findByText('owner@example.com')).toBeVisible();
    expect(screen.getByText('데이터 오너')).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: 'owner@example.com 비활성화' }));

    await waitFor(() => {
      expect(screen.getByText('DISABLED')).toBeVisible();
    });
    expect(confirmSpy).toHaveBeenCalledWith('이 유저를 비활성화할까요?');
    expect(fetchMock).toHaveBeenCalledTimes(4);
    expect(JSON.parse((fetchMock.mock.calls[2][1] as RequestInit).body as string).query).toContain('DisableUser');
  });

  it('유저 목록 조회가 실패하면 빈 목록 문구를 함께 보여주지 않는다', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({
        headerName: 'X-CSRF-TOKEN',
        parameterName: '_csrf',
        token: 'csrf-token'
      }))
      .mockResolvedValueOnce(jsonResponse({ errors: [{ message: 'fail' }] }));
    vi.stubGlobal('fetch', fetchMock);

    renderApp('/users');

    expect(await screen.findByText('유저를 불러오지 못했습니다.')).toBeVisible();
    expect(screen.queryByText('유저가 없습니다.')).not.toBeInTheDocument();
  });

  it('워크스페이스 화면에서 생성, 삭제, 복구를 수행한다', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const owner = {
      id: 'user-id',
      email: 'owner@example.com',
      displayName: '데이터 오너',
      role: 'USER',
      status: 'ACTIVE',
      deletedAt: null
    };
    const personal = workspaceFixture({ id: 'workspace-id', name: 'Personal' });
    const team = workspaceFixture({ id: 'team-id', name: 'Team A' });
    const deletedTeam = workspaceFixture({
      id: 'team-id',
      name: 'Team A',
      deletedAt: '2026-06-26T00:00:00Z'
    });
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({
        headerName: 'X-CSRF-TOKEN',
        parameterName: '_csrf',
        token: 'csrf-token'
      }))
      .mockResolvedValueOnce(adminWorkspaceManagementResponse({
        users: [owner],
        workspaces: [personal]
      }))
      .mockResolvedValueOnce(graphqlResponse('createWorkspace', team))
      .mockResolvedValueOnce(adminWorkspaceManagementResponse({
        users: [owner],
        workspaces: [personal, team]
      }))
      .mockResolvedValueOnce(graphqlResponse('softDeleteWorkspace', deletedTeam))
      .mockResolvedValueOnce(adminWorkspaceManagementResponse({
        users: [owner],
        workspaces: [personal, deletedTeam]
      }))
      .mockResolvedValueOnce(graphqlResponse('restoreWorkspace', team))
      .mockResolvedValueOnce(adminWorkspaceManagementResponse({
        users: [owner],
        workspaces: [personal, team]
      }));
    vi.stubGlobal('fetch', fetchMock);

    renderApp('/workspaces');

    expect(await screen.findByText('Personal')).toBeVisible();
    expect(screen.getByText('데이터 오너 · owner@example.com')).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: '워크스페이스 추가' }));
    fireEvent.change(screen.getByLabelText('이름'), {
      target: { value: 'Team A' }
    });
    fireEvent.click(screen.getByRole('button', { name: '저장' }));

    expect(await screen.findByText('Team A')).toBeVisible();
    const createBody = JSON.parse((fetchMock.mock.calls[2][1] as RequestInit).body as string);
    expect(createBody.query).toContain('CreateWorkspace');
    expect(createBody.variables.input).toMatchObject({
      name: 'Team A',
      ownerUserId: 'user-id'
    });

    fireEvent.click(screen.getByRole('button', { name: 'Team A 삭제' }));

    expect(await screen.findByText('DELETED')).toBeVisible();
    expect(confirmSpy).toHaveBeenCalledWith('이 워크스페이스를 삭제할까요?');
    expect(JSON.parse((fetchMock.mock.calls[4][1] as RequestInit).body as string).query)
      .toContain('SoftDeleteWorkspace');

    fireEvent.click(screen.getByRole('button', { name: 'Team A 복구' }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledTimes(8);
    });
    expect(JSON.parse((fetchMock.mock.calls[6][1] as RequestInit).body as string).query)
      .toContain('RestoreWorkspace');
  });

  it('데이터소스 화면에서 목록, 수동 수집, 수집 기록을 관리한다', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({
        headerName: 'X-CSRF-TOKEN',
        parameterName: '_csrf',
        token: 'csrf-token'
      }))
      .mockResolvedValueOnce(adminDataSourcesResponse([
        dataSourceFixture({ id: 'source-id', name: 'Notion 문서함' })
      ]))
      .mockResolvedValueOnce(graphqlResponse('requestDataSourceSync', {
        id: 'job-id',
        workspaceId: 'workspace-id',
        dataSourceId: 'source-id',
        triggerType: 'MANUAL',
        status: 'PENDING',
        errorMessage: null,
        startedAt: null,
        finishedAt: null,
        createdAt: '2026-06-22T00:00:00Z'
      }))
      .mockResolvedValueOnce(adminDataSourcesResponse([
        dataSourceFixture({ id: 'source-id', name: 'Notion 문서함', lastSyncedAt: '2026-06-22T00:00:00Z' })
      ]))
      .mockResolvedValueOnce(graphqlResponse('ingestionJobs', [
        {
          id: 'job-id',
          workspaceId: 'workspace-id',
          dataSourceId: 'source-id',
          triggerType: 'MANUAL',
          status: 'PENDING',
          errorMessage: null,
          startedAt: null,
          finishedAt: null,
          createdAt: '2026-06-22T00:00:00Z'
        }
      ]));
    vi.stubGlobal('fetch', fetchMock);

    renderApp('/data-sources');

    expect(await screen.findByText('Notion 문서함')).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: 'Notion 문서함 수동 수집' }));

    await waitFor(() => {
      expect(JSON.parse((fetchMock.mock.calls[2][1] as RequestInit).body as string).query).toContain('RequestDataSourceSync');
    });

    fireEvent.click(screen.getByRole('button', { name: 'Notion 문서함 수집 기록' }));

    expect(await screen.findByText('PENDING')).toBeVisible();
    expect(JSON.parse((fetchMock.mock.calls[4][1] as RequestInit).body as string).query).toContain('AdminIngestionJobs');
  });

  it('Notion 데이터소스를 만들 때 루트 페이지 ID를 함께 보낸다', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({
        headerName: 'X-CSRF-TOKEN',
        parameterName: '_csrf',
        token: 'csrf-token'
      }))
      .mockResolvedValueOnce(adminDataSourcesResponse([]))
      .mockResolvedValueOnce(adminDataSourceFormOptionsResponse({
        users: [
          {
            id: 'user-id',
            email: 'owner@example.com',
            displayName: '데이터 오너',
            role: 'USER',
            status: 'ACTIVE',
            deletedAt: null
          }
        ],
        workspaces: [
          {
            id: 'workspace-id',
            ownerUserId: 'user-id',
            name: 'Personal',
            deletedAt: null
          }
        ]
      }))
      .mockResolvedValueOnce(graphqlResponse('createDataSource', dataSourceFixture({
        id: 'notion-source-id',
        name: 'Notion wiki',
        notionRootPageId: 'root-page-id'
      })))
      .mockResolvedValueOnce(adminDataSourcesResponse([
        dataSourceFixture({
          id: 'notion-source-id',
          name: 'Notion wiki',
          notionRootPageId: 'root-page-id'
        })
      ]));
    vi.stubGlobal('fetch', fetchMock);

    renderApp('/data-sources');

    expect(await screen.findByText('데이터소스가 없습니다.')).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: '데이터소스 추가' }));
    expect(await screen.findByRole('option', { name: 'Personal' })).toBeVisible();
    expect(screen.getByRole('option', { name: '데이터 오너 · owner@example.com' })).toBeVisible();
    fireEvent.change(screen.getByLabelText('이름'), {
      target: { value: 'Notion wiki' }
    });
    fireEvent.change(screen.getByLabelText('워크스페이스'), {
      target: { value: 'workspace-id' }
    });
    fireEvent.change(screen.getByLabelText('소유 유저'), {
      target: { value: 'user-id' }
    });
    expect(screen.getByRole('option', { name: 'LOCAL_TEXT' })).toBeVisible();
    expect(screen.getByRole('option', { name: 'NOTION' })).toBeVisible();
    expect(screen.queryByRole('option', { name: 'GOOGLE_DRIVE' })).not.toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'SLACK' })).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('종류'), {
      target: { value: 'NOTION' }
    });
    fireEvent.change(screen.getByLabelText('Notion 루트 페이지 ID'), {
      target: { value: 'root-page-id' }
    });
    fireEvent.click(screen.getByRole('button', { name: '저장' }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledTimes(5);
    });
    expect(JSON.parse((fetchMock.mock.calls[2][1] as RequestInit).body as string).query)
      .toContain('AdminDataSourceFormOptions');
    const createBody = JSON.parse((fetchMock.mock.calls[3][1] as RequestInit).body as string);
    expect(createBody.variables.input).toMatchObject({
      name: 'Notion wiki',
      notionRootPageId: 'root-page-id',
      ownerUserId: 'user-id',
      type: 'NOTION',
      workspaceId: 'workspace-id'
    });
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

function stubLocationAssign(assign: (url: string) => void, pathname: string) {
  const originalLocation = window.location;
  Object.defineProperty(window, 'location', {
    configurable: true,
    value: {
      assign,
      href: `http://localhost${pathname}`,
      origin: 'http://localhost',
      pathname
    }
  });

  return () => {
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: originalLocation
    });
  };
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

function adminUsersResponse(items: Array<Record<string, unknown>>) {
  return graphqlResponse('users', {
    totalCount: items.length,
    items
  });
}

function adminDataSourcesResponse(items: Array<Record<string, unknown>>) {
  return graphqlResponse('dataSources', {
    totalCount: items.length,
    items
  });
}

function adminDataSourceFormOptionsResponse(options: {
  users: Array<Record<string, unknown>>;
  workspaces: Array<Record<string, unknown>>;
}) {
  return jsonResponse({
    data: {
      users: {
        totalCount: options.users.length,
        items: options.users
      },
      workspaces: {
        totalCount: options.workspaces.length,
        items: options.workspaces
      }
    }
  });
}

function adminWorkspaceManagementResponse(options: {
  users: Array<Record<string, unknown>>;
  workspaces: Array<Record<string, unknown>>;
}) {
  return jsonResponse({
    data: {
      users: {
        totalCount: options.users.length,
        items: options.users
      },
      workspaces: {
        totalCount: options.workspaces.length,
        items: options.workspaces
      }
    }
  });
}

function graphqlResponse(field: string, value: unknown) {
  return jsonResponse({
    data: {
      [field]: value
    }
  });
}

function dataSourceFixture(overrides: Record<string, unknown> = {}) {
  return {
    id: 'source-id',
    workspaceId: 'workspace-id',
    ownerUserId: 'user-id',
    type: 'NOTION',
    name: 'Notion 문서함',
    status: 'ACTIVE',
    syncMode: 'MANUAL',
    visibility: 'PRIVATE',
    notionRootPageId: null,
    lastSyncedAt: null,
    deletedAt: null,
    ...overrides
  };
}

function workspaceFixture(overrides: Record<string, unknown> = {}) {
  return {
    id: 'workspace-id',
    ownerUserId: 'user-id',
    name: 'Personal',
    deletedAt: null,
    ...overrides
  };
}
