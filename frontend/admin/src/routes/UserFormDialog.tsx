import type { FormEvent } from 'react';
import { useEffect, useState } from 'react';
import type { UserFieldsFragment, UserRole, UserStatus } from '../generated/graphql';

export type UserFormValues = {
  displayName: string;
  email: string;
  password: string;
  role: UserRole;
  status: UserStatus;
};

type UserFormDialogProps = {
  isSubmitting: boolean;
  onClose: () => void;
  onSubmit: (values: UserFormValues) => void;
  user: UserFieldsFragment | null;
};

function UserFormDialog({ isSubmitting, onClose, onSubmit, user }: UserFormDialogProps) {
  const [values, setValues] = useState<UserFormValues>(() => createInitialValues(user));

  useEffect(() => {
    setValues(createInitialValues(user));
  }, [user]);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onSubmit(values);
  }

  const title = user ? '유저 수정' : '유저 추가';

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
            이메일
            <input
              type="email"
              value={values.email}
              disabled={Boolean(user)}
              required
              onChange={(event) => setValues({ ...values, email: event.target.value })}
            />
          </label>

          <label>
            이름
            <input
              value={values.displayName}
              required
              onChange={(event) => setValues({ ...values, displayName: event.target.value })}
            />
          </label>

          <label>
            역할
            <select
              value={values.role}
              onChange={(event) => setValues({ ...values, role: event.target.value as UserRole })}
            >
              <option value="USER">USER</option>
              <option value="ADMIN">ADMIN</option>
            </select>
          </label>

          {user ? (
            <label>
              상태
              <select
                value={values.status}
                onChange={(event) => setValues({ ...values, status: event.target.value as UserStatus })}
              >
                <option value="ACTIVE">ACTIVE</option>
                <option value="DISABLED">DISABLED</option>
              </select>
            </label>
          ) : (
            <label>
              임시 비밀번호
              <input
                type="password"
                value={values.password}
                required
                minLength={8}
                onChange={(event) => setValues({ ...values, password: event.target.value })}
              />
            </label>
          )}

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

function createInitialValues(user: UserFieldsFragment | null): UserFormValues {
  return {
    displayName: user?.displayName ?? '',
    email: user?.email ?? '',
    password: '',
    role: user?.role ?? 'USER',
    status: user?.status ?? 'ACTIVE'
  };
}

export default UserFormDialog;
