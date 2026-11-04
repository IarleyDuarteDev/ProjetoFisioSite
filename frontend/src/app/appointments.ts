import { Component, ElementRef, computed, effect, inject, input, output, signal, viewChild } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, firstValueFrom, map, of, startWith, switchMap } from 'rxjs';
import { Api, Appointment, Page } from './api';
import { clinicToday, dateLabel, hour, sessionBand, sessionNames } from './format';

const empty: Page = { itens: [], totalPaginas: 0 };

@Component({
  selector: 'app-appointments',
  template: `
    <section class="mine" aria-label="Meus agendamentos">
      <h1>Meus agendamentos</h1>
      @let st = state();
      @if (st.loading) {
        <div class="rows" role="status"><span class="sr-only">Carregando suas sessões…</span><div class="row sk"><i></i></div><div class="row sk"><i></i></div></div>
      } @else if (st.error) {
        <div class="alert" role="alert">{{ st.error }}<button class="link" (click)="retry.update(n => n + 1)">Tentar novamente</button></div>
      } @else if (!st.data.itens.length) {
        <div class="empty"><h3>Você não tem sessões agendadas.</h3><p>Escolha um dia e um horário para começar.</p><button class="btn" (click)="browse.emit()">Encontrar um horário</button></div>
      } @else {
        <ul class="rows">
          @for (a of st.data.itens; track a.id; let i = $index) {
            <li class="row" [style.--i]="i">
              <div class="when"><strong>{{ a.horario.data.slice(8) }}</strong><span>{{ month(a.horario.data) }} {{ a.horario.data.slice(0, 4) }}</span></div>
              <div class="what">
                <p class="what__t"><i class="band band--{{ bands[a.horario.tipoSessao] }}"></i>{{ names[a.horario.tipoSessao] }}
                  <span class="status" [class.is-off]="a.status === 'CANCELADO'">{{ a.status === 'CONFIRMADO' ? 'Confirmado' : 'Cancelado' }}</span></p>
                <p>{{ a.horario.profissional.nome }}, {{ hr(a.horario.horaInicio) }} às {{ hr(a.horario.horaFim) }}</p>
                @if (a.observacoes) { <p class="obs">{{ a.observacoes }}</p> }
              </div>
              @if (a.status === 'CONFIRMADO' && a.horario.data >= today) {
                <div class="row__act"><button class="btn btn--line" (click)="reschedule.emit(a)">Remarcar</button><button class="link link--danger" (click)="target.set(a)">Cancelar</button></div>
              }
            </li>
          }
        </ul>
        @if (st.data.totalPaginas > 1) {
          <div class="pager"><button class="btn btn--line" [disabled]="page() === 0" (click)="page.set(page() - 1)">Anterior</button><span>Página {{ page() + 1 }} de {{ st.data.totalPaginas }}</span><button class="btn btn--line" [disabled]="page() + 1 >= st.data.totalPaginas" (click)="page.set(page() + 1)">Próxima</button></div>
        }
      }
    </section>

    <dialog #dlg class="dlg" aria-labelledby="x-title" (close)="target.set(null)">
      @if (target(); as t) {
        <h2 id="x-title">Cancelar esta sessão?</h2>
        <p class="muted">{{ names[t.horario.tipoSessao] }} em {{ day(t.horario.data) }}, às {{ hr(t.horario.horaInicio) }}. O horário será liberado para outra pessoa.</p>
        @if (err()) { <p class="alert" role="alert">{{ err() }}</p> }
        <div class="actions"><button class="btn btn--line" [disabled]="busy()" (click)="target.set(null)">Manter sessão</button><button class="btn btn--danger" [disabled]="busy()" (click)="cancel()">{{ busy() ? 'Cancelando…' : 'Sim, cancelar' }}</button></div>
      }
    </dialog>
  `,
})
export class Appointments {
  private api = inject(Api);
  refresh = input(0);
  reschedule = output<Appointment>();
  notice = output<string>();
  browse = output<void>();
  protected today = clinicToday();
  protected names = sessionNames;
  protected bands = sessionBand;
  protected hr = hour;
  page = signal(0);
  retry = signal(0);
  target = signal<Appointment | null>(null);
  busy = signal(false);
  err = signal('');
  private query = computed(() => ({ p: this.page(), n: this.refresh() + this.retry() }));
  protected state = toSignal(toObservable(this.query).pipe(switchMap(q =>
    this.api.request<Page>('GET', `/agendamentos/meus?pagina=${q.p}&tamanho=6`).pipe(
      map(data => ({ loading: false, data, error: '' })),
      catchError(e => of({ loading: false, data: empty, error: (e as Error).message })),
      startWith({ loading: true, data: empty, error: '' })))), { initialValue: { loading: true, data: empty, error: '' } });
  private dlg = viewChild.required<ElementRef<HTMLDialogElement>>('dlg');

  constructor() {
    effect(() => {
      const el = this.dlg().nativeElement, open = !!this.target();
      if (open && !el.open) el.showModal();
      if (!open && el.open) el.close();
    });
  }
  month(d: string) { return dateLabel(d, { month: 'short' }).replace('.', ''); }
  day(d: string) { return dateLabel(d); }
  async cancel() {
    const t = this.target();
    if (!t) return;
    this.busy.set(true); this.err.set('');
    try {
      await firstValueFrom(this.api.request('PUT', `/agendamentos/${t.id}`, { acao: 'CANCELAR' }));
      this.notice.emit('Agendamento cancelado. Você pode escolher um novo horário quando quiser.');
      this.target.set(null); this.retry.update(n => n + 1);
    } catch (e) { this.err.set((e as Error).message); } finally { this.busy.set(false); }
  }
}
