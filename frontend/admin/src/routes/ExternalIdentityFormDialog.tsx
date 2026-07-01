import { type FormEvent, useEffect, useState } from 'react';
import { CircleHelp } from 'lucide-react';
import type {
  ExternalIdentityFieldsFragment,
  UserFieldsFragment,
  WorkspaceFieldsFragment
} from '../generated/graphql';

export type ExternalIdentityFormValues = {
  workspaceId: string;
  userId: string;
  externalWorkspaceId: string;
  externalUserId: string;
  email: string;
  displayName: string;
};

type ExternalIdentityFormDialogProps = {
  identity?: ExternalIdentityFieldsFragment | null;
  isSubmitting: boolean;
  users: UserFieldsFragment[];
  workspaces: WorkspaceFieldsFragment[];
  onClose: () => void;
  onSubmit: (values: ExternalIdentityFormValues) => void;
};

function ExternalIdentityFormDialog({
  identity,
  isSubmitting,
  users,
  workspaces,
  onClose,
  onSubmit
}: ExternalIdentityFormDialogProps) {
  const [values, setValues] = useState(() => initialValues(identity));

  useEffect(() => {
    setValues(initialValues(identity));
  }, [identity]);

  useEffect(() => {
    setValues((current) => ({
      ...current,
      userId: current.userId || users[0]?.id || '',
      workspaceId: current.workspaceId || workspaces[0]?.id || ''
    }));
  }, [users, workspaces]);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onSubmit(values);
  }

  const title = identity ? 'Slack 매핑 수정' : 'Slack 매핑 추가';

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
            워크스페이스
            <select
              value={values.workspaceId}
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
            내부 유저
            <select
              value={values.userId}
              required
              onChange={(event) => setValues({ ...values, userId: event.target.value })}
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

          <div className="form-field">
            <span className="field-label-row">
              <label htmlFor="slack-team-id">Slack 팀 ID</label>
              <FieldHelp
                id="slack-team-id-help"
                label="Slack 팀 ID 도움말"
                text="Slack 웹에서 아무 채널에 들어간 뒤 주소창의 app.slack.com/client/T.../C... 중 /client/ 바로 뒤 T... 값을 입력합니다."
              />
            </span>
            <input
              aria-describedby="slack-team-id-help"
              id="slack-team-id"
              value={values.externalWorkspaceId}
              required
              onChange={(event) => setValues({ ...values, externalWorkspaceId: event.target.value })}
            />
          </div>

          <div className="form-field">
            <span className="field-label-row">
              <label htmlFor="slack-user-id">Slack 유저 ID</label>
              <FieldHelp
                id="slack-user-id-help"
                label="Slack 유저 ID 도움말"
                text="Slack 사용자 프로필의 멤버 ID 복사 메뉴에서 U... 값을 확인해 입력합니다. 메시지 수신 로그의 userId와도 같은 값입니다."
              />
            </span>
            <input
              aria-describedby="slack-user-id-help"
              id="slack-user-id"
              value={values.externalUserId}
              required
              onChange={(event) => setValues({ ...values, externalUserId: event.target.value })}
            />
          </div>

          <label>
            이메일
            <input
              type="email"
              value={values.email}
              onChange={(event) => setValues({ ...values, email: event.target.value })}
            />
          </label>

          <label>
            표시 이름
            <input
              value={values.displayName}
              onChange={(event) => setValues({ ...values, displayName: event.target.value })}
            />
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

function FieldHelp({ id, label, text }: { id: string; label: string; text: string }) {
  return (
    <span className="field-help">
      <button
        aria-describedby={id}
        aria-label={label}
        className="field-help-trigger"
        type="button"
      >
        <CircleHelp size={15} aria-hidden="true" />
      </button>
      <span className="field-tooltip" id={id} role="tooltip">
        {text}
      </span>
    </span>
  );
}

function initialValues(identity?: ExternalIdentityFieldsFragment | null): ExternalIdentityFormValues {
  return {
    workspaceId: identity?.workspaceId ?? '',
    userId: identity?.userId ?? '',
    externalWorkspaceId: identity?.externalWorkspaceId ?? '',
    externalUserId: identity?.externalUserId ?? '',
    email: identity?.email ?? '',
    displayName: identity?.displayName ?? ''
  };
}

export default ExternalIdentityFormDialog;
