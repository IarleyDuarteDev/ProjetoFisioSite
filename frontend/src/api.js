const base = import.meta.env.VITE_API_URL || '/api';
let accessToken = null;
export function setAccessToken(token) { accessToken = token; }

export async function api(path, { body, method = 'GET', signal } = {}) {
  let response;
  const sentToken = accessToken;
  try {
    response = await fetch(`${base}${path}`, {
      method, signal,
      headers: {
        ...(body ? { 'Content-Type': 'application/json' } : {}),
        ...(sentToken ? { Authorization: `Bearer ${sentToken}` } : {}),
      },
      ...(body ? { body: JSON.stringify(body) } : {}),
    });
  } catch (error) {
    if (error.name === 'AbortError') throw error;
    throw new Error('Não foi possível conectar à clínica. Verifique sua conexão e tente novamente.');
  }
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    if (response.status === 401 && sentToken && sentToken === accessToken) {
      accessToken = null;
      window.dispatchEvent(new Event('session-expired'));
    }
    const fields = payload.campos ? ` ${Object.values(payload.campos).join(' · ')}` : '';
    const error = new Error((payload.detail || 'Não foi possível concluir a operação.') + fields);
    error.status = response.status;
    throw error;
  }
  return payload;
}
