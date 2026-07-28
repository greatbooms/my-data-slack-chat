import '@testing-library/jest-dom/vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
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
    vi.restoreAllMocks();
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

  it('브라우저가 지원하면 로그인 성공 후 비밀번호 저장을 요청한다', async () => {
    const assignSpy = vi.fn();
    const restoreLocation = stubLocationAssign(assignSpy, '/admin-ui/login');
    const storeCredential = vi.fn().mockResolvedValue(undefined);
    const PasswordCredential = vi.fn(function (
      this: { form: HTMLFormElement; id: string; type: string },
      form: HTMLFormElement
    ) {
      this.form = form;
      this.id = 'admin@example.com';
      this.type = 'password';
    });
    Object.defineProperty(window.navigator, 'credentials', {
      configurable: true,
      value: {
        store: storeCredential
      }
    });
    vi.stubGlobal('PasswordCredential', PasswordCredential);
    vi.stubGlobal('fetch', vi.fn()
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
      })));

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
        expect(storeCredential).toHaveBeenCalledTimes(1);
      });
      expect(PasswordCredential).toHaveBeenCalledWith(expect.any(HTMLFormElement));
      expect(assignSpy).toHaveBeenCalledWith('/admin-ui/');
    } finally {
      restoreLocation();
    }
  });

  it('비밀번호 저장 요청이 실패해도 로그인 성공 이동을 막지 않는다', async () => {
    const assignSpy = vi.fn();
    const restoreLocation = stubLocationAssign(assignSpy, '/admin-ui/login');
    Object.defineProperty(window.navigator, 'credentials', {
      configurable: true,
      value: {
        store: vi.fn().mockRejectedValue(new Error('save rejected'))
      }
    });
    vi.stubGlobal('PasswordCredential', vi.fn(function (
      this: { form: HTMLFormElement; id: string; type: string },
      form: HTMLFormElement
    ) {
      this.form = form;
      this.id = 'admin@example.com';
      this.type = 'password';
    }));
    vi.stubGlobal('fetch', vi.fn()
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
      })));

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
        expect(assignSpy).toHaveBeenCalledWith('/admin-ui/');
      });
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

  it('외부 계정 화면에서 Slack 매핑을 생성하고 삭제한다', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const owner = {
      id: 'user-id',
      email: 'owner@example.com',
      displayName: '데이터 오너',
      role: 'USER',
      status: 'ACTIVE',
      deletedAt: null
    };
    const workspace = workspaceFixture({ id: 'workspace-id', name: 'Personal' });
    const identity = externalIdentityFixture({
      id: 'identity-id',
      workspaceId: 'workspace-id',
      userId: 'user-id',
      externalWorkspaceId: 'T123',
      externalUserId: 'U123',
      email: 'slack@example.com',
      displayName: 'Slack Person'
    });
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({
        headerName: 'X-CSRF-TOKEN',
        parameterName: '_csrf',
        token: 'csrf-token'
      }))
      .mockResolvedValueOnce(adminExternalIdentityManagementResponse({
        externalIdentities: [],
        users: [owner],
        workspaces: [workspace]
      }))
      .mockResolvedValueOnce(graphqlResponse('createExternalIdentity', identity))
      .mockResolvedValueOnce(adminExternalIdentityManagementResponse({
        externalIdentities: [identity],
        users: [owner],
        workspaces: [workspace]
      }))
      .mockResolvedValueOnce(graphqlResponse('deleteExternalIdentity', true))
      .mockResolvedValueOnce(adminExternalIdentityManagementResponse({
        externalIdentities: [],
        users: [owner],
        workspaces: [workspace]
      }));
    vi.stubGlobal('fetch', fetchMock);

    renderApp('/external-identities');

    expect(await screen.findByText('외부 계정 매핑이 없습니다.')).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: 'Slack 매핑 추가' }));
    expect(screen.getByLabelText('Slack 팀 ID 도움말')).toHaveAttribute(
      'aria-describedby',
      'slack-team-id-help'
    );
    expect(screen.getByText(/Slack 웹에서 아무 채널에 들어간 뒤 주소창의/)).toBeInTheDocument();
    expect(screen.getByLabelText('Slack 유저 ID 도움말')).toHaveAttribute(
      'aria-describedby',
      'slack-user-id-help'
    );
    expect(screen.getByText(/사용자 프로필의 멤버 ID 복사/)).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Slack 팀 ID'), {
      target: { value: 'T123' }
    });
    fireEvent.change(screen.getByLabelText('Slack 유저 ID'), {
      target: { value: 'U123' }
    });
    fireEvent.change(screen.getByLabelText('이메일'), {
      target: { value: 'slack@example.com' }
    });
    fireEvent.change(screen.getByLabelText('표시 이름'), {
      target: { value: 'Slack Person' }
    });
    fireEvent.click(screen.getByRole('button', { name: '저장' }));

    expect(await screen.findByText('T123')).toBeVisible();
    expect(screen.getByText('U123')).toBeVisible();
    expect(screen.getByText('데이터 오너 · owner@example.com')).toBeVisible();
    expect(screen.getByText('Personal')).toBeVisible();
    const createBody = JSON.parse((fetchMock.mock.calls[2][1] as RequestInit).body as string);
    expect(createBody.query).toContain('CreateExternalIdentity');
    expect(createBody.variables.input).toMatchObject({
      provider: 'SLACK',
      workspaceId: 'workspace-id',
      userId: 'user-id',
      externalWorkspaceId: 'T123',
      externalUserId: 'U123',
      email: 'slack@example.com',
      displayName: 'Slack Person'
    });

    fireEvent.click(screen.getByRole('button', { name: 'Slack Person 삭제' }));

    expect(await screen.findByText('외부 계정 매핑이 없습니다.')).toBeVisible();
    expect(confirmSpy).toHaveBeenCalledWith('이 외부 계정 매핑을 삭제할까요?');
    expect(JSON.parse((fetchMock.mock.calls[4][1] as RequestInit).body as string).query)
      .toContain('DeleteExternalIdentity');
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
        succeededItemCount: 0,
        failedItemCount: 0,
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
          succeededItemCount: 0,
          failedItemCount: 0,
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

  it('PARTIAL_FAILED 수집 job의 성공·실패 건수와 선택한 실패 상세를 표시한다', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({
        headerName: 'X-CSRF-TOKEN',
        parameterName: '_csrf',
        token: 'csrf-token'
      }))
      .mockResolvedValueOnce(adminDataSourcesResponse([
        dataSourceFixture({ id: 'source-id', name: 'Notion 문서함' })
      ]))
      .mockResolvedValueOnce(graphqlResponse('ingestionJobs', [
        ingestionJobFixture({
          id: 'partial-job',
          status: 'PARTIAL_FAILED',
          errorMessage: '전체 3개 중 1개 실패',
          succeededItemCount: 2,
          failedItemCount: 1,
          startedAt: '2026-07-13T01:00:00Z',
          finishedAt: '2026-07-13T01:01:00Z',
          createdAt: '2026-07-13T01:00:00Z'
        })
      ]))
      .mockResolvedValueOnce(graphqlResponse('ingestionJobItems', {
        items: [{
          externalId: 'database:bad-database',
          documentId: null,
          status: 'FAILED',
          reason: '[DATABASE] Root / Hidden DB (RETRIEVE): 원본 database를 integration에 공유하세요',
          processedAt: '2026-07-13T01:00:30Z'
        }],
        hasNextPage: false,
        endCursor: null
      }));
    vi.stubGlobal('fetch', fetchMock);

    renderApp('/data-sources');
    fireEvent.click(await screen.findByRole('button', { name: 'Notion 문서함 수집 기록' }));

    const partialStatus = await screen.findByText('PARTIAL_FAILED');
    const jobRow = partialStatus.closest('tr');
    expect(jobRow).not.toBeNull();
    expect(within(jobRow as HTMLTableRowElement).getByText('2')).toBeVisible();
    expect(within(jobRow as HTMLTableRowElement).getByText('1')).toBeVisible();
    expect(fetchMock.mock.calls.some((call) => {
      const body = JSON.parse((call[1] as RequestInit | undefined)?.body as string ?? '{}');
      return body.query?.includes('AdminIngestionJobItems');
    })).toBe(false);

    fireEvent.click(screen.getByRole('button', { name: 'partial-job 실패 상세 보기' }));

    expect(await screen.findByText('database:bad-database')).toBeVisible();
    expect(screen.getByText(/원본 database를 integration에 공유하세요/)).toBeVisible();
    expect(JSON.parse((fetchMock.mock.calls[3][1] as RequestInit).body as string).query)
      .toContain('AdminIngestionJobItems');
  });

  it('job 선택 전과 SUCCEEDED job에는 실패 item query를 보내지 않는다', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({
        headerName: 'X-CSRF-TOKEN',
        parameterName: '_csrf',
        token: 'csrf-token'
      }))
      .mockResolvedValueOnce(adminDataSourcesResponse([
        dataSourceFixture({ id: 'source-id', name: 'Notion 문서함' })
      ]))
      .mockResolvedValueOnce(graphqlResponse('ingestionJobs', [
        ingestionJobFixture({
          id: 'succeeded-job',
          status: 'SUCCEEDED',
          succeededItemCount: 3,
          failedItemCount: 0
        })
      ]));
    vi.stubGlobal('fetch', fetchMock);

    renderApp('/data-sources');

    await screen.findByText('Notion 문서함');
    expect(fetchMock.mock.calls.some(isIngestionJobItemsCall)).toBe(false);

    fireEvent.click(screen.getByRole('button', { name: 'Notion 문서함 수집 기록' }));

    expect(await screen.findByText('SUCCEEDED')).toBeVisible();
    expect(screen.queryByRole('button', { name: /실패 상세 보기/ })).not.toBeInTheDocument();
    expect(fetchMock.mock.calls.some(isIngestionJobItemsCall)).toBe(false);
  });

  it('실패 항목 더 보기로 다음 cursor 페이지를 이어 붙인다', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({
        headerName: 'X-CSRF-TOKEN',
        parameterName: '_csrf',
        token: 'csrf-token'
      }))
      .mockResolvedValueOnce(adminDataSourcesResponse([
        dataSourceFixture({ id: 'source-id', name: 'Notion 문서함' })
      ]))
      .mockResolvedValueOnce(graphqlResponse('ingestionJobs', [
        ingestionJobFixture({
          id: 'partial-job',
          status: 'PARTIAL_FAILED',
          succeededItemCount: 1,
          failedItemCount: 2
        })
      ]))
      .mockResolvedValueOnce(graphqlResponse('ingestionJobItems', {
        items: [ingestionJobItemFixture({ externalId: 'database:failed-1' })],
        hasNextPage: true,
        endCursor: 'cursor-1'
      }))
      .mockResolvedValueOnce(graphqlResponse('ingestionJobItems', {
        items: [ingestionJobItemFixture({ externalId: 'database:failed-2' })],
        hasNextPage: false,
        endCursor: null
      }));
    vi.stubGlobal('fetch', fetchMock);

    renderApp('/data-sources');
    fireEvent.click(await screen.findByRole('button', { name: 'Notion 문서함 수집 기록' }));
    fireEvent.click(await screen.findByRole('button', { name: 'partial-job 실패 상세 보기' }));

    expect(await screen.findByText('database:failed-1')).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: '실패 항목 더 보기' }));

    expect(await screen.findByText('database:failed-2')).toBeVisible();
    expect(screen.getAllByText('database:failed-1')).toHaveLength(1);
    expect(screen.queryByRole('button', { name: '실패 항목 더 보기' })).not.toBeInTheDocument();
    const nextPageBody = JSON.parse((fetchMock.mock.calls[4][1] as RequestInit).body as string);
    expect(nextPageBody.variables).toMatchObject({
      jobId: 'partial-job',
      status: 'FAILED',
      first: 50,
      after: 'cursor-1'
    });
  });

  it('실패 상세 조회 실패 상태를 표시하고 job 목록을 유지한다', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({
        headerName: 'X-CSRF-TOKEN',
        parameterName: '_csrf',
        token: 'csrf-token'
      }))
      .mockResolvedValueOnce(adminDataSourcesResponse([
        dataSourceFixture({ id: 'source-id', name: 'Notion 문서함' })
      ]))
      .mockResolvedValueOnce(graphqlResponse('ingestionJobs', [
        ingestionJobFixture({
          id: 'failed-job',
          status: 'FAILED',
          errorMessage: '모든 항목 실패',
          failedItemCount: 1
        })
      ]))
      .mockRejectedValueOnce(new Error('network failed'));
    vi.stubGlobal('fetch', fetchMock);

    renderApp('/data-sources');
    fireEvent.click(await screen.findByRole('button', { name: 'Notion 문서함 수집 기록' }));
    fireEvent.click(await screen.findByRole('button', { name: 'failed-job 실패 상세 보기' }));

    expect(await screen.findByText('실패 상세를 불러오지 못했습니다.')).toBeVisible();
    expect(screen.getByText('FAILED')).toBeVisible();
    expect(screen.getByText('모든 항목 실패')).toBeVisible();
  });

  it('선택한 실패 job이 최신 목록에서 성공으로 바뀌면 stale 상세를 닫고 item을 재조회하지 않는다', async () => {
    let jobsRequestCount = 0;
    let itemsRequestCount = 0;
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      if (!init?.body) {
        return jsonResponse({
          headerName: 'X-CSRF-TOKEN',
          parameterName: '_csrf',
          token: 'csrf-token'
        });
      }

      const body = JSON.parse(init.body as string);
      if (body.query?.includes('AdminDataSources')) {
        return adminDataSourcesResponse([
          dataSourceFixture({ id: 'source-id', name: 'Notion 문서함' })
        ]);
      }
      if (body.query?.includes('AdminIngestionJobs')) {
        jobsRequestCount += 1;
        return graphqlResponse('ingestionJobs', [
          ingestionJobFixture(jobsRequestCount === 1
            ? {
                id: 'failed-job',
                status: 'FAILED',
                errorMessage: '한 항목 실패',
                failedItemCount: 1
              }
            : {
                id: 'failed-job',
                status: 'SUCCEEDED',
                errorMessage: null,
                succeededItemCount: 1,
                failedItemCount: 0
              })
        ]);
      }
      if (body.query?.includes('AdminIngestionJobItems')) {
        itemsRequestCount += 1;
        return graphqlResponse('ingestionJobItems', {
          items: [ingestionJobItemFixture({ externalId: 'database:stale-failure' })],
          hasNextPage: false,
          endCursor: null
        });
      }
      if (body.query?.includes('RequestDataSourceSync')) {
        return graphqlResponse('requestDataSourceSync', ingestionJobFixture({
          id: 'new-job',
          status: 'PENDING'
        }));
      }
      throw new Error('예상하지 못한 요청입니다.');
    });
    vi.stubGlobal('fetch', fetchMock);

    renderApp('/data-sources');
    fireEvent.click(await screen.findByRole('button', { name: 'Notion 문서함 수집 기록' }));
    fireEvent.click(await screen.findByRole('button', { name: 'failed-job 실패 상세 보기' }));
    expect(await screen.findByText('database:stale-failure')).toBeVisible();

    fireEvent.click(screen.getByRole('button', { name: 'Notion 문서함 수동 수집' }));

    expect(await screen.findByText('SUCCEEDED')).toBeVisible();
    await waitFor(() => {
      expect(jobsRequestCount).toBe(2);
      expect(screen.queryByLabelText('failed-job 실패 상세')).not.toBeInTheDocument();
    });
    expect(screen.queryByRole('button', { name: 'failed-job 실패 상세 보기' })).not.toBeInTheDocument();
    expect(itemsRequestCount).toBe(1);
  });

  it('Notion 페이지 데이터소스를 만들 때 페이지 링크를 함께 보낸다', async () => {
    const pageLink = 'https://www.notion.so/greatbooms/Project-Wiki-248104cd477e80fdb757e945d38000bd?pvs=4';
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
        notionRootPageId: '248104cd-477e-80fd-b757-e945d38000bd'
      })))
      .mockResolvedValueOnce(adminDataSourcesResponse([
        dataSourceFixture({
          id: 'notion-source-id',
          name: 'Notion wiki',
          notionRootPageId: '248104cd-477e-80fd-b757-e945d38000bd'
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
    expect(screen.getByRole('option', { name: 'SLACK' })).toBeVisible();
    expect(screen.getByRole('option', { name: 'GOOGLE_DRIVE' })).toBeVisible();
    fireEvent.change(screen.getByLabelText('종류'), {
      target: { value: 'NOTION' }
    });
    const pageInput = screen.getByLabelText('Notion 루트 페이지 링크 또는 ID');
    expect(pageInput).toHaveAttribute(
      'placeholder',
      'https://www.notion.so/workspace/Project-Wiki-...'
    );
    fireEvent.change(pageInput, {
      target: { value: pageLink }
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
      notionRootPageId: pageLink,
      ownerUserId: 'user-id',
      type: 'NOTION',
      workspaceId: 'workspace-id'
    });
    expect(createBody.variables.input.notionDatabaseId).toBeUndefined();
  });

  it('잘못된 Notion 페이지 링크 저장 오류를 폼에 표시한다', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({
        headerName: 'X-CSRF-TOKEN',
        parameterName: '_csrf',
        token: 'csrf-token'
      }))
      .mockResolvedValueOnce(adminDataSourcesResponse([]))
      .mockResolvedValueOnce(adminDataSourceFormOptionsResponse({
        users: [{
          id: 'user-id',
          email: 'owner@example.com',
          displayName: '데이터 오너',
          role: 'USER',
          status: 'ACTIVE',
          deletedAt: null
        }],
        workspaces: [{
          id: 'workspace-id',
          ownerUserId: 'user-id',
          name: 'Personal',
          deletedAt: null
        }]
      }))
      .mockResolvedValueOnce(jsonResponse({
        data: { createDataSource: null },
        errors: [{ message: 'notionRootPageId 형식이 올바르지 않습니다' }]
      }));
    vi.stubGlobal('fetch', fetchMock);

    renderApp('/data-sources');

    expect(await screen.findByText('데이터소스가 없습니다.')).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: '데이터소스 추가' }));
    expect(await screen.findByRole('option', { name: 'Personal' })).toBeVisible();
    fireEvent.change(screen.getByLabelText('이름'), {
      target: { value: 'Invalid Notion page' }
    });
    fireEvent.change(screen.getByLabelText('워크스페이스'), {
      target: { value: 'workspace-id' }
    });
    fireEvent.change(screen.getByLabelText('소유 유저'), {
      target: { value: 'user-id' }
    });
    fireEvent.change(screen.getByLabelText('종류'), {
      target: { value: 'NOTION' }
    });
    fireEvent.change(screen.getByLabelText('Notion 루트 페이지 링크 또는 ID'), {
      target: { value: 'https://www.notion.so/greatbooms/not-a-page-id' }
    });
    fireEvent.click(screen.getByRole('button', { name: '저장' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'notionRootPageId 형식이 올바르지 않습니다'
    );
    expect(screen.getByRole('dialog', { name: '데이터소스 추가' })).toBeVisible();
    expect(fetchMock).toHaveBeenCalledTimes(4);
  });

  it('Notion 데이터베이스 데이터소스를 만들 때 데이터베이스 링크를 함께 보낸다', async () => {
    const databaseLink = 'https://www.notion.so/greatbooms/Roadmap-248104cd477e80fdb757e945d38000bd?v=248104cd477e80afbc30000bd28de8f9';
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
        id: 'notion-database-source-id',
        name: 'Notion database',
        notionDatabaseId: '248104cd-477e-80fd-b757-e945d38000bd'
      })))
      .mockResolvedValueOnce(adminDataSourcesResponse([
        dataSourceFixture({
          id: 'notion-database-source-id',
          name: 'Notion database',
          notionDatabaseId: '248104cd-477e-80fd-b757-e945d38000bd'
        })
      ]));
    vi.stubGlobal('fetch', fetchMock);

    renderApp('/data-sources');

    expect(await screen.findByText('데이터소스가 없습니다.')).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: '데이터소스 추가' }));
    expect(await screen.findByRole('option', { name: 'Personal' })).toBeVisible();
    fireEvent.change(screen.getByLabelText('이름'), {
      target: { value: 'Notion database' }
    });
    fireEvent.change(screen.getByLabelText('워크스페이스'), {
      target: { value: 'workspace-id' }
    });
    fireEvent.change(screen.getByLabelText('소유 유저'), {
      target: { value: 'user-id' }
    });
    fireEvent.change(screen.getByLabelText('종류'), {
      target: { value: 'NOTION' }
    });
    fireEvent.change(screen.getByLabelText('Notion 수집 대상'), {
      target: { value: 'DATABASE' }
    });
    expect(screen.queryByLabelText('Notion 루트 페이지 링크 또는 ID')).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Notion 데이터베이스 링크 또는 ID'), {
      target: { value: databaseLink }
    });
    fireEvent.click(screen.getByRole('button', { name: '저장' }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledTimes(5);
    });
    const createBody = JSON.parse((fetchMock.mock.calls[3][1] as RequestInit).body as string);
    expect(createBody.variables.input).toMatchObject({
      name: 'Notion database',
      notionDatabaseId: databaseLink,
      ownerUserId: 'user-id',
      type: 'NOTION',
      workspaceId: 'workspace-id'
    });
    expect(createBody.variables.input.notionRootPageId).toBeUndefined();
  });

  it('Google Drive 데이터소스를 만들 때 폴더 링크를 함께 보낸다', async () => {
    const folderLink = 'https://drive.google.com/drive/folders/folder-123';
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
        id: 'google-drive-source-id',
        name: 'Google Drive folder',
        type: 'GOOGLE_DRIVE',
        driveFolderId: 'folder-123'
      })))
      .mockResolvedValueOnce(adminDataSourcesResponse([
        dataSourceFixture({
          id: 'google-drive-source-id',
          name: 'Google Drive folder',
          type: 'GOOGLE_DRIVE',
          driveFolderId: 'folder-123'
        })
      ]));
    vi.stubGlobal('fetch', fetchMock);

    renderApp('/data-sources');

    expect(await screen.findByText('데이터소스가 없습니다.')).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: '데이터소스 추가' }));
    expect(await screen.findByRole('option', { name: 'Personal' })).toBeVisible();
    fireEvent.change(screen.getByLabelText('이름'), {
      target: { value: 'Google Drive folder' }
    });
    fireEvent.change(screen.getByLabelText('워크스페이스'), {
      target: { value: 'workspace-id' }
    });
    fireEvent.change(screen.getByLabelText('소유 유저'), {
      target: { value: 'user-id' }
    });
    fireEvent.change(screen.getByLabelText('종류'), {
      target: { value: 'GOOGLE_DRIVE' }
    });
    const folderInput = screen.getByLabelText('Google Drive 폴더 링크 또는 ID');
    expect(folderInput).toBeVisible();
    fireEvent.change(folderInput, {
      target: { value: folderLink }
    });
    fireEvent.click(screen.getByRole('button', { name: '저장' }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledTimes(5);
    });
    const createBody = JSON.parse((fetchMock.mock.calls[3][1] as RequestInit).body as string);
    expect(createBody.variables.input).toMatchObject({
      driveFolderId: folderLink,
      name: 'Google Drive folder',
      ownerUserId: 'user-id',
      type: 'GOOGLE_DRIVE',
      workspaceId: 'workspace-id'
    });
  });

  it('Slack 데이터소스를 만들 때 채널 ID를 함께 보낸다', async () => {
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
        id: 'slack-source-id',
        name: 'Slack channel',
        type: 'SLACK',
        slackChannelId: 'C1234567890',
        slackWorkspaceUrl: 'https://example.slack.com'
      })))
      .mockResolvedValueOnce(adminDataSourcesResponse([
        dataSourceFixture({
          id: 'slack-source-id',
          name: 'Slack channel',
          type: 'SLACK',
          slackChannelId: 'C1234567890',
          slackWorkspaceUrl: 'https://example.slack.com'
        })
      ]));
    vi.stubGlobal('fetch', fetchMock);

    renderApp('/data-sources');

    expect(await screen.findByText('데이터소스가 없습니다.')).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: '데이터소스 추가' }));
    expect(await screen.findByRole('option', { name: 'Personal' })).toBeVisible();
    fireEvent.change(screen.getByLabelText('이름'), {
      target: { value: 'Slack channel' }
    });
    fireEvent.change(screen.getByLabelText('워크스페이스'), {
      target: { value: 'workspace-id' }
    });
    fireEvent.change(screen.getByLabelText('소유 유저'), {
      target: { value: 'user-id' }
    });
    fireEvent.change(screen.getByLabelText('종류'), {
      target: { value: 'SLACK' }
    });
    expect(screen.queryByLabelText('Notion 루트 페이지 링크 또는 ID')).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Slack 채널 ID'), {
      target: { value: 'C1234567890' }
    });
    fireEvent.change(screen.getByLabelText('Slack 워크스페이스 URL'), {
      target: { value: 'https://example.slack.com' }
    });
    fireEvent.click(screen.getByRole('button', { name: '저장' }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledTimes(5);
    });
    const createBody = JSON.parse((fetchMock.mock.calls[3][1] as RequestInit).body as string);
    expect(createBody.variables.input).toMatchObject({
      name: 'Slack channel',
      ownerUserId: 'user-id',
      slackChannelId: 'C1234567890',
      slackWorkspaceUrl: 'https://example.slack.com',
      type: 'SLACK',
      workspaceId: 'workspace-id'
    });
    expect(createBody.variables.input.notionRootPageId).toBeUndefined();
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

function adminExternalIdentityManagementResponse(options: {
  externalIdentities: Array<Record<string, unknown>>;
  users: Array<Record<string, unknown>>;
  workspaces: Array<Record<string, unknown>>;
}) {
  return jsonResponse({
    data: {
      externalIdentities: {
        totalCount: options.externalIdentities.length,
        items: options.externalIdentities
      },
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
    notionDatabaseId: null,
    slackChannelId: null,
    slackWorkspaceUrl: null,
    driveFolderId: null,
    lastSyncedAt: null,
    deletedAt: null,
    embeddingCoverage: { model: 'deterministic-1536', totalChunks: 0, coveredChunks: 0 },
    ...overrides
  };
}

function ingestionJobFixture(overrides: Record<string, unknown> = {}) {
  return {
    id: 'job-id',
    workspaceId: 'workspace-id',
    dataSourceId: 'source-id',
    triggerType: 'MANUAL',
    status: 'PENDING',
    errorMessage: null,
    succeededItemCount: 0,
    failedItemCount: 0,
    startedAt: null,
    finishedAt: null,
    createdAt: '2026-07-13T01:00:00Z',
    ...overrides
  };
}

function ingestionJobItemFixture(overrides: Record<string, unknown> = {}) {
  return {
    externalId: 'database:failed',
    documentId: null,
    status: 'FAILED',
    reason: '[DATABASE] Root / Hidden DB (RETRIEVE): 원본 database를 integration에 공유하세요',
    processedAt: '2026-07-13T01:00:30Z',
    ...overrides
  };
}

function isIngestionJobItemsCall(call: unknown[]) {
  const body = JSON.parse((call[1] as RequestInit | undefined)?.body as string ?? '{}');
  return body.query?.includes('AdminIngestionJobItems') === true;
}

function externalIdentityFixture(overrides: Record<string, unknown> = {}) {
  return {
    id: 'identity-id',
    workspaceId: 'workspace-id',
    userId: 'user-id',
    provider: 'SLACK',
    externalWorkspaceId: 'T123',
    externalUserId: 'U123',
    email: 'slack@example.com',
    displayName: 'Slack Person',
    principalKey: 'SLACK_USER:T123:U123',
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
