import type { FormEvent } from 'react';
import { useEffect, useState } from 'react';
import type { UserFieldsFragment, WorkspaceFieldsFragment } from '../generated/graphql';

export type WorkspaceFormValues = {
  name: string;
  ownerUserId: string;
};

type WorkspaceFormDialogProps = {
  isSubmitting: boolean;
  onClose: () => void;
  onSubmit: (values: WorkspaceFormValues) => void;
  users: UserFieldsFragment[];
  workspace: WorkspaceFieldsFragment | null;
};

function WorkspaceFormDialog({
  isSubmitting,
  onClose,
  onSubmit,
  users,
  workspace
}: WorkspaceFormDialogProps) {
  const [values, setValues] = useState<WorkspaceFormValues>(() => createInitialValues(workspace));

  useEffect(() => {
    setValues(createInitialValues(workspace));
  }, [workspace]);

  useEffect(() => {
    setValues((currentValues) => ({
      ...currentValues,
      ownerUserId: currentValues.ownerUserId || users[0]?.id || ''
    }));
  }, [users]);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onSubmit(values);
  }

  const title = workspace ? '워크스페이스 수정' : '워크스페이스 추가';

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

function createInitialValues(workspace: WorkspaceFieldsFragment | null): WorkspaceFormValues {
  return {
    name: workspace?.name ?? '',
    ownerUserId: workspace?.ownerUserId ?? ''
  };
}

export default WorkspaceFormDialog;
