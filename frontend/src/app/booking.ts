import { Component, ElementRef, computed, effect, inject, input, linkedSignal, output, signal, viewChild } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, firstValueFrom, map, of, startWith, switchMap } from 'rxjs';
import { Api, ApiError, Appointment, Pro, Slot, Usuario } from './api';
import { ICONS } from './icons';
import { addDays, clinicToday, dateLabel, formatCpf, hour, initials, sessionBand, sessionNames } from './format';

@Component({
  selector: 'app-booking',
  template: `
    <section class="head">
      <div>
        <h1>{{ rescheduling() ? 'Escolha um novo horário' : 'Escolha seu horário' }}</h1>
        <p class="sub">Veja quem está livre em cada dia e toque no horário que cabe na sua rotina.</p>
      </div>
      <div class="filters">
        <div class="legend" role="group" aria-label="Tipo de sessão">
          <button class="chip" [attr.aria-pressed]="type() === ''" (click)="type.set('')">Todas</button>
          @for (t of types; track t[0]) {
            <button class="chip" [attr.aria-pressed]="type() === t[0]" (click)="type.set(t[0])"><i class="band band--{{ bands[t[0]] }}"></i>{{ t[1] }}</button>
          }
        </div>
        <label class="select"><span>Profissional</span>
          <select (change)="pro.set($any($event.target).value)"><option value="">Todos</option>@for (p of pros(); track p.id) { <option [value]="p.id">{{ p.nome }}</option> }</select>
        </label>
      </div>
    </section>

    <div class="week">
      <button class="icon" aria-label="Semana anterior" [disabled]="week() <= today" (click)="shift(-7)"><svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path [attr.d]="ic.left" /></svg></button>
      <div class="days" role="group" aria-label="Dias da semana">
        @for (d of days(); track d) {
          <button class="day" [class.is-on]="d === date()" [attr.aria-pressed]="d === date()" [attr.aria-label]="longDay(d)" (click)="date.set(d)">
            <span>{{ short(d) }}</span><strong>{{ d.slice(8) }}</strong>@if (d === today) { <small>hoje</small> }
          </button>
        }
      </div>
      <button class="icon" aria-label="Próxima semana" (click)="shift(7)"><svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path [attr.d]="ic.right" /></svg></button>
      <label class="other"><span class="sr-only">Outra data</span><input type="date" [min]="today" [value]="date()" (change)="pick($any($event.target).value)" /></label>
    </div>

    @if (rescheduling(); as r) {
      <div class="banner"><span>Remarcando a sessão de {{ label(r.horario.data) }} às {{ hr(r.horario.horaInicio) }}. Ela continua reservada até você confirmar.</span><button class="link" (click)="cancelReschedule.emit()">Desistir</button></div>
    }

    @let st = state();
    <h2 class="dayline">{{ longDay(date()) }}@if (!st.loading && !st.error) { <span aria-live="polite">{{ slots().length === 1 ? '1 horário livre' : slots().length + ' horários livres' }}</span> }</h2>
    @if (msg() && !selected()) { <div class="alert" role="alert">{{ msg() }}</div> }

    @if (st.loading) {
      <div class="board" role="status"><span class="sr-only">Buscando horários…</span>@for (n of [1, 2, 3]; track n) { <div class="col sk"><i></i><i></i><i></i></div> }</div>
    } @else if (st.error) {
      <div class="alert" role="alert">{{ st.error }}<button class="link" (click)="retry.update(n => n + 1)">Tentar novamente</button></div>
    } @else if (!slots().length) {
      <div class="empty"><svg class="ico big" viewBox="0 0 24 24" aria-hidden="true"><path [attr.d]="ic.empty" /></svg><h3>Nenhum horário livre neste dia.</h3><p>Escolha outro dia{{ type() || pro() ? ' ou remova os filtros' : '' }}.</p>
        @if (type() || pro()) { <button class="btn btn--line" (click)="clear()">Remover filtros</button> }</div>
    } @else {
      <div class="board">
        @for (g of groups(); track g.pro.id) {
          <section class="col">
            <header class="col__head"><span class="avatar" aria-hidden="true">{{ ini(g.pro.nome) }}</span><div><h3>{{ g.pro.nome }}</h3><p>{{ g.slots.length === 1 ? '1 horário' : g.slots.length + ' horários' }}</p></div></header>
            <div class="tiles">
              @for (s of g.slots; track s.id; let i = $index) {
                <button class="tile" [style.--i]="i" (click)="choose(s)"><strong>{{ hr(s.horaInicio) }}</strong><i class="band band--{{ bands[s.tipoSessao] }}"></i><small>{{ names[s.tipoSessao] }}</small></button>
              }
            </div>
          </section>
        }
      </div>
    }
    <p class="note">Horários no fuso da clínica (Rio Branco).</p>

    <dialog #dlg class="dlg" aria-labelledby="c-title" (close)="selected.set(null)">
      @if (selected(); as s) {
        <form (submit)="confirm($event)">
          <h2 id="c-title">{{ rescheduling() ? 'Confirmar remarcação' : 'Falta só confirmar' }}</h2>
          <dl class="facts">
            <div><dt>Sessão</dt><dd><i class="band band--{{ bands[s.tipoSessao] }}"></i>{{ names[s.tipoSessao] }}</dd></div>
            <div><dt>Profissional</dt><dd>{{ s.profissional.nome }}</dd></div>
            <div><dt>Data e horário</dt><dd>{{ label(s.data) }}, {{ hr(s.horaInicio) }}</dd></div>
          </dl>
          @if (!rescheduling() && cpfPendente) { <label class="field"><span>CPF (necessário para agendar)</span><input name="cpf" required inputmode="numeric" maxlength="14" autocomplete="off" placeholder="000.000.000-00" (input)="maskCpf($event)" /></label> }
          @if (!rescheduling()) { <label class="field"><span>Observações (opcional)</span><textarea name="observacoes" rows="3" maxlength="1000" placeholder="Algo que a gente deva saber?"></textarea></label> }
          @if (msg()) { <p class="alert" role="alert">{{ msg() }}</p> }
          <div class="actions"><button type="button" class="btn btn--line" (click)="selected.set(null)">Voltar</button><button class="btn" [disabled]="busy()">{{ busy() ? 'Confirmando…' : !api.session() ? 'Entrar para confirmar' : rescheduling() ? 'Confirmar remarcação' : 'Confirmar agendamento' }}</button></div>
        </form>
      }
    </dialog>
  `,
})
export class Booking {
  protected api = inject(Api);
  rescheduling = input<Appointment | null>(null);
  refresh = input(0);
  needLogin = output<void>();
  booked = output<string>();
  cancelReschedule = output<void>();

  protected today = clinicToday();
  protected ic = ICONS;
  protected names = sessionNames;
  protected bands = sessionBand;
  protected types = Object.entries(sessionNames);
  protected hr = hour;
  protected ini = initials;
  week = signal(this.today);
  date = signal(this.today);
  type = signal('');
  pro = signal('');
  retry = signal(0);
  busy = signal(false);
  msg = signal('');
  private gone = signal<string[]>([]);
  days = computed(() => Array.from({ length: 7 }, (_, i) => addDays(this.week(), i)));
  pros = toSignal(this.api.request<Pro[]>('GET', '/profissionais').pipe(catchError(() => of([] as Pro[]))), { initialValue: [] as Pro[] });

  private query = computed(() => ({ date: this.date(), type: this.type(), pro: this.pro(), n: this.refresh() + this.retry() }));
  protected state = toSignal(toObservable(this.query).pipe(switchMap(q => {
    const params = new URLSearchParams({ data: q.date });
    if (q.pro) params.set('profissionalId', q.pro);
    if (q.type) params.set('tipoSessao', q.type);
    return this.api.request<Slot[]>('GET', `/horarios/disponiveis?${params}`).pipe(
      map(slots => ({ loading: false, slots, error: '' })),
      catchError(e => of({ loading: false, slots: [] as Slot[], error: (e as Error).message })),
      startWith({ loading: true, slots: [] as Slot[], error: '' }));
  })), { initialValue: { loading: true, slots: [] as Slot[], error: '' } });
  protected slots = computed(() => this.state().slots.filter(s => !this.gone().includes(s.id)));
  /** Uma coluna por profissional, como no quadro da recepção. */
  protected groups = computed(() => {
    const map = new Map<string, { pro: Pro; slots: Slot[] }>();
    for (const s of this.slots()) (map.get(s.profissional.id) ?? map.set(s.profissional.id, { pro: s.profissional, slots: [] }).get(s.profissional.id)!).slots.push(s);
    return [...map.values()];
  });
  selected = linkedSignal<Slot | null>(() => { this.query(); return null; });
  private dlg = viewChild.required<ElementRef<HTMLDialogElement>>('dlg');

  constructor() {
    effect(() => {
      const el = this.dlg().nativeElement, open = !!this.selected();
      if (open && !el.open) el.showModal();
      if (!open && el.open) el.close();
    });
  }
  short(d: string) { return dateLabel(d, { weekday: 'short' }).replace('.', ''); }
  longDay(d: string) { return dateLabel(d, { weekday: 'long', day: 'numeric', month: 'long' }); }
  label(d: string) { return dateLabel(d); }
  shift(days: number) { this.week.update(w => { const next = addDays(w, days); return next < this.today ? this.today : next; }); }
  pick(value: string) { if (value) { this.date.set(value); this.week.set(value); } }
  protected get cpfPendente() { return this.api.session()?.usuario.cpfPendente === true; }
  maskCpf(event: Event) { const el = event.target as HTMLInputElement; el.value = formatCpf(el.value); }
  clear() { this.type.set(''); this.pro.set(''); }
  choose(s: Slot) { this.msg.set(''); this.selected.set(s); }

  async confirm(event: Event) {
    event.preventDefault();
    const s = this.selected();
    if (!s) return;
    if (!this.api.session()) { this.needLogin.emit(); return; }
    const r = this.rescheduling();
    const notes = ((event.target as HTMLFormElement).elements.namedItem('observacoes') as HTMLTextAreaElement | null)?.value ?? '';
    this.busy.set(true); this.msg.set('');
    try {
      if (!r && this.api.session()?.usuario.cpfPendente) {
        const cpf = ((event.target as HTMLFormElement).elements.namedItem('cpf') as HTMLInputElement | null)?.value ?? '';
        const usuario = await firstValueFrom(this.api.request<Usuario>('PUT', '/auth/cpf', { cpf }));
        this.api.session.update(s => (s ? { ...s, usuario } : s));
      }
      await firstValueFrom(r ? this.api.request('PUT', `/agendamentos/${r.id}`, { acao: 'REMARCAR', novoHorarioId: s.id }) : this.api.request('POST', '/agendamentos', { horarioId: s.id, observacoes: notes }));
      this.selected.set(null);
      this.booked.emit(r ? 'Sessão remarcada! Seu novo horário está confirmado.' : 'Agendamento confirmado! Seu próximo passo para se sentir melhor está marcado.');
    } catch (e) {
      const err = e as ApiError;
      this.msg.set(err.message);
      if (err.status === 409) { this.gone.update(g => [...g, s.id]); this.selected.set(null); }
    } finally { this.busy.set(false); }
  }
}
