export type CsrfToken = {
  headerName: string;
  parameterName: string;
  token: string;
};

let cachedToken: CsrfToken | null = null;

export async function fetchCsrfToken(): Promise<CsrfToken> {
  if (cachedToken) {
    return cachedToken;
  }

  const response = await fetch('/admin/auth/csrf', {
    credentials: 'include'
  });
  if (!response.ok) {
    throw new Error('CSRF 토큰을 가져오지 못했습니다');
  }

  cachedToken = await response.json() as CsrfToken;
  return cachedToken;
}

export function clearCsrfToken() {
  cachedToken = null;
}
