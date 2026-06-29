import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { History, Pencil, Play, Plus, Trash2 } from 'lucide-react';
import { useState } from 'react';
import {
  createAdminDataSource,
  fetchAdminDataSourceFormOptions,
  fetchAdminDataSources,
  fetchAdminIngestionJobs,
  requestAdminDataSourceSync,
  softDeleteAdminDataSource,
  updateAdminDataSource
} from '../api/adminGraphql';
import { useFragment } from '../generated/fragment-masking';
import {
  DataSourceFieldsFragmentDoc,
  IngestionJobFieldsFragmentDoc,
  UserFieldsFragmentDoc,
  WorkspaceFieldsFragmentDoc,
  type DataSourceFieldsFragment
} from '../generated/graphql';
import DataSourceFormDialog, { type DataSourceFormValues } from './DataSourceFormDialog';

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
  const jobsQuery = useQuery({
    enabled: Boolean(selectedJobSourceId),
    queryKey: ['admin-ingestion-jobs', selectedJobSourceId],
    queryFn: () => fetchAdminIngestionJobs(selectedJobSourceId as string)
  });
  const jobs = useFragment(IngestionJobFieldsFragmentDoc, jobsQuery.data?.ingestionJobs ?? []);

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
          notionRootPageId: editingDataSource.type === 'NOTION' ? values.notionRootPageId : undefined,
          ownerUserId: values.ownerUserId,
          status: values.status,
          syncMode: values.syncMode,
          visibility: values.visibility
        });
      }

      return await createAdminDataSource({
        name: values.name,
        notionRootPageId: values.type === 'NOTION' ? values.notionRootPageId : undefined,
        ownerUserId: values.ownerUserId,
        syncMode: values.syncMode,
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
    setEditingDataSource(null);
    setIsFormOpen(true);
  }

  function openEditForm(dataSource: DataSourceFieldsFragment) {
    setEditingDataSource(dataSource);
    setIsFormOpen(true);
  }

  function closeForm() {
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
        <section className="table-shell" aria-label={`${selectedDataSource.name} 수집 기록 목록`}>
          <header className="subsection-heading">
            <div>
              <h3>수집 기록</h3>
              <p>{selectedDataSource.name}</p>
            </div>
          </header>
          {jobsQuery.isLoading ? <p className="state-text">수집 기록을 불러오는 중입니다.</p> : null}
          {jobsQuery.isError ? <p className="state-text">수집 기록을 불러오지 못했습니다.</p> : null}
          {!jobsQuery.isLoading && !jobsQuery.isError && jobs.length === 0 ? (
            <p className="state-text">수집 기록이 없습니다.</p>
          ) : null}
          {jobs.length > 0 ? (
            <table>
              <thead>
                <tr>
                  <th>상태</th>
                  <th>트리거</th>
                  <th>생성</th>
                  <th>시작</th>
                  <th>종료</th>
                </tr>
              </thead>
              <tbody>
                {jobs.map((job) => (
                  <tr key={job.id}>
                    <td>
                      <span className="status-badge">{job.status}</span>
                    </td>
                    <td>{job.triggerType}</td>
                    <td>{formatDate(job.createdAt)}</td>
                    <td>{job.startedAt ? formatDate(job.startedAt) : '-'}</td>
                    <td>{job.finishedAt ? formatDate(job.finishedAt) : '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : null}
        </section>
      ) : null}

      {isFormOpen ? (
        <DataSourceFormDialog
          dataSource={editingDataSource}
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

function formatDate(value: string) {
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'short',
    timeStyle: 'short'
  }).format(new Date(value));
}

export default DataSourcesPage;
