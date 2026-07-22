import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { fetchAdminIngestionJobItems, fetchAdminIngestionJobs } from '../api/adminGraphql';
import { useFragment } from '../generated/fragment-masking';
import {
  IngestionJobFieldsFragmentDoc,
  IngestionJobItemFieldsFragmentDoc
} from '../generated/graphql';

type IngestionJobHistoryPanelProps = {
  dataSourceId: string;
  dataSourceName: string;
};

function IngestionJobHistoryPanel({ dataSourceId, dataSourceName }: IngestionJobHistoryPanelProps) {
  const [selectedJobId, setSelectedJobId] = useState<string | null>(null);
  const jobsQuery = useQuery({
    queryKey: ['admin-ingestion-jobs', dataSourceId],
    queryFn: () => fetchAdminIngestionJobs(dataSourceId)
  });
  const jobs = useFragment(IngestionJobFieldsFragmentDoc, jobsQuery.data?.ingestionJobs ?? []);
  const selectedJob = jobs.find(
    (job) => job.id === selectedJobId && job.failedItemCount > 0
  ) ?? null;
  const itemsQuery = useInfiniteQuery({
    enabled: Boolean(selectedJob && selectedJob.failedItemCount > 0),
    queryKey: ['admin-ingestion-job-items', selectedJob?.id, 'FAILED'],
    initialPageParam: null as string | null,
    queryFn: ({ pageParam }) => fetchAdminIngestionJobItems(
      selectedJob?.id as string,
      'FAILED',
      50,
      pageParam
    ),
    getNextPageParam: (page) => page.ingestionJobItems.hasNextPage
      ? page.ingestionJobItems.endCursor ?? undefined
      : undefined
  });
  const itemReferences = itemsQuery.data?.pages.flatMap(
    (page) => page.ingestionJobItems.items
  ) ?? [];
  const items = useFragment(IngestionJobItemFieldsFragmentDoc, itemReferences);

  return (
    <section className="table-shell ingestion-history" aria-label={`${dataSourceName} 수집 기록 목록`}>
      <header className="subsection-heading">
        <div>
          <h3>수집 기록</h3>
          <p>{dataSourceName}</p>
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
              <th>성공</th>
              <th>건너뜀</th>
              <th>실패</th>
              <th>트리거</th>
              <th>생성</th>
              <th>시작</th>
              <th>종료</th>
              <th>상세</th>
            </tr>
          </thead>
          <tbody>
            {jobs.map((job) => (
              <tr key={job.id} className={selectedJob?.id === job.id ? 'selected-row' : undefined}>
                <td>
                  <span className={job.status === 'PARTIAL_FAILED'
                    ? 'status-badge status-badge--warning'
                    : 'status-badge'}>
                    {job.status}
                  </span>
                </td>
                <td>{job.succeededItemCount}</td>
                <td>{job.skippedItemCount}</td>
                <td>{job.failedItemCount}</td>
                <td>{job.triggerType}</td>
                <td>{formatDate(job.createdAt)}</td>
                <td>{job.startedAt ? formatDate(job.startedAt) : '-'}</td>
                <td>{job.finishedAt ? formatDate(job.finishedAt) : '-'}</td>
                <td>
                  {job.failedItemCount > 0 ? (
                    <button
                      type="button"
                      className="text-button"
                      aria-label={`${job.id} 실패 상세 보기`}
                      aria-pressed={selectedJob?.id === job.id}
                      onClick={() => setSelectedJobId(job.id)}
                    >
                      실패 상세
                    </button>
                  ) : '-'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}

      {selectedJob ? (
        <section className="ingestion-failure-detail" aria-label={`${selectedJob.id} 실패 상세`}>
          <header className="subsection-heading">
            <div>
              <h3>실패 상세</h3>
              <p>{selectedJob.errorMessage ?? '실패 요약이 없습니다.'}</p>
            </div>
          </header>
          {itemsQuery.isPending ? <p className="state-text">실패 상세를 불러오는 중입니다.</p> : null}
          {itemsQuery.isLoadingError ? (
            <p className="state-text">실패 상세를 불러오지 못했습니다.</p>
          ) : null}
          {itemsQuery.isSuccess && items.length === 0 ? (
            <p className="state-text">실패 항목이 없습니다.</p>
          ) : null}
          {items.length > 0 ? (
            <table className="failure-items-table">
              <thead>
                <tr>
                  <th>외부 ID</th>
                  <th>실패 사유</th>
                  <th>처리 시각</th>
                </tr>
              </thead>
              <tbody>
                {items.map((item, index) => (
                  <tr key={`${item.externalId ?? 'unknown'}-${item.processedAt}-${index}`}>
                    <td>{item.externalId ?? '-'}</td>
                    <td className="failure-reason">{item.reason ?? '-'}</td>
                    <td>{formatDate(item.processedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : null}
          {itemsQuery.isFetchNextPageError ? (
            <p className="state-text">다음 실패 항목을 불러오지 못했습니다.</p>
          ) : null}
          {itemsQuery.hasNextPage ? (
            <div className="ingestion-failure-actions">
              <button
                type="button"
                className="secondary-button"
                disabled={itemsQuery.isFetchingNextPage}
                onClick={() => itemsQuery.fetchNextPage()}
              >
                {itemsQuery.isFetchingNextPage ? '불러오는 중' : '실패 항목 더 보기'}
              </button>
            </div>
          ) : null}
        </section>
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

export default IngestionJobHistoryPanel;
