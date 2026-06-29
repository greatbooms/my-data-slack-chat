import type { FormEvent } from 'react';
import { useEffect, useState } from 'react';
import type {
  DataSourceFieldsFragment,
  DataSourceStatus,
  DataSourceType,
  DataSourceVisibility,
  SyncMode,
  UserFieldsFragment,
  WorkspaceFieldsFragment
} from '../generated/graphql';

export type DataSourceFormValues = {
  name: string;
  notionRootPageId: string;
  ownerUserId: string;
  status: DataSourceStatus;
  syncMode: SyncMode;
  type: DataSourceType;
  visibility: DataSourceVisibility;
  workspaceId: string;
};

type DataSourceFormDialogProps = {
  dataSource: DataSourceFieldsFragment | null;
  isSubmitting: boolean;
  users: UserFieldsFragment[];
  workspaces: WorkspaceFieldsFragment[];
  onClose: () => void;
  onSubmit: (values: DataSourceFormValues) => void;
};

function DataSourceFormDialog({
  dataSource,
  isSubmitting,
  users,
  workspaces,
  onClose,
  onSubmit
}: DataSourceFormDialogProps) {
  const [values, setValues] = useState<DataSourceFormValues>(() => createInitialValues(dataSource));

  useEffect(() => {
    setValues(createInitialValues(dataSource));
  }, [dataSource]);

  useEffect(() => {
    setValues((currentValues) => ({
      ...currentValues,
      ownerUserId: currentValues.ownerUserId || users[0]?.id || '',
      workspaceId: currentValues.workspaceId || workspaces[0]?.id || ''
    }));
  }, [users, workspaces]);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onSubmit(values);
  }

  const title = dataSource ? '데이터소스 수정' : '데이터소스 추가';

  return (
    <div className="dialog-backdrop" role="presentation">
      <section className="form-dialog" role="dialog" aria-modal="true" aria-label={title}>
        <form onSubmit={handleSubmit}>
          <header>
            <h2>{title}</h2>
            <button type="button" className="text-button" onClick={onClose}>
              닫기
            </button>
          </header>

          <label>
            이름
            <input
              value={values.name}
              required
              onChange={(event) => setValues({ ...values, name: event.target.value })}
            />
          </label>

          <label>
            워크스페이스
            <select
              value={values.workspaceId}
              disabled={Boolean(dataSource)}
              required
              onChange={(event) => setValues({ ...values, workspaceId: event.target.value })}
            >
              <option value="" disabled>
                워크스페이스 선택
              </option>
              {workspaces.map((workspace) => (
                <option key={workspace.id} value={workspace.id}>
                  {workspace.name}
                </option>
              ))}
            </select>
          </label>

          <label>
            소유 유저
            <select
              value={values.ownerUserId}
              required
              onChange={(event) => setValues({ ...values, ownerUserId: event.target.value })}
            >
              <option value="" disabled>
                유저 선택
              </option>
              {users.map((user) => (
                <option key={user.id} value={user.id}>
                  {user.displayName} · {user.email}
                </option>
              ))}
            </select>
          </label>

          <label>
            종류
            <select
              value={values.type}
              disabled={Boolean(dataSource)}
              onChange={(event) => setValues({ ...values, type: event.target.value as DataSourceType })}
            >
              <option value="LOCAL_TEXT">LOCAL_TEXT</option>
              <option value="NOTION">NOTION</option>
            </select>
          </label>

          {values.type === 'NOTION' ? (
            <label>
              Notion 루트 페이지 ID
              <input
                value={values.notionRootPageId}
                disabled={Boolean(dataSource && dataSource.type !== 'NOTION')}
                required
                onChange={(event) => setValues({ ...values, notionRootPageId: event.target.value })}
              />
            </label>
          ) : null}

          {dataSource ? (
            <label>
              상태
              <select
                value={values.status}
                onChange={(event) => setValues({ ...values, status: event.target.value as DataSourceStatus })}
              >
                <option value="ACTIVE">ACTIVE</option>
                <option value="PAUSED">PAUSED</option>
                <option value="ERROR">ERROR</option>
              </select>
            </label>
          ) : null}

          <label>
            가시성
            <select
              value={values.visibility}
              onChange={(event) => setValues({ ...values, visibility: event.target.value as DataSourceVisibility })}
            >
              <option value="PRIVATE">PRIVATE</option>
              <option value="WORKSPACE">WORKSPACE</option>
            </select>
          </label>

          <label>
            수집 방식
            <select
              value={values.syncMode}
              onChange={(event) => setValues({ ...values, syncMode: event.target.value as SyncMode })}
            >
              <option value="MANUAL">MANUAL</option>
              <option value="SCHEDULED">SCHEDULED</option>
              <option value="MANUAL_AND_SCHEDULED">MANUAL_AND_SCHEDULED</option>
            </select>
          </label>

          <footer>
            <button type="button" className="secondary-button" onClick={onClose}>
              취소
            </button>
            <button type="submit" disabled={isSubmitting}>
              저장
            </button>
          </footer>
        </form>
      </section>
    </div>
  );
}

function createInitialValues(dataSource: DataSourceFieldsFragment | null): DataSourceFormValues {
  return {
    name: dataSource?.name ?? '',
    notionRootPageId: dataSource?.notionRootPageId ?? '',
    ownerUserId: dataSource?.ownerUserId ?? '',
    status: dataSource?.status ?? 'ACTIVE',
    syncMode: dataSource?.syncMode ?? 'MANUAL',
    type: dataSource?.type ?? 'LOCAL_TEXT',
    visibility: dataSource?.visibility ?? 'PRIVATE',
    workspaceId: dataSource?.workspaceId ?? ''
  };
}

export default DataSourceFormDialog;
