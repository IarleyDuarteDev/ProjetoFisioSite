import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, setAccessToken } from './api';

afterEach(() => { setAccessToken(null); vi.unstubAllGlobals(); });
describe('cliente da API', () => {
  it('envia o JWT no header e serializa o corpo', async () => {
    const fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ id: 'reserva' }) });
    vi.stubGlobal('fetch', fetch);
    setAccessToken('token-de-teste');
    expect(await api('/agendamentos', { method: 'POST', body: { horarioId: 'horario' } })).toEqual({ id: 'reserva' });
    expect(fetch).toHaveBeenCalledWith('/api/agendamentos', expect.objectContaining({ method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer token-de-teste' }, body: '{"horarioId":"horario"}' }));
  });
  it('expira a sessão quando a API rejeita o JWT', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 401, json: async () => ({ detail: 'Sessão expirada' }) }));
    setAccessToken('expirado');
    const listener = vi.fn(); window.addEventListener('session-expired', listener);
    await expect(api('/agendamentos/meus')).rejects.toThrow('Sessão expirada');
    expect(listener).toHaveBeenCalledOnce();
    window.removeEventListener('session-expired', listener);
  });
  it('preserva mensagens de conflito e não expira a sessão', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 409, json: async () => ({ detail: 'Horário ocupado' }) }));
    await expect(api('/agendamentos')).rejects.toMatchObject({ status: 409, message: 'Horário ocupado' });
  });
  it('diferencia cancelamento da requisição e erro de conexão', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new DOMException('Cancelado', 'AbortError')));
    await expect(api('/horarios/disponiveis')).rejects.toMatchObject({ name: 'AbortError' });
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    await expect(api('/horarios/disponiveis')).rejects.toThrow('Não foi possível conectar');
  });
});
