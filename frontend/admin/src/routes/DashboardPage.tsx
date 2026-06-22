import { useQuery } from '@tanstack/react-query';
import { fetchViewerAndDashboard } from '../api/adminGraphql';

function DashboardPage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['viewer-and-dashboard'],
    queryFn: fetchViewerAndDashboard
  });
  const metrics = [
    {
      label: '연결된 데이터소스',
      value: data?.dashboardSummary.dataSourceCount ?? 0,
      hint: '수집 가능한 데이터소스'
    },
    {
      label: '진행 중 수집',
      value: data?.dashboardSummary.runningJobCount ?? 0,
      hint: '현재 실행 중인 작업'
    },
    {
      label: '관리 대상 유저',
      value: data?.dashboardSummary.userCount ?? 0,
      hint: '삭제되지 않은 계정'
    }
  ];

  return (
    <section className="dashboard" aria-label="관리자 대시보드">
      <div className="metric-grid">
        {metrics.map((metric) => (
          <article className="metric" key={metric.label} aria-label={metric.label}>
            <span>{metric.label}</span>
            <strong>{metric.value}</strong>
            <p>{metric.hint}</p>
          </article>
        ))}
      </div>

      <section className="work-panel">
        <div>
          <h2>다음 작업</h2>
          {isLoading ? <p>대시보드 데이터를 불러오는 중입니다.</p> : null}
          {isError ? <p>대시보드 데이터를 불러오지 못했습니다.</p> : null}
          {data ? (
            <p>
              {data.viewer.displayName} · {data.viewer.email}
            </p>
          ) : null}
        </div>
        <span className="status-badge">{data?.viewer.role ?? 'ADMIN'}</span>
      </section>
    </section>
  );
}

export default DashboardPage;
