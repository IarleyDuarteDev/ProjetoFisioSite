import { Component, ElementRef, afterNextRender, effect, inject, input, linkedSignal, output, signal, viewChild } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Api, Session } from './api';
import { formatCpf } from './format';
import { APPLE_JS, GOOGLE_JS, carregarScript } from './social';
import { ICONS } from './icons';

export type AuthMode = 'login' | 'register' | 'forgot' | 'reset';

@Component({
  selector: 'app-auth-dialog',
  template: `
    <dialog #dlg class="dlg" aria-labelledby="a-title" (cancel)="$event.preventDefault(); close()">
      <button class="icon dlg__x" aria-label="Fechar" [disabled]="busy()" (click)="close()"><svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path [attr.d]="ic.close" /></svg></button>
      <h2 id="a-title">{{ titles[mode()] }}</h2>
      <p class="muted">{{ mode() === 'login' ? 'Entre para confirmar e acompanhar suas sessões.' : mode() === 'register' ? 'Crie sua conta e encontre seu próximo horário.' : 'Informe seus dados para continuar.' }}</p>
      @if (mode() === 'login' || mode() === 'register') {
        <div class="seg"><button [disabled]="busy()" [class.is-on]="mode() === 'login'" (click)="go('login')">Entrar</button><button [disabled]="busy()" [class.is-on]="mode() === 'register'" (click)="go('register')">Criar conta</button></div>
      }
      <form (submit)="submit($event)">
        @if (mode() === 'register') { <label class="field"><span>Nome completo</span><input name="nome" required maxlength="120" autocomplete="name" placeholder="Como podemos chamar você?" /></label> }
        @if (mode() !== 'reset') { <label class="field"><span>E-mail</span><input name="email" type="email" required maxlength="254" autocomplete="email" placeholder="voce@exemplo.com" /></label> }
        @if (mode() === 'register') { <label class="field"><span>CPF</span><input name="cpf" required inputmode="numeric" maxlength="14" autocomplete="off" placeholder="000.000.000-00" (input)="maskCpf($event)" /></label> }
        @if (mode() !== 'forgot') { <label class="field"><span>{{ mode() === 'reset' ? 'Nova senha' : 'Senha' }}</span><input name="senha" type="password" required minlength="8" maxlength="72" [attr.autocomplete]="mode() === 'login' ? 'current-password' : 'new-password'" placeholder="Pelo menos 8 caracteres" /></label> }
        @if (error()) { <p class="alert" role="alert">{{ error() }}</p> }
        @if (message()) { <p class="okmsg" role="status">{{ message() }}</p> }
        <button class="btn btn--full" [disabled]="busy()">{{ busy() ? 'Aguarde…' : mode() === 'login' ? 'Entrar na minha conta' : mode() === 'register' ? 'Criar minha conta' : mode() === 'forgot' ? 'Enviar link' : 'Salvar nova senha' }}</button>
      </form>
      @if ((mode() === 'login' || mode() === 'register') && (prov().google || prov().apple)) {
        <div class="social">
          <p class="social__ou"><span>ou</span></p>
          @if (prov().google) { <div #gbtn class="social__google"></div> }
          @if (prov().apple) { <button type="button" class="btn btn--line btn--full" [disabled]="busy()" (click)="apple()">Continuar com a Apple</button> }
        </div>
      }
      @if (mode() === 'login') { <button class="link center" (click)="go('forgot')">Esqueci minha senha</button> }
      @if (mode() === 'forgot' || mode() === 'reset') { <button class="link center" (click)="go('login')">Voltar para o login</button> }
    </dialog>
  `,
})
export class AuthDialog {
  private api = inject(Api);
  mode0 = input<AuthMode>('login', { alias: 'mode' });
  token = input<string>();
  closed = output<void>();
  done = output<Session>();
  protected ic = ICONS;
  protected titles: Record<AuthMode, string> = { login: 'Entrar na sua conta', register: 'Criar sua conta', forgot: 'Recuperar acesso', reset: 'Criar nova senha' };
  mode = linkedSignal<AuthMode>(() => this.mode0());
  busy = signal(false);
  error = signal('');
  message = signal('');
  private dlg = viewChild.required<ElementRef<HTMLDialogElement>>('dlg');

  protected prov = signal<{ google: string; apple: string }>({ google: '', apple: '' });
  private gbtn = viewChild<ElementRef<HTMLElement>>('gbtn');

  constructor() {
    afterNextRender(() => this.dlg().nativeElement.showModal());
    firstValueFrom(this.api.request<{ google: string; apple: string }>('GET', '/auth/provedores')).then(p => this.prov.set(p)).catch(() => undefined);
    // Desenha o botão oficial do Google quando o contêiner aparece (login ou cadastro).
    effect(() => {
      const el = this.gbtn()?.nativeElement, id = this.prov().google;
      if (!el || !id) return;
      carregarScript(GOOGLE_JS).then(() => {
        const g = (window as any).google.accounts.id;
        g.initialize({ client_id: id, callback: (r: { credential: string }) => this.entrarSocial('google', r.credential) });
        g.renderButton(el, { theme: 'outline', size: 'large', text: 'continue_with', locale: 'pt-BR', width: 320 });
      }).catch(() => this.error.set('Não foi possível carregar o login do Google.'));
    });
  }
  async entrarSocial(provedor: 'google' | 'apple', token: string, nome?: string) {
    this.busy.set(true); this.error.set('');
    try { this.done.emit(await firstValueFrom(this.api.request<Session>('POST', `/auth/${provedor}`, { token, nome }))); }
    catch (e) { this.error.set((e as Error).message); } finally { this.busy.set(false); }
  }
  async apple() {
    try {
      await carregarScript(APPLE_JS);
      const auth = (window as any).AppleID.auth;
      auth.init({ clientId: this.prov().apple, scope: 'name email', redirectURI: location.origin, usePopup: true });
      const r = await auth.signIn();
      const n = r.user?.name;
      await this.entrarSocial('apple', r.authorization.id_token, n ? `${n.firstName ?? ''} ${n.lastName ?? ''}`.trim() : undefined);
    } catch (e) { if ((e as { error?: string })?.error !== 'popup_closed_by_user') this.error.set('Não foi possível entrar com a Apple.'); }
  }
  go(m: AuthMode) { this.mode.set(m); this.error.set(''); this.message.set(''); }
  maskCpf(event: Event) { const el = event.target as HTMLInputElement; el.value = formatCpf(el.value); }
  close() { if (!this.busy()) this.closed.emit(); }

  async submit(event: Event) {
    event.preventDefault();
    const form = event.target as HTMLFormElement;
    const data = Object.fromEntries(new FormData(form)) as Record<string, string>;
    this.busy.set(true); this.error.set(''); this.message.set('');
    try {
      const m = this.mode();
      if (m === 'forgot') this.message.set((await firstValueFrom(this.api.request<{ mensagem: string }>('POST', '/auth/forgot-password', data))).mensagem);
      else if (m === 'reset') { const r = await firstValueFrom(this.api.request<{ mensagem: string }>('POST', '/auth/reset-password', { token: this.token(), novaSenha: data['senha'] })); this.mode.set('login'); this.message.set(r.mensagem); }
      else this.done.emit(await firstValueFrom(this.api.request<Session>('POST', `/auth/${m}`, data)));
    } catch (e) { this.error.set((e as Error).message); } finally { this.busy.set(false); }
  }
}
