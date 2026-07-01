import { type FormEvent, useState } from 'react';
import { loginAdmin } from '../api/auth';

function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrorMessage(null);
    setIsSubmitting(true);
    try {
      await loginAdmin(email, password);
      window.location.assign('/admin-ui/');
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '로그인에 실패했습니다');
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <main className="login-page">
      <section className="login-panel" aria-label="관리자 로그인">
        <div className="login-brand">
          <strong>My Data</strong>
          <span>관리자</span>
        </div>
        <form
          action="/admin/auth/login"
          autoComplete="on"
          className="login-form"
          method="post"
          onSubmit={handleSubmit}
        >
          <h1>관리자 로그인</h1>
          <label htmlFor="admin-username">
            이메일
            <input
              autoCapitalize="none"
              autoComplete="username"
              id="admin-username"
              inputMode="email"
              name="username"
              required
              spellCheck={false}
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
          </label>
          <label htmlFor="admin-password">
            비밀번호
            <input
              autoComplete="current-password"
              id="admin-password"
              name="password"
              required
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </label>
          {errorMessage ? <p className="form-error" role="alert">{errorMessage}</p> : null}
          <button type="submit" disabled={isSubmitting}>
            {isSubmitting ? '로그인 중' : '로그인'}
          </button>
        </form>
      </section>
    </main>
  );
}

export default LoginPage;
