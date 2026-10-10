// Keep localhost development compatibility; use same-origin routes on HTTPS.
export const API_ORIGIN =
  typeof window !== 'undefined' && ['localhost','127.0.0.1'].includes(window.location.hostname)
    ? 'http://localhost:8080'
    : '';
export const GITHUB_LOGIN_URL = `${API_ORIGIN}/oauth2/authorization/github`;
