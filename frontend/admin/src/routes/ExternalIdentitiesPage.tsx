import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Pencil, Plus, Trash2 } from 'lucide-react';
import { useState } from 'react';
import {
  createAdminExternalIdentity,
  deleteAdminExternalIdentity,
  fetchAdminExternalIdentityManagement,
  updateAdminExternalIdentity
} from '../api/adminGraphql';
import { useFragment } from '../generated/fragment-masking';
import {
  ExternalIdentityFieldsFragmentDoc,
  type ExternalIdentityFieldsFragment,
  UserFieldsFragmentDoc,
  WorkspaceFieldsFragmentDoc
} from '../generated/graphql';
import ExternalIdentityFormDialog, { type ExternalIdentityFormValues } from './ExternalIdentityFormDialog';

const EXTERNAL_IDENTITIES_QUERY_KEY = ['admin-external-identities'];

function ExternalIdentitiesPage() {
  const queryClient = useQueryClient();
  const [editingIdentity, setEditingIdentity] = useState<ExternalIdentityFieldsFragment | null>(null);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const managementQuery = useQuery({
    queryKey: EXTERNAL_IDENTITIES_QUERY_KEY,
    queryFn: fetchAdminExternalIdentityManagement
  });
  const externalIdentities = useFragment(
    ExternalIdentityFieldsFragmentDoc,
    managementQuery.data?.externalIdentities.items ?? []
  );
  const users = useFragment(UserFieldsFragmentDoc, managementQuery.data?.users.items ?? []);
  const workspaces = useFragment(WorkspaceFieldsFragmentDoc, managementQuery.data?.workspaces.items ?? []);
  const usersById = new Map(users.map((user) => [user.id, user]));
  const workspacesById = new Map(workspaces.map((workspace) => [workspace.id, workspace]));

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: EXTERNAL_IDENTITIES_QUERY_KEY });
  };
  const saveIdentityMutation = useMutation({
    mutationFn: async (values: ExternalIdentityFormValues): Promise<unknown> => {
      const input = {
        displayName: values.displayName || undefined,
        email: values.email || undefined,
        externalUserId: values.externalUserId,
        externalWorkspaceId: values.externalWorkspaceId,
        userId: values.userId,
        workspaceId: values.workspaceId
      };
      if (editingIdentity) {
        return await updateAdminExternalIdentity(editingIdentity.id, input);
      }

      return await createAdminExternalIdentity({
        ...input,
        provider: 'SLACK'
      });
    },
    onSuccess: () => {
      refresh();
      closeForm();
    }
  });
  const deleteMutation = useMutation({
    mutationFn: deleteAdminExternalIdentity,
    onSuccess: refresh
  });

  function openCreateForm() {
    setEditingIdentity(null);
    setIsFormOpen(true);
  }

  function openEditForm(identity: ExternalIdentityFieldsFragment) {
    setEditingIdentity(identity);
    setIsFormOpen(true);
  }

  function closeForm() {
    setEditingIdentity(null);
    setIsFormOpen(false);
  }

  function handleDelete(identity: ExternalIdentityFieldsFragment) {
    if (window.confirm('이 외부 계정 매핑을 삭제할까요?')) {
      deleteMutation.mutate(identity.id);
    }
  }

  return (
    <section className="management-page" aria-label="외부 계정 관리">
      <header className="section-heading">
        <div>
          <h2>외부 계정</h2>
          <p>{managementQuery.data?.externalIdentities.totalCount ?? 0}개</p>
        </div>
        <button type="button" className="primary-button" onClick={openCreateForm}>
          <Plus size={16} aria-hidden="true" />
          Slack 매핑 추가
        </button>
      </header>

      <div className="table-shell">
        {managementQuery.isLoading ? <p className="state-text">외부 계정 매핑을 불러오는 중입니다.</p> : null}
        {managementQuery.isError ? <p className="state-text">외부 계정 매핑을 불러오지 못했습니다.</p> : null}
        {!managementQuery.isLoading && !managementQuery.isError && externalIdentities.length === 0 ? (
          <p className="state-text">외부 계정 매핑이 없습니다.</p>
        ) : null}
        {externalIdentities.length > 0 ? (
          <table>
            <thead>
              <tr>
                <th>제공자</th>
                <th>Slack 팀</th>
                <th>Slack 유저</th>
                <th>워크스페이스</th>
                <th>내부 유저</th>
                <th>표시 정보</th>
                <th>Principal</th>
                <th>작업</th>
              </tr>
            </thead>
            <tbody>
              {externalIdentities.map((identity) => {
                const user = usersById.get(identity.userId);
                const workspace = workspacesById.get(identity.workspaceId);
                return (
                  <tr key={identity.id}>
                    <td>
                      <span className="status-badge">{identity.provider}</span>
                    </td>
                    <td>{identity.externalWorkspaceId}</td>
                    <td>{identity.externalUserId}</td>
                    <td>{workspace?.name ?? identity.workspaceId}</td>
                    <td>{user ? `${user.displayName} · ${user.email}` : identity.userId}</td>
                    <td>{identity.displayName || identity.email || '-'}</td>
                    <td>{identity.principalKey}</td>
                    <td>
                      <div className="row-actions">
                        <button
                          type="button"
                          className="icon-button"
                          aria-label={`${identityLabel(identity)} 수정`}
                          title="수정"
                          onClick={() => openEditForm(identity)}
                        >
                          <Pencil size={16} aria-hidden="true" />
                        </button>
                        <button
                          type="button"
                          className="icon-button"
                          aria-label={`${identityLabel(identity)} 삭제`}
                          title="삭제"
                          disabled={deleteMutation.isPending}
                          onClick={() => handleDelete(identity)}
                        >
                          <Trash2 size={16} aria-hidden="true" />
                        </button>
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
        <ExternalIdentityFormDialog
          identity={editingIdentity}
          isSubmitting={saveIdentityMutation.isPending}
          users={users}
          workspaces={workspaces}
          onClose={closeForm}
          onSubmit={(values) => saveIdentityMutation.mutate(values)}
        />
      ) : null}
    </section>
  );
}

function identityLabel(identity: ExternalIdentityFieldsFragment) {
  return identity.displayName || identity.email || identity.externalUserId;
}

export default ExternalIdentitiesPage;
