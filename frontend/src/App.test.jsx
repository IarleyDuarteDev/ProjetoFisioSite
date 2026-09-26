import React from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, configure, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import App from './App';
import { api, setAccessToken } from './api';
import { addDays, clinicToday } from './format';

vi.mock('./api', () => ({ api: vi.fn(), setAccessToken: vi.fn() }));
configure({ asyncUtilTimeout: 5000 });
const profissional = { id: 'p1', nome: 'Marina Costa', especialidade: 'Ortopedia' };
const slot = { id: 'h1', data: addDays(clinicToday(), 1), horaInicio: '09:00:00', horaFim: '09:50:00', tipoSessao: 'FISIOTERAPIA', profissional };
const second = { ...slot, id: 'h2', horaInicio: '10:00:00', horaFim: '10:50:00' };
let appointments;
beforeEach(() => {
  appointments = [];
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', ''); };
  api.mockReset(); setAccessToken.mockReset();
  api.mockImplementation(async (path, options = {}) => {
    if (path === '/profissionais') return [profissional];
    if (path.startsWith('/horarios/disponiveis')) return [slot, second];
    if (path === '/auth/login') return { accessToken: 'jwt-teste', usuario: { id: 'u1', nome: 'Ana Silva' } };
    if (path.startsWith('/agendamentos/meus')) return { itens: appointments, totalPaginas: 1 };
    if (path === '/agendamentos' && options.method === 'POST') {
      appointments = [{ id: 'a1', status: 'CONFIRMADO', horario: slot, observacoes: options.body.observacoes }];
      return appointments[0];
    }
    if (path === '/agendamentos/a1') {
      appointments = [{ ...appointments[0], ...(options.body.acao === 'CANCELAR' ? { status: 'CANCELADO' } : { horario: second }) }];
      return appointments[0];
    }
    throw new Error(`Rota inesperada: ${path}`);
  });
});
afterEach(cleanup);

it('mantém a seleção no login e permite reservar, remarcar e cancelar pela interface', async () => {
  render(<App />);
  fireEvent.click(await screen.findByRole('button', { name: /09:00/ }));
  fireEvent.click(screen.getByRole('button', { name: 'Entrar para confirmar' }));
  const dialog = screen.getByRole('dialog');
  fireEvent.change(within(dialog).getByLabelText('E-mail'), { target: { value: 'ana@example.com' } });
  fireEvent.change(within(dialog).getByLabelText('Senha'), { target: { value: 'senha-teste-123' } });
  fireEvent.click(within(dialog).getByRole('button', { name: 'Entrar na minha conta' }));
  const confirm = await screen.findByRole('button', { name: 'Confirmar agendamento' });
  expect(setAccessToken).toHaveBeenCalledWith('jwt-teste');
  fireEvent.click(confirm);
  fireEvent.click(await screen.findByRole('button', { name: 'Remarcar' }));
  fireEvent.click(await screen.findByRole('button', { name: /10:00/ }));
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar remarcação' }));
  expect(await screen.findByText(/Sessão remarcada!/)).toBeTruthy();
  expect(api).toHaveBeenCalledWith('/agendamentos/a1', { method: 'PUT', body: { acao: 'REMARCAR', novoHorarioId: 'h2' } });
  fireEvent.click(await screen.findByRole('button', { name: 'Cancelar', exact: true }));
  fireEvent.click(screen.getByRole('button', { name: 'Sim, cancelar' }));
  expect(await screen.findByText('Cancelado', { exact: true })).toBeTruthy();
  expect(api).toHaveBeenCalledWith('/agendamentos/a1', { method: 'PUT', body: { acao: 'CANCELAR' } });
});

it('mostra falha de conexão e recupera a consulta com tentar novamente', async () => {
  const working = api.getMockImplementation();
  api.mockImplementation(async (...args) => {
    if (args[0].startsWith('/horarios/disponiveis')) throw new Error('Conexão indisponível');
    return working(...args);
  });
  render(<App />);
  expect(await screen.findByRole('alert')).toHaveProperty('textContent', expect.stringContaining('Conexão indisponível'));
  api.mockImplementation(working);
  fireEvent.click(screen.getByRole('button', { name: 'Tentar novamente' }));
  await waitFor(() => expect(screen.queryByRole('alert')).toBeNull());
  expect(await screen.findByRole('button', { name: /09:00/ })).toBeTruthy();
});
