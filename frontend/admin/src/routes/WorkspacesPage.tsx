import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Pencil, Plus, RotateCcw, Trash2 } from 'lucide-react';
import { useState } from 'react';
import {
  createAdminWorkspace,
  fetchAdminWorkspaceManagement,
  restoreAdminWorkspace,
  softDeleteAdminWorkspace,
  updateAdminWorkspace
} from '../api/adminGraphql';
import { useFragment } from '../generated/fragment-masking';
import {
  UserFieldsFragmentDoc,
  WorkspaceFieldsFragmentDoc,
  type WorkspaceFieldsFragment
} from '../generated/graphql';
import WorkspaceFormDialog, { type WorkspaceFormValues } from './WorkspaceFormDialog';

const WORKSPACES_QUERY_KEY = ['admin-workspaces'];
const DATA_SOURCE_FORM_OPTIONS_QUERY_KEY = ['admin-data-source-form-options'];

function WorkspacesPage() {
  const queryClient = useQueryClient();
  const [editingWorkspace, setEditingWorkspace] = useState<WorkspaceFieldsFragment | null>(null);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const workspacesQuery = useQuery({
    queryKey: WORKSPACES_QUERY_KEY,
    queryFn: () => fetchAdminWorkspaceManagement(true)
  });
  const workspaces = useFragment(WorkspaceFieldsFragmentDoc, workspacesQuery.data?.workspaces.items ?? []);
  const users = useFragment(UserFieldsFragmentDoc, workspacesQuery.data?.users.items ?? []);
  const usersById = new Map(users.map((user) => [user.id, user]));

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: WORKSPACES_QUERY_KEY });
    queryClient.invalidateQueries({ queryKey: DATA_SOURCE_FORM_OPTIONS_QUERY_KEY });
  };
  const saveWorkspaceMutation = useMutation({
    mutationFn: async (values: WorkspaceFormValues): Promise<unknown> => {
      if (editingWorkspace) {
        return await updateAdminWorkspace(editingWorkspace.id, {
          name: values.name,
          ownerUserId: values.ownerUserId
        });
      }

      return await createAdminWorkspace({
        name: values.name,
        ownerUserId: values.ownerUserId
      });
    },
    onSuccess: () => {
      refresh();
      closeForm();
    }
  });
  const deleteMutation = useMutation({
    mutationFn: softDeleteAdminWorkspace,
    onSuccess: refresh
  });
  const restoreMutation = useMutation({
    mutationFn: restoreAdminWorkspace,
    onSuccess: refresh
  });

  function openCreateForm() {
    setEditingWorkspace(null);
    setIsFormOpen(true);
  }

  function openEditForm(workspace: WorkspaceFieldsFragment) {
    setEditingWorkspace(workspace);
    setIsFormOpen(true);
  }

  function closeForm() {
    setEditingWorkspace(null);
    setIsFormOpen(false);
  }

  function handleDelete(workspace: WorkspaceFieldsFragment) {
    if (window.confirm('이 워크스페이스를 삭제할까요?')) {
      deleteMutation.mutate(workspace.id);
    }
  }

  function handleRestore(workspace: WorkspaceFieldsFragment) {
    restoreMutation.mutate(workspace.id);
  }

  return (
    <section className="management-page" aria-label="워크스페이스 관리">
      <header className="section-heading">
        <div>
          <h2>워크스페이스</h2>
          <p>{workspacesQuery.data?.workspaces.totalCount ?? 0}개</p>
        </div>
        <button type="button" className="primary-button" onClick={openCreateForm}>
          <Plus size={16} aria-hidden="true" />
          워크스페이스 추가
        </button>
      </header>

      <div className="table-shell">
        {workspacesQuery.isLoading ? <p className="state-text">워크스페이스를 불러오는 중입니다.</p> : null}
        {workspacesQuery.isError ? <p className="state-text">워크스페이스를 불러오지 못했습니다.</p> : null}
        {!workspacesQuery.isLoading && !workspacesQuery.isError && workspaces.length === 0 ? (
          <p className="state-text">워크스페이스가 없습니다.</p>
        ) : null}
        {workspaces.length > 0 ? (
          <table>
            <thead>
              <tr>
                <th>이름</th>
                <th>소유 유저</th>
                <th>상태</th>
                <th>삭제</th>
                <th>작업</th>
              </tr>
            </thead>
            <tbody>
              {workspaces.map((workspace) => {
                const owner = usersById.get(workspace.ownerUserId);
                return (
                  <tr key={workspace.id}>
                    <td>{workspace.name}</td>
                    <td>{owner ? `${owner.displayName} · ${owner.email}` : workspace.ownerUserId}</td>
                    <td>
                      <span className="status-badge">{workspace.deletedAt ? 'DELETED' : 'ACTIVE'}</span>
                    </td>
                    <td>{workspace.deletedAt ? formatDate(workspace.deletedAt) : '-'}</td>
                    <td>
                      <div className="row-actions">
                        {!workspace.deletedAt ? (
                          <>
                            <button
                              type="button"
                              className="icon-button"
                              aria-label={`${workspace.name} 수정`}
                              title="수정"
                              onClick={() => openEditForm(workspace)}
                            >
                              <Pencil size={16} aria-hidden="true" />
                            </button>
                            <button
                              type="button"
                              className="icon-button"
                              aria-label={`${workspace.name} 삭제`}
                              title="삭제"
                              disabled={deleteMutation.isPending}
                              onClick={() => handleDelete(workspace)}
                            >
                              <Trash2 size={16} aria-hidden="true" />
                            </button>
                          </>
                        ) : (
                          <button
                            type="button"
                            className="icon-button"
                            aria-label={`${workspace.name} 복구`}
                            title="복구"
                            disabled={restoreMutation.isPending}
                            onClick={() => handleRestore(workspace)}
                          >
                            <RotateCcw size={16} aria-hidden="true" />
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        ) : null}
      </div>

      {isFormOpen ? (
        <WorkspaceFormDialog
          isSubmitting={saveWorkspaceMutation.isPending}
          users={users}
          workspace={editingWorkspace}
          onClose={closeForm}
          onSubmit={(values) => saveWorkspaceMutation.mutate(values)}
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

export default WorkspacesPage;
