import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Ban, KeyRound, Pencil, Plus, RotateCcw, Trash2 } from 'lucide-react';
import { useState } from 'react';
import {
  createAdminUser,
  disableAdminUser,
  fetchAdminUsers,
  resetAdminUserPassword,
  restoreAdminUser,
  softDeleteAdminUser,
  updateAdminUser
} from '../api/adminGraphql';
import { useFragment } from '../generated/fragment-masking';
import {
  UserFieldsFragmentDoc,
  type UserFieldsFragment
} from '../generated/graphql';
import UserFormDialog, { type UserFormValues } from './UserFormDialog';

const USERS_QUERY_KEY = ['admin-users'];
const DASHBOARD_QUERY_KEY = ['viewer-and-dashboard'];

function UsersPage() {
  const queryClient = useQueryClient();
  const [editingUser, setEditingUser] = useState<UserFieldsFragment | null>(null);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const usersQuery = useQuery({
    queryKey: USERS_QUERY_KEY,
    queryFn: fetchAdminUsers
  });
  const users = useFragment(UserFieldsFragmentDoc, usersQuery.data?.users.items ?? []);

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: USERS_QUERY_KEY });
    queryClient.invalidateQueries({ queryKey: DASHBOARD_QUERY_KEY });
  };
  const saveUserMutation = useMutation({
    mutationFn: async (values: UserFormValues): Promise<unknown> => {
      if (editingUser) {
        return await updateAdminUser(editingUser.id, {
          displayName: values.displayName,
          role: values.role,
          status: values.status
        });
      }

      return await createAdminUser({
        displayName: values.displayName,
        email: values.email,
        password: values.password,
        role: values.role
      });
    },
    onSuccess: () => {
      refresh();
      closeForm();
    }
  });
  const disableMutation = useMutation({
    mutationFn: disableAdminUser,
    onSuccess: refresh
  });
  const deleteMutation = useMutation({
    mutationFn: softDeleteAdminUser,
    onSuccess: refresh
  });
  const restoreMutation = useMutation({
    mutationFn: restoreAdminUser,
    onSuccess: refresh
  });
  const resetPasswordMutation = useMutation({
    mutationFn: ({ id, password }: { id: string; password: string }) => resetAdminUserPassword(id, password),
    onSuccess: refresh
  });

  function openCreateForm() {
    setEditingUser(null);
    setIsFormOpen(true);
  }

  function openEditForm(user: UserFieldsFragment) {
    setEditingUser(user);
    setIsFormOpen(true);
  }

  function closeForm() {
    setEditingUser(null);
    setIsFormOpen(false);
  }

  function handleDisable(user: UserFieldsFragment) {
    if (window.confirm('이 유저를 비활성화할까요?')) {
      disableMutation.mutate(user.id);
    }
  }

  function handleDelete(user: UserFieldsFragment) {
    if (window.confirm('이 유저를 삭제할까요?')) {
      deleteMutation.mutate(user.id);
    }
  }

  function handleRestore(user: UserFieldsFragment) {
    restoreMutation.mutate(user.id);
  }

  function handleResetPassword(user: UserFieldsFragment) {
    const password = window.prompt('새 임시 비밀번호를 입력하세요');
    if (password && password.length >= 8) {
      resetPasswordMutation.mutate({ id: user.id, password });
    }
  }

  return (
    <section className="management-page" aria-label="유저 관리">
      <header className="section-heading">
        <div>
          <h2>유저</h2>
          <p>{usersQuery.data?.users.totalCount ?? 0}명</p>
        </div>
        <button type="button" className="primary-button" onClick={openCreateForm}>
          <Plus size={16} aria-hidden="true" />
          유저 추가
        </button>
      </header>

      <div className="table-shell">
        {usersQuery.isLoading ? <p className="state-text">유저를 불러오는 중입니다.</p> : null}
        {usersQuery.isError ? <p className="state-text">유저를 불러오지 못했습니다.</p> : null}
        {!usersQuery.isLoading && !usersQuery.isError && users.length === 0 ? (
          <p className="state-text">유저가 없습니다.</p>
        ) : null}
        {users.length > 0 ? (
          <table>
            <thead>
              <tr>
                <th>이메일</th>
                <th>이름</th>
                <th>역할</th>
                <th>상태</th>
                <th>삭제</th>
                <th>작업</th>
              </tr>
            </thead>
            <tbody>
              {users.map((user) => (
                <tr key={user.id}>
                  <td>{user.email}</td>
                  <td>{user.displayName}</td>
                  <td>
                    <span className="status-badge">{user.role}</span>
                  </td>
                  <td>
                    <span className="status-badge">{user.status}</span>
                  </td>
                  <td>{user.deletedAt ? formatDate(user.deletedAt) : '-'}</td>
                  <td>
                    <div className="row-actions">
                      <button
                        type="button"
                        className="icon-button"
                        aria-label={`${user.email} 수정`}
                        title="수정"
                        onClick={() => openEditForm(user)}
                      >
                        <Pencil size={16} aria-hidden="true" />
                      </button>
                      {!user.deletedAt && user.status === 'ACTIVE' ? (
                        <button
                          type="button"
                          className="icon-button"
                          aria-label={`${user.email} 비활성화`}
                          title="비활성화"
                          disabled={disableMutation.isPending}
                          onClick={() => handleDisable(user)}
                        >
                          <Ban size={16} aria-hidden="true" />
                        </button>
                      ) : null}
                      {!user.deletedAt ? (
                        <button
                          type="button"
                          className="icon-button"
                          aria-label={`${user.email} 삭제`}
                          title="삭제"
                          disabled={deleteMutation.isPending}
                          onClick={() => handleDelete(user)}
                        >
                          <Trash2 size={16} aria-hidden="true" />
                        </button>
                      ) : (
                        <button
                          type="button"
                          className="icon-button"
                          aria-label={`${user.email} 복구`}
                          title="복구"
                          disabled={restoreMutation.isPending}
                          onClick={() => handleRestore(user)}
                        >
                          <RotateCcw size={16} aria-hidden="true" />
                        </button>
                      )}
                      <button
                        type="button"
                        className="icon-button"
                        aria-label={`${user.email} 비밀번호 초기화`}
                        title="비밀번호 초기화"
                        disabled={resetPasswordMutation.isPending}
                        onClick={() => handleResetPassword(user)}
                      >
                        <KeyRound size={16} aria-hidden="true" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : null}
      </div>

      {isFormOpen ? (
        <UserFormDialog
          isSubmitting={saveUserMutation.isPending}
          user={editingUser}
          onClose={closeForm}
          onSubmit={(values) => saveUserMutation.mutate(values)}
        />
      ) : null}
    </section>
  );
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'short',
    timeStyle: 'short'
  }).format(new Date(value));
}

export default UsersPage;
