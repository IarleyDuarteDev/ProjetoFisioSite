import { useEffect, useRef, useState } from 'react';
import { CalendarX, Check, ChevronLeft, ChevronRight, LogOut, X } from 'lucide-react';
import { api, setAccessToken } from './api';
import AuthDialog from './AuthDialog';
import { addDays, clinicToday, dateLabel, hour, initials, sessionNames } from './format';

export default function App() {
  const [session, setSession] = useState(null);
  const [auth, setAuth] = useState(null);
  const [tab, setTab] = useState('agendar');
  const [date, setDate] = useState(() => addDays(clinicToday(), 1));
  const [week, setWeek] = useState(clinicToday);
  const [professional, setProfessional] = useState('');
  const [type, setType] = useState('');
  const [professionals, setProfessionals] = useState([]);
  const [slots, setSlots] = useState([]);
  const [selected, setSelected] = useState(null);
  const [rescheduling, setRescheduling] = useState(null);
  const [appointments, setAppointments] = useState({ itens: [], totalPaginas: 0 });
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [refresh, setRefresh] = useState(0);
  const cancelRef = useRef(null);
  const confirmRef = useRef(null);
  const [cancelTarget, setCancelTarget] = useState(null);

  useEffect(() => {
    const match = window.location.hash.match(/^#redefinir=([A-Za-z0-9_-]{43})$/);
    if (match) { setAuth({ mode: 'reset', token: match[1] }); window.history.replaceState(null, '', window.location.pathname); }
    const expired = () => { setSession(null); setAppointments({ itens: [], totalPaginas: 0 }); setTab('agendar'); setAuth({ mode: 'login' }); setNotice('Sua sessão expirou. Entre novamente para continuar.'); };
    window.addEventListener('session-expired', expired);
    return () => window.removeEventListener('session-expired', expired);
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    api('/profissionais', { signal: controller.signal }).then(setProfessionals).catch(() => {});
    return () => controller.abort();
  }, [refresh]);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError('');
    if (tab === 'meus' && !session) { setLoading(false); return () => controller.abort(); }
    const params = new URLSearchParams({ data: date });
    if (professional) params.set('profissionalId', professional);
    if (type) params.set('tipoSessao', type);
    const path = tab === 'agendar' ? `/horarios/disponiveis?${params}` : `/agendamentos/meus?pagina=${page}&tamanho=6`;
    api(path, { signal: controller.signal })
      .then(data => { if (tab === 'agendar') setSlots(data); else setAppointments(data); })
      .catch(err => { if (err.name !== 'AbortError') setError(err.message); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [date, professional, type, tab, page, refresh, session]);
  useEffect(() => { setSelected(null); }, [date, professional, type, tab, refresh]);
  useEffect(() => { if (cancelTarget) cancelRef.current.showModal(); }, [cancelTarget]);
  // Ao escolher um horário, leva o formulário de confirmação para a tela (importante no celular).
  useEffect(() => { if (selected) confirmRef.current?.scrollIntoView?.({ block: 'nearest' }); }, [selected]);

  function login(result) { setAccessToken(result.accessToken); setSession(result); setAuth(null); setNotice(`Bem-vindo(a), ${result.usuario.nome.split(' ')[0]}! Escolha seu horário para continuar.`); }
  function logout() { setAccessToken(null); setSession(null); setTab('agendar'); setSelected(null); setRescheduling(null); setAppointments({ itens: [], totalPaginas: 0 }); setNotice('Você saiu da sua conta.'); }
  function showMine() { if (!session) { setAuth({ mode: 'login' }); return; } setTab('meus'); setRescheduling(null); setPage(0); }
  function startReschedule(appointment) { setRescheduling(appointment); setSelected(null); setTab('agendar'); setNotice('Escolha um novo horário. Sua sessão atual permanece reservada até a confirmação.'); }
  async function book(event) {
    event.preventDefault();
    if (!session) { setAuth({ mode: 'login' }); return; }
    setBusy(true); setError('');
    const body = rescheduling ? { acao: 'REMARCAR', novoHorarioId: selected.id } : { horarioId: selected.id, observacoes: new FormData(event.currentTarget).get('observacoes') || '' };
    try {
      await api(rescheduling ? `/agendamentos/${rescheduling.id}` : '/agendamentos', { method: rescheduling ? 'PUT' : 'POST', body });
      setNotice(rescheduling ? 'Sessão remarcada! Seu novo horário está confirmado.' : 'Agendamento confirmado! Seu próximo passo para se sentir melhor está marcado.');
      setSelected(null); setRescheduling(null); setTab('meus'); setPage(0); setRefresh(n => n + 1);
    } catch (err) { setError(err.message); if (err.status === 409) { setSelected(null); setSlots(list => list.filter(slot => slot.id !== selected.id)); } }
    finally { setBusy(false); }
  }
  async function cancel() {
    setBusy(true); setError('');
    try {
      await api(`/agendamentos/${cancelTarget.id}`, { method: 'PUT', body: { acao: 'CANCELAR' } });
      setNotice('Agendamento cancelado. Você pode escolher um novo horário quando quiser.');
      setRefresh(n => n + 1);
    } catch (err) { setError(err.message); }
    finally { setBusy(false); setCancelTarget(null); }
  }
  const today = clinicToday();
  const days = Array.from({ length: 7 }, (_, index) => addDays(week, index));
  const minutes = slot => Number(hour(slot.horaInicio).slice(0, 2)) * 60 + Number(hour(slot.horaInicio).slice(3));
  const t = selected ? Math.min(1, Math.max(0, (minutes(selected) - 420) / 720)) : 0.18;
  const title = rescheduling ? 'Escolha um novo horário' : 'Agende sua fisioterapia em poucos toques.';
  const filtered = Boolean(type || professional);
  const confirmLabel = busy ? 'Confirmando…' : !session ? 'Entrar para confirmar' : rescheduling ? 'Confirmar remarcação' : 'Confirmar agendamento';
  return <>
    <header className="header"><div className="shell header-inner">
      <a className="brand" href="#" aria-label="Fisio Clínica, início" onClick={event => { event.preventDefault(); setTab('agendar'); }}><i className="brand-mark" aria-hidden="true" />fisioclínica</a>
      <nav className="main-nav" aria-label="Navegação principal">
        <button aria-current={tab === 'agendar' ? 'page' : undefined} onClick={() => setTab('agendar')}>Agendar sessão</button>
        <button aria-current={tab === 'meus' ? 'page' : undefined} onClick={showMine}>Meus agendamentos</button>
      </nav>
      {session
        ? <div className="account"><span>{session.usuario.nome.split(' ')[0]}</span><button className="icon-button" title="Sair" aria-label="Sair da conta" onClick={logout}><LogOut size={20} /></button></div>
        : <button className="button ghost" onClick={() => setAuth({ mode: 'login' })}>Entrar</button>}
    </div></header>
    <main className="shell main">
      {notice && <div className="notice" role="status"><Check size={20} /><span>{notice}</span><button className="icon-button" aria-label="Dispensar mensagem" onClick={() => setNotice('')}><X size={18} /></button></div>}
      {error && <div className="message error" role="alert">{error}<button className="text-button" onClick={() => setRefresh(n => n + 1)}>Tentar novamente</button></div>}
      {tab === 'agendar' ? <>
        <section className="hero">
          <div><h1 aria-label={title}>{title.split(' ').map((word, i) => <span key={i} aria-hidden="true" style={{ '--w': i }} className={['poucos', 'toques.'].includes(word) ? 'hl' : ''}>{word}&nbsp;</span>)}</h1><p>Escolha o dia, toque no horário e confirme. Leva menos de um minuto.</p></div>
          <div className={`arc ${selected ? 'set' : ''}`} style={{ '--t': t }} aria-hidden="true">
            <svg viewBox="0 0 380 220"><path d="M 15 205 A 175 175 0 0 1 365 205" /><path className="arc-fill" pathLength="1" d="M 15 205 A 175 175 0 0 1 365 205" /><circle className="arc-dot" r="14" /></svg>
            <div className="arc-label"><strong>{selected ? hour(selected.horaInicio) : '--:--'}</strong><span>{selected ? 'Seu horário' : 'Escolha um horário'}</span></div>
          </div>
        </section>
        <div className="booking">
          <section className="panel" aria-label="Horários disponíveis">
            {rescheduling && <div className="reschedule-banner"><span>Remarcando a sessão de {dateLabel(rescheduling.horario.data)} às {hour(rescheduling.horario.horaInicio)}</span><button className="text-button" onClick={() => { setRescheduling(null); setSelected(null); }}>Desistir</button></div>}
            <p className="group-label" id="type-label">Tipo de sessão</p>
            <div className="chips" role="group" aria-labelledby="type-label"><button className="chip" aria-pressed={type === ''} onClick={() => setType('')}>Todas as sessões</button>{Object.entries(sessionNames).map(([key, label]) => <button key={key} className="chip" aria-pressed={type === key} onClick={() => setType(key)}>{label}</button>)}</div>
            <p className="group-label" id="pro-label">Profissional</p>
            <div className="chips" role="group" aria-labelledby="pro-label"><button className="chip" aria-pressed={professional === ''} onClick={() => setProfessional('')}>Todos</button>{professionals.map(p => <button key={p.id} className="chip" aria-pressed={professional === p.id} onClick={() => setProfessional(p.id)}><span className="avatar" aria-hidden="true">{initials(p.nome)}</span>{p.nome}</button>)}</div>
            <div className="week-head">
              <h2>{dateLabel(days[0], { day: 'numeric', month: 'short' })} a {dateLabel(days[6], { day: 'numeric', month: 'short' })}</h2>
              <div><button className="icon-button" aria-label="Semana anterior" disabled={week <= today} onClick={() => setWeek(current => addDays(current, -7) < today ? today : addDays(current, -7))}><ChevronLeft size={22} /></button><button className="icon-button" aria-label="Próxima semana" onClick={() => setWeek(current => addDays(current, 7))}><ChevronRight size={22} /></button></div>
            </div>
            <div className="days" role="group" aria-label="Dias da semana">{days.map(day => <button key={day} className={`day ${day === date ? 'selected' : ''}`} aria-pressed={day === date} aria-label={dateLabel(day, { weekday: 'long', day: 'numeric', month: 'long' })} onClick={() => setDate(day)}><span className="day-name">{dateLabel(day, { weekday: 'short' }).replace('.', '')}</span><strong>{day.slice(8)}</strong>{day === today ? <i className="day-today" title="Hoje" /> : <i />}</button>)}</div>
            <label className="date-input">Outra data<input type="date" min={today} value={date} onChange={event => { if (event.target.value) { setDate(event.target.value); setWeek(event.target.value); } }} /></label>
            <div className="slots-head"><h2>{dateLabel(date, { weekday: 'long', day: 'numeric', month: 'long' })}</h2>{!loading && !error && <p>{slots.length === 1 ? '1 horário livre' : `${slots.length} horários livres`}</p>}</div>
            {loading ? <div role="status"><span className="sr-only">Buscando horários…</span><div className="slots">{[0, 1, 2, 3, 4, 5].map(i => <div className="skeleton" key={i} />)}</div></div>
              : error ? null
              : slots.length === 0 ? <div className="empty"><CalendarX size={36} /><h3>Nenhum horário livre neste dia.</h3><p>Escolha outro dia{filtered ? ' ou remova os filtros' : ''}.</p>{filtered && <button className="button ghost" onClick={() => { setType(''); setProfessional(''); }}>Remover filtros</button>}</div>
              : <div className="slots" key={`${date}-${type}-${professional}`}>{slots.map((slot, i) => <button key={slot.id} className="slot" style={{ '--i': i }} aria-pressed={selected?.id === slot.id} onClick={() => setSelected(slot)} disabled={busy}><strong>{hour(slot.horaInicio)}</strong><small>{sessionNames[slot.tipoSessao]}</small><span className="who">{slot.profissional.nome}</span></button>)}</div>}
            <p className="timezone">Horários no fuso da clínica.</p>
          </section>
          {selected
            ? <aside className="panel summary" aria-label="Resumo do agendamento"><form ref={confirmRef} onSubmit={book} key={selected.id}>
                <h2>Falta só confirmar</h2><p className="sub">Confira os detalhes da sessão.</p>
                <dl className="details"><div><dt>Sessão</dt><dd>{sessionNames[selected.tipoSessao]}</dd></div><div><dt>Profissional</dt><dd>{selected.profissional.nome}</dd></div><div><dt>Data e horário</dt><dd>{dateLabel(selected.data)}, {hour(selected.horaInicio)}</dd></div></dl>
                {!rescheduling && <label><span>Observações <span className="optional">(opcional)</span></span><textarea name="observacoes" rows={3} maxLength={1000} placeholder="Algo que a gente deva saber?" /></label>}
                <button className="button primary full" disabled={busy}>{confirmLabel}</button>
                <button type="button" className="text-button centered" style={{ color: '#fff' }} onClick={() => setSelected(null)}>Escolher outro horário</button>
              </form></aside>
            : <aside className="panel summary summary-idle"><h2>Como funciona</h2><p className="sub">Três passos, sem burocracia.</p><ol><li>Escolha o dia e o horário.</li><li>Entre ou crie sua conta.</li><li>Confirme e pronto.</li></ol></aside>}
        </div>
      </> : <section className="appointments" aria-label="Meus agendamentos">
        <h1>Meus agendamentos</h1>
        {loading ? <div className="empty" role="status"><div className="skeleton" style={{ width: 280 }} />Carregando suas sessões…</div>
          : !error && appointments.itens.length === 0 ? <div className="empty panel"><CalendarX size={36} /><h3>Você não tem sessões agendadas.</h3><button className="button primary" onClick={() => setTab('agendar')}>Encontrar um horário</button></div>
          : !error && <>
            <ul className="appointment-list">{appointments.itens.map((item, i) => <li className="appointment" style={{ '--i': i }} key={item.id}>
              <div className="appt-date"><strong>{item.horario.data.slice(8)}</strong><span>{dateLabel(item.horario.data, { month: 'short' }).replace('.', '')}</span></div>
              <div className="appt-info"><p className="appt-title">{sessionNames[item.horario.tipoSessao]}<span className={`badge ${item.status === 'CANCELADO' ? 'cancelled' : ''}`}>{item.status === 'CONFIRMADO' ? 'Confirmado' : 'Cancelado'}</span></p><p>{item.horario.profissional.nome}, {hour(item.horario.horaInicio)} às {hour(item.horario.horaFim)}, {item.horario.data.slice(0, 4)}</p>{item.observacoes && <p>{item.observacoes}</p>}</div>
              {item.status === 'CONFIRMADO' && item.horario.data >= today && <div className="appointment-actions"><button className="button ghost" onClick={() => startReschedule(item)}>Remarcar</button><button className="text-button danger" onClick={() => setCancelTarget(item)}>Cancelar</button></div>}
            </li>)}</ul>
            {appointments.totalPaginas > 1 && <div className="pagination"><button className="button ghost" disabled={page === 0} onClick={() => setPage(p => p - 1)}>Anterior</button><span>Página {page + 1} de {appointments.totalPaginas}</span><button className="button ghost" disabled={page + 1 >= appointments.totalPaginas} onClick={() => setPage(p => p + 1)}>Próxima</button></div>}
          </>}
      </section>}
    </main>
    <footer className="shell footer">Fisio Clínica. Cada sessão no horário que cabe na sua rotina.</footer>
    {auth && <AuthDialog initialMode={auth.mode} resetToken={auth.token} onClose={() => setAuth(null)} onSuccess={login} />}
    {cancelTarget && <dialog ref={cancelRef} className="auth-dialog" aria-labelledby="cancel-title" onCancel={event => { event.preventDefault(); if (!busy) setCancelTarget(null); }}><h2 id="cancel-title">Cancelar esta sessão?</h2><p className="muted">{sessionNames[cancelTarget.horario.tipoSessao]} em {dateLabel(cancelTarget.horario.data)}, às {hour(cancelTarget.horario.horaInicio)}. O horário será liberado para outra pessoa.</p><div className="dialog-actions"><button className="button ghost" disabled={busy} onClick={() => setCancelTarget(null)}>Manter sessão</button><button className="button destructive" disabled={busy} onClick={cancel}>{busy ? 'Cancelando…' : 'Sim, cancelar'}</button></div></dialog>}
  </>;
}
