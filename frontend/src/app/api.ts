import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, throwError } from 'rxjs';

export interface Pro { id: string; nome: string; }
export interface Slot { id: string; data: string; horaInicio: string; horaFim: string; tipoSessao: string; profissional: Pro; }
export interface Appointment { id: string; status: 'CONFIRMADO' | 'CANCELADO'; observacoes?: string; horario: Slot; }
export interface Page { itens: Appointment[]; totalPaginas: number; }
export interface Usuario { id: string; nome: string; email: string; papel: 'CLIENTE' | 'ADMIN'; cpfPendente: boolean; emailVerificado: boolean; }
export interface AgendamentoAdmin { agendamento: Appointment; clienteNome: string; clienteEmail: string; clienteCpf: string | null; }
export interface PageAdmin { itens: AgendamentoAdmin[]; totalPaginas: number; }
export interface HorarioAdmin { horario: Slot; status: 'DISPONIVEL' | 'RESERVADO'; }
export interface Session { accessToken: string; usuario: Usuario; }

export class ApiError extends Error {
  constructor(message: string, public status = 0) { super(message); }
}

/** Fala com o back-end (/api no nginx e no proxy do ng serve). O token fica só na memória. */
@Injectable({ providedIn: 'root' })
export class Api {
  private http = inject(HttpClient);
  readonly session = signal<Session | null>(null);
  readonly expired = signal(0);

  request<T>(method: string, path: string, body?: unknown): Observable<T> {
    const token = this.session()?.accessToken;
    return this.http.request<T>(method, `/api${path}`, { body, headers: token ? { Authorization: `Bearer ${token}` } : {} }).pipe(
      catchError((e: HttpErrorResponse) => {
        if (e.status === 401 && token && token === this.session()?.accessToken) { this.session.set(null); this.expired.update(n => n + 1); }
        const payload = e.error && typeof e.error === 'object' ? e.error : {};
        const fields = payload.campos ? ' ' + Object.values(payload.campos).join(' · ') : '';
        const message = e.status === 0
          ? 'Não foi possível conectar à clínica. Verifique sua conexão e tente novamente.'
          : (payload.detail || 'Não foi possível concluir a operação.') + fields;
        return throwError(() => new ApiError(message, e.status));
      }),
    );
  }
}
