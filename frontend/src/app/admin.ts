import { Component, inject, signal } from '@angular/core';
import { Observable, firstValueFrom } from 'rxjs';
import { Api, AgendamentoAdmin, HorarioAdmin, PageAdmin, Pro } from './api';
import { addDays, clinicToday, dateLabel, hour, sessionBand, sessionNames } from './format';

type Aba = 'agendamentos' | 'horarios' | 'profissionais';

/** Área exclusiva do administrador. O servidor confere o papel ADMIN em cada chamada; esconder a aba é só conveniência. */
@Component({
  selector: 'app-admin',
  template: `
    <section class="mine adm" aria-label="Administração">
      <h1>Administração</h1>
      <div class="seg" role="group" aria-label="Seções">
        @for (a of abas; track a[0]) { <button [class.is-on]="aba() === a[0]" [attr.aria-pressed]="aba() === a[0]" (click)="aba.set(a[0])">{{ a[1] }}</button> }
      </div>
      @if (ok()) { <div class="notice" role="status"><span>{{ ok() }}</span><button class="link" (click)="ok.set('')">Fechar</button></div> }
      @if (erro()) { <div class="alert" role="alert">{{ erro() }}</div> }

      @switch (aba()) {
        @case ('agendamentos') {
          @if (lista(); as l) {
            @if (!l.itens.length) { <div class="empty"><h3>Nenhum agendamento ainda.</h3></div> }
            <ul class="rows">
              @for (a of l.itens; track a.agendamento.id) {
                <li class="row">
                  <div class="when"><strong>{{ a.agendamento.horario.data.slice(8) }}</strong><span>{{ mes(a.agendamento.horario.data) }}</span></div>
                  <div class="what">
                    <p class="what__t"><i class="band band--{{ bands[a.agendamento.horario.tipoSessao] }}"></i>{{ names[a.agendamento.horario.tipoSessao] }}
                      <span class="status" [class.is-off]="a.agendamento.status === 'CANCELADO'">{{ a.agendamento.status === 'CONFIRMADO' ? 'Confirmado' : 'Cancelado' }}</span></p>
                    <p>{{ hr(a.agendamento.horario.horaInicio) }} com {{ a.agendamento.horario.profissional.nome }}</p>
                    <p class="obs">{{ a.clienteNome }}, {{ a.clienteEmail }}, CPF {{ a.clienteCpf ?? 'não informado' }}</p>
                  </div>
                  @if (a.agendamento.status === 'CONFIRMADO') {
                    <div class="row__act">
                      @if (confirmando() === a.agendamento.id) {
                        <button class="btn btn--danger" [disabled]="busy()" (click)="cancelar(a)">Confirmar cancelamento</button>
                        <button class="link" (click)="confirmando.set(null)">Voltar</button>
                      } @else { <button class="link link--danger" (click)="confirmando.set(a.agendamento.id)">Cancelar</button> }
                    </div>
                  }
                </li>
              }
            </ul>
            @if (l.totalPaginas > 1) {
              <div class="pager"><button class="btn btn--line" [disabled]="pagina() === 0" (click)="irPara(pagina() - 1)">Anterior</button><span>Página {{ pagina() + 1 }} de {{ l.totalPaginas }}</span><button class="btn btn--line" [disabled]="pagina() + 1 >= l.totalPaginas" (click)="irPara(pagina() + 1)">Próxima</button></div>
            }
          }
        }
        @case ('horarios') {
          <form class="form-grid" (submit)="criarHorarios($event)">
            <label class="field"><span>Data</span><input type="date" [min]="hoje" [value]="dia()" (change)="mudarDia($any($event.target).value)" required /></label>
            <label class="field"><span>Profissional</span><select name="profissionalId" required>@for (p of pros(); track p.id) { <option [value]="p.id">{{ p.nome }}</option> }</select></label>
            <label class="field"><span>Tipo de sessão</span><select name="tipoSessao">@for (t of tipos; track t[0]) { <option [value]="t[0]">{{ t[1] }}</option> }</select></label>
            <label class="field"><span>Inícios (separe por vírgula)</span><input name="inicios" required placeholder="08:00, 09:00, 14:30" autocomplete="off" /></label>
            <label class="field"><span>Duração (min)</span><input name="duracao" type="number" min="10" max="180" value="50" required /></label>
            <button class="btn" [disabled]="busy() || !pros().length">Criar horários</button>
          </form>
          <h2 class="dayline">{{ diaLongo() }}<span>{{ slots().length === 1 ? '1 horário' : slots().length + ' horários' }}</span></h2>
          @if (!slots().length) { <div class="empty"><p>Nenhum horário neste dia.</p></div> }
          <ul class="rows">
            @for (s of slots(); track s.horario.id) {
              <li class="row row--time">
                <div class="when"><strong>{{ hr(s.horario.horaInicio) }}</strong><span>até {{ hr(s.horario.horaFim) }}</span></div>
                <div class="what"><p class="what__t"><i class="band band--{{ bands[s.horario.tipoSessao] }}"></i>{{ names[s.horario.tipoSessao] }}
                  <span class="status" [class.is-off]="s.status === 'RESERVADO'">{{ s.status === 'RESERVADO' ? 'Reservado' : 'Livre' }}</span></p><p>{{ s.horario.profissional.nome }}</p></div>
                @if (s.status === 'DISPONIVEL') { <div class="row__act"><button class="link link--danger" [disabled]="busy()" (click)="remover(s)">Remover</button></div> }
              </li>
            }
          </ul>
        }
        @case ('profissionais') {
          <form class="form-grid" (submit)="criarProfissional($event)">
            <label class="field"><span>Nome</span><input name="nome" required maxlength="120" autocomplete="off" /></label>
            <label class="field"><span>Especialidade</span><input name="especialidade" required maxlength="120" autocomplete="off" /></label>
            <button class="btn" [disabled]="busy()">Cadastrar profissional</button>
          </form>
          <ul class="rows">
            @for (p of pros(); track p.id) { <li class="row row--simple"><div class="what"><p class="what__t">{{ p.nome }}</p></div></li> }
          </ul>
        }
      }
    </section>
  `,
})
export class Admin {
  private api = inject(Api);
  protected hoje = clinicToday();
  protected names = sessionNames;
  protected bands = sessionBand;
  protected tipos = Object.entries(sessionNames);
  protected hr = hour;
  protected abas: [Aba, string][] = [['agendamentos', 'Agendamentos'], ['horarios', 'Horários'], ['profissionais', 'Profissionais']];
  aba = signal<Aba>('agendamentos');
  lista = signal<PageAdmin | null>(null);
  pagina = signal(0);
  confirmando = signal<string | null>(null);
  pros = signal<Pro[]>([]);
  dia = signal(addDays(clinicToday(), 1));
  slots = signal<HorarioAdmin[]>([]);
  erro = signal('');
  ok = signal('');
  busy = signal(false);

  constructor() { this.carregarAgendamentos(); this.carregarProfissionais(); this.carregarHorarios(); }

  mes(d: string) { return dateLabel(d, { month: 'short' }).replace('.', ''); }
  diaLongo() { const t = dateLabel(this.dia(), { weekday: 'long', day: 'numeric', month: 'long' }); return t.charAt(0).toUpperCase() + t.slice(1); }

  private async chamar<T>(req: Observable<T>): Promise<T | undefined> {
    this.erro.set(''); this.busy.set(true);
    try { return await firstValueFrom(req); } catch (e) { this.erro.set((e as Error).message); return undefined; } finally { this.busy.set(false); }
  }
  async carregarAgendamentos() {
    const r = await this.chamar(this.api.request<PageAdmin>('GET', `/admin/agendamentos?pagina=${this.pagina()}&tamanho=10`));
    if (r) this.lista.set(r);
  }
  irPara(p: number) { this.pagina.set(p); this.carregarAgendamentos(); }
  async cancelar(a: AgendamentoAdmin) {
    this.ok.set('');
    const r = await this.chamar(this.api.request('PUT', `/admin/agendamentos/${a.agendamento.id}/cancelar`));
    this.confirmando.set(null);
    if (r) { this.ok.set('Agendamento cancelado. O horário voltou a ficar livre.'); this.carregarAgendamentos(); this.carregarHorarios(); }
  }
  async carregarProfissionais() { const r = await this.chamar(this.api.request<Pro[]>('GET', '/profissionais')); if (r) this.pros.set(r); }
  async carregarHorarios() { const r = await this.chamar(this.api.request<HorarioAdmin[]>('GET', `/admin/horarios?data=${this.dia()}`)); if (r) this.slots.set(r); }
  mudarDia(valor: string) { if (valor) { this.dia.set(valor); this.carregarHorarios(); } }

  async criarHorarios(event: Event) {
    event.preventDefault();
    this.ok.set('');
    const f = new FormData(event.target as HTMLFormElement);
    const inicios = String(f.get('inicios')).split(/[\s,;]+/).filter(Boolean);
    if (!inicios.length || inicios.some(i => !/^([01]\d|2[0-3]):[0-5]\d$/.test(i))) { this.ok.set(''); this.erro.set('Informe os inícios no formato 08:00, 09:00.'); return; }
    const corpo = { profissionalId: f.get('profissionalId'), data: this.dia(), tipoSessao: f.get('tipoSessao'), inicios, duracaoMinutos: Number(f.get('duracao')) };
    const r = await this.chamar(this.api.request<HorarioAdmin[]>('POST', '/admin/horarios', corpo));
    if (r) { this.ok.set(r.length === 1 ? '1 horário criado.' : `${r.length} horários criados.`); this.carregarHorarios(); }
  }
  async remover(s: HorarioAdmin) {
    this.ok.set('');
    const r = await this.chamar(this.api.request('DELETE', `/admin/horarios/${s.horario.id}`));
    if (r !== undefined || !this.erro()) { this.ok.set('Horário removido.'); this.carregarHorarios(); }
  }
  async criarProfissional(event: Event) {
    event.preventDefault();
    this.ok.set('');
    const form = event.target as HTMLFormElement;
    const f = new FormData(form);
    const r = await this.chamar(this.api.request<Pro>('POST', '/admin/profissionais', { nome: f.get('nome'), especialidade: f.get('especialidade') }));
    if (r) { form.reset(); this.ok.set('Profissional cadastrado.'); this.carregarProfissionais(); }
  }
}
