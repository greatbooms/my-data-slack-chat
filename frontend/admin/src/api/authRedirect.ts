const ADMIN_LOGIN_PATH = '/admin-ui/login';

export function isAuthenticationFailureStatus(status: number | undefined): boolean {
  return status === 401 || status === 403;
}

export function redirectToAdminLogin() {
  if (typeof window === 'undefined') {
    return;
  }
  if (window.location.pathname === ADMIN_LOGIN_PATH) {
    return;
  }

  window.location.assign(ADMIN_LOGIN_PATH);
}
