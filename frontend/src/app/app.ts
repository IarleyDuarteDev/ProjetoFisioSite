import { Component, effect, inject, signal } from '@angular/core';
import { Api, Appointment, Session } from './api';
import { Appointments } from './appointments';
import { AuthDialog, AuthMode } from './auth-dialog';
import { Booking } from './booking';
import { ICONS } from './icons';

@Component({
  selector: 'app-root',
  imports: [Booking, Appointments, AuthDialog],
  template: `
    <header class="top"><div class="wrap top__in">
      <a class="brand" href="#" aria-label="Fisio Clínica, início" (click)="$event.preventDefault(); view.set('agendar')"><span class="loops" aria-hidden="true"></span>fisioclínica</a>
      <nav class="tabs" aria-label="Navegação principal">
        <button [attr.aria-current]="view() === 'agendar' ? 'page' : null" (click)="view.set('agendar')">Agendar sessão</button>
        <button [attr.aria-current]="view() === 'meus' ? 'page' : null" (click)="showMine()">Meus agendamentos</button>
      </nav>
      @if (api.session(); as s) {
        <div class="who"><span>{{ s.usuario.nome.split(' ')[0] }}</span><button class="icon" title="Sair" aria-label="Sair da conta" (click)="logout()"><svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path [attr.d]="ic.logout" /></svg></button></div>
      } @else { <button class="btn btn--line" (click)="auth.set({ mode: 'login' })">Entrar</button> }
    </div></header>
    <main class="wrap main">
      @if (notice()) { <div class="notice" role="status"><span>{{ notice() }}</span><button class="icon" aria-label="Dispensar mensagem" (click)="notice.set('')"><svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path [attr.d]="ic.close" /></svg></button></div> }
      @if (view() === 'agendar') {
        <app-booking [rescheduling]="rescheduling()" [refresh]="refresh()" (needLogin)="auth.set({ mode: 'login' })" (booked)="booked($event)" (cancelReschedule)="rescheduling.set(null)" />
      } @else {
        <app-appointments [refresh]="refresh()" (reschedule)="startReschedule($event)" (notice)="notice.set($event)" (browse)="view.set('agendar')" />
      }
    </main>
    <footer class="wrap foot">Fisio Clínica. Cada sessão no horário que cabe na sua rotina.</footer>
    @if (auth(); as a) { <app-auth-dialog [mode]="a.mode" [token]="a.token" (closed)="auth.set(null)" (done)="login($event)" /> }
  `,
})
export class App {
  protected api = inject(Api);
  protected ic = ICONS;
  view = signal<'agendar' | 'meus'>('agendar');
  notice = signal('');
  auth = signal<{ mode: AuthMode; token?: string } | null>(null);
  rescheduling = signal<Appointment | null>(null);
  refresh = signal(0);

  constructor() {
    const reset = location.hash.match(/^#redefinir=([A-Za-z0-9_-]{43})$/);
    if (reset) { this.auth.set({ mode: 'reset', token: reset[1] }); history.replaceState(null, '', location.pathname); }
    effect(() => {
      if (!this.api.expired()) return;
      this.view.set('agendar'); this.auth.set({ mode: 'login' }); this.notice.set('Sua sessão expirou. Entre novamente para continuar.');
    });
  }
  login(s: Session) { this.api.session.set(s); this.auth.set(null); this.notice.set(`Bem-vindo(a), ${s.usuario.nome.split(' ')[0]}! Escolha seu horário para continuar.`); }
  logout() { this.api.session.set(null); this.view.set('agendar'); this.rescheduling.set(null); this.notice.set('Você saiu da sua conta.'); }
  showMine() { if (!this.api.session()) { this.auth.set({ mode: 'login' }); return; } this.rescheduling.set(null); this.view.set('meus'); }
  startReschedule(a: Appointment) { this.rescheduling.set(a); this.view.set('agendar'); this.notice.set('Escolha um novo horário. Sua sessão atual permanece reservada até a confirmação.'); }
  booked(message: string) { this.notice.set(message); this.rescheduling.set(null); this.view.set('meus'); this.refresh.update(n => n + 1); }
}
