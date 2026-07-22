import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { History, Pencil, Play, Plus, Trash2 } from 'lucide-react';
import { useState } from 'react';
import {
  createAdminDataSource,
  fetchAdminDataSourceFormOptions,
  fetchAdminDataSources,
  requestAdminDataSourceSync,
  softDeleteAdminDataSource,
  updateAdminDataSource
} from '../api/adminGraphql';
import { useFragment } from '../generated/fragment-masking';
import {
  DataSourceFieldsFragmentDoc,
  UserFieldsFragmentDoc,
  WorkspaceFieldsFragmentDoc,
  type DataSourceFieldsFragment
} from '../generated/graphql';
import DataSourceFormDialog, { type DataSourceFormValues } from './DataSourceFormDialog';
import IngestionJobHistoryPanel from './IngestionJobHistoryPanel';

const DATA_SOURCES_QUERY_KEY = ['admin-data-sources'];
const DASHBOARD_QUERY_KEY = ['viewer-and-dashboard'];

function DataSourcesPage() {
  const queryClient = useQueryClient();
  const [editingDataSource, setEditingDataSource] = useState<DataSourceFieldsFragment | null>(null);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [selectedJobSourceId, setSelectedJobSourceId] = useState<string | null>(null);
  const dataSourcesQuery = useQuery({
    queryKey: DATA_SOURCES_QUERY_KEY,
    queryFn: fetchAdminDataSources
  });
  const dataSources = useFragment(DataSourceFieldsFragmentDoc, dataSourcesQuery.data?.dataSources.items ?? []);
  const selectedDataSource = dataSources.find((dataSource) => dataSource.id === selectedJobSourceId) ?? null;
  const formOptionsQuery = useQuery({
    enabled: isFormOpen,
    queryKey: ['admin-data-source-form-options'],
    queryFn: fetchAdminDataSourceFormOptions
  });
  const users = useFragment(UserFieldsFragmentDoc, formOptionsQuery.data?.users.items ?? []);
  const workspaces = useFragment(WorkspaceFieldsFragmentDoc, formOptionsQuery.data?.workspaces.items ?? []);
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: DATA_SOURCES_QUERY_KEY });
    queryClient.invalidateQueries({ queryKey: DASHBOARD_QUERY_KEY });
    if (selectedJobSourceId) {
      queryClient.invalidateQueries({ queryKey: ['admin-ingestion-jobs', selectedJobSourceId] });
    }
  };
  const saveDataSourceMutation = useMutation({
    mutationFn: async (values: DataSourceFormValues): Promise<unknown> => {
      if (editingDataSource) {
        return await updateAdminDataSource(editingDataSource.id, {
          name: values.name,
          notionDatabaseId: editingDataSource.type === 'NOTION' && values.notionRootKind === 'DATABASE'
            ? values.notionDatabaseId
            : undefined,
          notionRootPageId: editingDataSource.type === 'NOTION' && values.notionRootKind === 'PAGE'
            ? values.notionRootPageId
            : undefined,
          ownerUserId: values.ownerUserId,
          slackChannelId: editingDataSource.type === 'SLACK' ? values.slackChannelId : undefined,
          slackWorkspaceUrl: editingDataSource.type === 'SLACK' ? values.slackWorkspaceUrl : undefined,
          status: values.status,
          syncMode: values.syncMode,
          syncCron: values.syncCron,
          visibility: values.visibility
        });
      }

      return await createAdminDataSource({
        name: values.name,
        notionDatabaseId: values.type === 'NOTION' && values.notionRootKind === 'DATABASE'
          ? values.notionDatabaseId
          : undefined,
        notionRootPageId: values.type === 'NOTION' && values.notionRootKind === 'PAGE'
          ? values.notionRootPageId
          : undefined,
        ownerUserId: values.ownerUserId,
        slackChannelId: values.type === 'SLACK' ? values.slackChannelId : undefined,
        slackWorkspaceUrl: values.type === 'SLACK' ? values.slackWorkspaceUrl : undefined,
        syncMode: values.syncMode,
        syncCron: values.syncCron,
        type: values.type,
        visibility: values.visibility,
        workspaceId: values.workspaceId
      });
    },
    onSuccess: () => {
      refresh();
      closeForm();
    }
  });
  const deleteMutation = useMutation({
    mutationFn: softDeleteAdminDataSource,
    onSuccess: refresh
  });
  const syncMutation = useMutation({
    mutationFn: requestAdminDataSourceSync,
    onSuccess: refresh
  });

  function openCreateForm() {
    saveDataSourceMutation.reset();
    setEditingDataSource(null);
    setIsFormOpen(true);
  }

  function openEditForm(dataSource: DataSourceFieldsFragment) {
    saveDataSourceMutation.reset();
    setEditingDataSource(dataSource);
    setIsFormOpen(true);
  }

  function closeForm() {
    saveDataSourceMutation.reset();
    setEditingDataSource(null);
    setIsFormOpen(false);
  }

  function handleDelete(dataSource: DataSourceFieldsFragment) {
    if (window.confirm('이 데이터소스를 삭제할까요?')) {
      deleteMutation.mutate(dataSource.id);
    }
  }

  return (
    <section className="management-page" aria-label="데이터소스 관리">
      <header className="section-heading">
        <div>
          <h2>데이터소스</h2>
          <p>{dataSourcesQuery.data?.dataSources.totalCount ?? 0}개</p>
        </div>
        <button type="button" className="primary-button" onClick={openCreateForm}>
          <Plus size={16} aria-hidden="true" />
          데이터소스 추가
        </button>
      </header>

      <div className="table-shell">
        {dataSourcesQuery.isLoading ? <p className="state-text">데이터소스를 불러오는 중입니다.</p> : null}
        {dataSourcesQuery.isError ? <p className="state-text">데이터소스를 불러오지 못했습니다.</p> : null}
        {!dataSourcesQuery.isLoading && !dataSourcesQuery.isError && dataSources.length === 0 ? (
          <p className="state-text">데이터소스가 없습니다.</p>
        ) : null}
        {dataSources.length > 0 ? (
          <table>
            <thead>
              <tr>
                <th>이름</th>
                <th>종류</th>
                <th>상태</th>
                <th>가시성</th>
                <th>수집 방식</th>
                <th>마지막 수집</th>
                <th>작업</th>
              </tr>
            </thead>
            <tbody>
              {dataSources.map((dataSource) => (
                <tr key={dataSource.id}>
                  <td>{dataSource.name}</td>
                  <td>{dataSource.type}</td>
                  <td>
                    <span className="status-badge">{dataSource.status}</span>
                  </td>
                  <td>{dataSource.visibility}</td>
                  <td>{dataSource.syncMode}</td>
                  <td>{dataSource.lastSyncedAt ? formatDate(dataSource.lastSyncedAt) : '-'}</td>
                  <td>
                    <div className="row-actions">
                      <button
                        type="button"
                        className="icon-button"
                        aria-label={`${dataSource.name} 수정`}
                        title="수정"
                        onClick={() => openEditForm(dataSource)}
                      >
                        <Pencil size={16} aria-hidden="true" />
                      </button>
                      {!dataSource.deletedAt ? (
                        <button
                          type="button"
                          className="icon-button"
                          aria-label={`${dataSource.name} 수동 수집`}
                          title="수동 수집"
                          disabled={syncMutation.isPending}
                          onClick={() => syncMutation.mutate(dataSource.id)}
                        >
                          <Play size={16} aria-hidden="true" />
                        </button>
                      ) : null}
                      <button
                        type="button"
                        className="icon-button"
                        aria-label={`${dataSource.name} 수집 기록`}
                        title="수집 기록"
                        onClick={() => setSelectedJobSourceId(dataSource.id)}
                      >
                        <History size={16} aria-hidden="true" />
                      </button>
                      {!dataSource.deletedAt ? (
                        <button
                          type="button"
                          className="icon-button"
                          aria-label={`${dataSource.name} 삭제`}
                          title="삭제"
                          disabled={deleteMutation.isPending}
                          onClick={() => handleDelete(dataSource)}
                        >
                          <Trash2 size={16} aria-hidden="true" />
                        </button>
                      ) : null}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : null}
      </div>

      {selectedDataSource ? (
        <IngestionJobHistoryPanel
          key={selectedDataSource.id}
          dataSourceId={selectedDataSource.id}
          dataSourceName={selectedDataSource.name}
        />
      ) : null}

      {isFormOpen ? (
        <DataSourceFormDialog
          dataSource={editingDataSource}
          errorMessage={dataSourceMutationErrorMessage(saveDataSourceMutation.error)}
          isSubmitting={saveDataSourceMutation.isPending}
          users={users}
          workspaces={workspaces}
          onClose={closeForm}
          onSubmit={(values) => saveDataSourceMutation.mutate(values)}
        />
      ) : null}
    </section>
  );
}

function dataSourceMutationErrorMessage(error: unknown): string | null {
  if (error === null || error === undefined) {
    return null;
  }
  if (error instanceof Error
    && error.message.includes('notionRootPageId 형식이 올바르지 않습니다')) {
    return 'notionRootPageId 형식이 올바르지 않습니다';
  }
  return '데이터소스를 저장하지 못했습니다.';
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'short',
    timeStyle: 'short'
  }).format(new Date(value));
}

export default DataSourcesPage;
