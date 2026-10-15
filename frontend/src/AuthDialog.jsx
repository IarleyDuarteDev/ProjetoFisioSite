import { useEffect, useRef, useState } from 'react';
import { X } from 'lucide-react';
import { api } from './api';

export default function AuthDialog({ initialMode = 'login', resetToken, onClose, onSuccess }) {
  const ref = useRef(null);
  const [mode, setMode] = useState(initialMode);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  useEffect(() => { ref.current.showModal(); }, []);
  const titles = { login: 'Entrar na sua conta', register: 'Criar sua conta', forgot: 'Recuperar acesso', reset: 'Criar nova senha' };
  function change(next) { setMode(next); setError(''); setMessage(''); }
  async function submit(event) {
    event.preventDefault(); setBusy(true); setError(''); setMessage('');
    const data = Object.fromEntries(new FormData(event.currentTarget));
    try {
      if (mode === 'forgot') {
        const result = await api('/auth/forgot-password', { method: 'POST', body: data });
        setMessage(result.mensagem);
      } else if (mode === 'reset') {
        const result = await api('/auth/reset-password', { method: 'POST', body: { token: resetToken, novaSenha: data.senha } });
        setMode('login'); setMessage(result.mensagem);
      } else {
        const result = await api(`/auth/${mode}`, { method: 'POST', body: data });
        onSuccess(result);
      }
    } catch (err) { setError(err.message); } finally { setBusy(false); }
  }
  return <dialog ref={ref} className="auth-dialog" aria-labelledby="auth-title" onCancel={event => { event.preventDefault(); if (!busy) onClose(); }}>
    <button className="icon-button close" aria-label="Fechar" onClick={onClose} disabled={busy}><X size={20} /></button>
    <h2 id="auth-title">{titles[mode]}</h2>
    <p className="muted">{mode === 'login' ? 'Entre para confirmar e acompanhar suas sessões.' : mode === 'register' ? 'Crie sua conta e encontre seu próximo horário.' : 'Informe seus dados para continuar.'}</p>
    {(mode === 'login' || mode === 'register') && <div className="auth-tabs">
      <button disabled={busy} className={mode === 'login' ? 'active' : ''} onClick={() => change('login')}>Entrar</button>
      <button disabled={busy} className={mode === 'register' ? 'active' : ''} onClick={() => change('register')}>Criar conta</button>
    </div>}
    <form onSubmit={submit} key={mode}>
      {mode === 'register' && <label>Nome completo<input name="nome" required maxLength={120} autoComplete="name" placeholder="Como podemos chamar você?" /></label>}
      {mode !== 'reset' && <label>E-mail<input name="email" type="email" required maxLength={254} autoComplete="email" placeholder="voce@exemplo.com" /></label>}
      {mode !== 'forgot' && <label>{mode === 'reset' ? 'Nova senha' : 'Senha'}<input name="senha" type="password" required minLength={mode === 'login' ? 1 : 8} maxLength={72} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} placeholder={mode === 'login' ? 'Sua senha' : 'Pelo menos 8 caracteres'} /></label>}
      {error && <p className="message error" role="alert">{error}</p>}
      {message && <p className="message success" role="status">{message}</p>}
      <button className="button primary full" disabled={busy}>{busy ? 'Aguarde…' : { login: 'Entrar na minha conta', register: 'Criar minha conta', forgot: 'Enviar link de recuperação', reset: 'Salvar nova senha' }[mode]}</button>
    </form>
    {mode === 'login' && <button className="text-button centered" onClick={() => change('forgot')}>Esqueci minha senha</button>}
    {mode === 'forgot' && <button className="text-button centered" onClick={() => change('login')}>Voltar para entrar</button>}
  </dialog>;
}
