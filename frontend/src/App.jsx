import { useEffect, useRef, useState } from 'react';
import { ArrowRight, CalendarDays, Check, ChevronLeft, ChevronRight, Clock3, HeartPulse, Leaf, LogOut, MapPin, ShieldCheck, Sparkles, UserRound, X } from 'lucide-react';
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
  const days = Array.from({ length: 7 }, (_, index) => addDays(week, index));
  return <>
    <div className="topline"><Leaf size={13} /> Cuidar de você é o que nos move.</div>
    <header className="header shell">
      <a className="brand" href="#" aria-label="Fisio Clínica, início" onClick={event => { event.preventDefault(); setTab('agendar'); }}><span className="brand-icon"><HeartPulse size={29} strokeWidth={1.6} /></span><span>fisio<span className="brand-light">clínica</span><small>MOVIMENTO & BEM-ESTAR</small></span></a>
      <nav aria-label="Navegação principal"><button className={tab === 'agendar' ? 'nav-active' : ''} onClick={() => setTab('agendar')}>Agendar sessão</button><button className={tab === 'meus' ? 'nav-active' : ''} onClick={showMine}>Meus agendamentos</button></nav>
      {session ? <div className="account"><span><UserRound size={16} /> {session.usuario.nome.split(' ')[0]}</span><button className="icon-button" title="Sair" aria-label="Sair da conta" onClick={logout}><LogOut size={18} /></button></div> : <button className="button outline" onClick={() => setAuth({ mode: 'login' })}><UserRound size={16} /> Entrar <ArrowRight size={15} /></button>}
    </header>
    <main className="shell">
      <section className="hero">
        <div className="hero-copy"><p className="eyebrow"><span className="little-line" /> UM TEMPO PARA VOCÊ</p><h1>Seu movimento.<br /><em>Nosso cuidado.</em></h1><p>Mais leveza na sua rotina, mais liberdade para viver.<br className="desktop-break" /> Agende sua sessão e dê o próximo passo no seu bem-estar.</p><div className="hero-foot"><ShieldCheck size={18} /><span>Cuidado individualizado</span><span className="dot">·</span><span>Agendamento simples</span></div></div>
        <div className="hero-art" aria-hidden="true"><div className="orbit orbit-one" /><div className="orbit orbit-two" /><div className="art-pill pill-one" /><div className="art-pill pill-two" /><div className="art-circle" /><div className="art-stem" /><div className="art-leaf leaf-one" /><div className="art-leaf leaf-two" /><div className="art-caption"><Sparkles size={18} /><span>Pequenos passos.<br /><strong>Novas possibilidades.</strong></span></div><span className="art-note">ENCONTRE SEU EQUILÍBRIO</span></div>
      </section>
      {notice && <div className="notice" role="status"><Check size={18} /><span>{notice}</span><button className="icon-button" aria-label="Dispensar mensagem" onClick={() => setNotice('')}><X size={17} /></button></div>}
      <div className="section-heading"><div><p className="eyebrow">{tab === 'agendar' ? 'SUA PRÓXIMA SESSÃO' : 'SEU ESPAÇO DE CUIDADO'}</p><h2>{tab === 'agendar' ? rescheduling ? 'Vamos encontrar um novo horário?' : 'Reserve um momento para se cuidar.' : 'Meus agendamentos'}</h2></div><span className="step-label"><span>01</span> Escolha <i /> <span>02</span> Confirme <i /> <span>03</span> Cuide-se</span></div>
      <div className="content-tabs"><button className={tab === 'agendar' ? 'active' : ''} onClick={() => setTab('agendar')}><CalendarDays size={17} /> Agendar sessão</button><button className={tab === 'meus' ? 'active' : ''} onClick={showMine}><UserRound size={17} /> Meus agendamentos</button></div>
      {error && <div className="message error" role="alert">{error}<button className="text-button" onClick={() => setRefresh(n => n + 1)}>Tentar novamente</button></div>}
      {tab === 'agendar' ? <div className="booking-layout">
        <section className="schedule-panel" aria-label="Horários disponíveis">
          {rescheduling && <div className="reschedule-banner"><span>Remarcando: {dateLabel(rescheduling.horario.data)} às {hour(rescheduling.horario.horaInicio)}</span><button className="text-button" onClick={() => { setRescheduling(null); setSelected(null); }}>Desistir</button></div>}
          <div className="filters"><label>Tipo de sessão<select value={type} onChange={event => setType(event.target.value)}><option value="">Todas as sessões</option>{Object.entries(sessionNames).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label><label>Profissional<select value={professional} onChange={event => setProfessional(event.target.value)}><option value="">Todos os profissionais</option>{professionals.map(p => <option key={p.id} value={p.id}>{p.nome}</option>)}</select></label></div>
          <div className="calendar-heading"><h3><CalendarDays size={18} /> Escolha o dia</h3><label className="date-picker"><span className="sr-only">Escolher data</span><input aria-label="Escolher data" type="date" min={clinicToday()} value={date} onChange={event => { if (event.target.value) { setDate(event.target.value); setWeek(event.target.value); } }} /></label></div>
          <div className="week"><button className="icon-button week-arrow" aria-label="Semana anterior" disabled={week <= clinicToday()} onClick={() => setWeek(current => addDays(current, -7) < clinicToday() ? clinicToday() : addDays(current, -7))}><ChevronLeft size={20} /></button>{days.map(day => <button key={day} className={`day ${day === date ? 'selected' : ''}`} aria-pressed={day === date} aria-label={dateLabel(day, { weekday: 'long', day: 'numeric', month: 'long' })} onClick={() => setDate(day)}><span>{dateLabel(day, { weekday: 'short' }).replace('.', '')}</span><strong>{day.slice(8)}</strong><i /></button>)}<button className="icon-button week-arrow" aria-label="Próxima semana" onClick={() => setWeek(current => addDays(current, 7))}><ChevronRight size={20} /></button></div>
          <div className="slots-heading"><h3>Horários disponíveis <span>· {dateLabel(date)}</span></h3><span className="availability"><i /> Disponível</span></div>
          {loading ? <div className="empty" role="status"><span className="spinner" />Buscando horários para você…</div> : error ? <div className="empty"><CalendarDays size={30} /><h3>Não conseguimos carregar a agenda.</h3><p>Tente novamente em alguns instantes.</p></div> : slots.length === 0 ? <div className="empty"><CalendarDays size={30} /><h3>Um novo dia, novas possibilidades.</h3><p>Não há horários para esta seleção.<br />Experimente outra data ou ajuste os filtros.</p></div> : <div className="slots">{slots.map(slot => <button key={slot.id} className={`slot ${selected?.id === slot.id ? 'chosen' : ''}`} aria-pressed={selected?.id === slot.id} onClick={() => setSelected(slot)} disabled={busy}><div className="slot-time"><Clock3 size={16} /><strong>{hour(slot.horaInicio)}</strong><span>{hour(slot.horaFim)}</span>{selected?.id === slot.id && <Check size={16} />}</div><span className="slot-type">{sessionNames[slot.tipoSessao]}</span><div className="slot-person"><span className="avatar">{initials(slot.profissional.nome)}</span><span>{slot.profissional.nome}</span></div></button>)}</div>}
          <p className="timezone"><Clock3 size={13} /> Os horários seguem o fuso da clínica.</p>
        </section>
        <aside className="summary"><div className="summary-icon"><Leaf size={25} strokeWidth={1.5} /></div><p className="eyebrow">SEU MOMENTO DE CUIDADO</p><h3>{selected ? 'Falta só confirmar.' : 'A gente cuida de cada passo.'}</h3><p className="summary-intro">{selected ? 'Confira os detalhes da sua próxima sessão.' : 'Escolha o dia e o horário que combinam com a sua rotina. O resto é com a gente.'}</p>
          {selected ? <form onSubmit={book}><dl className="summary-details"><div><dt>Sessão</dt><dd>{sessionNames[selected.tipoSessao]}</dd></div><div><dt>Profissional</dt><dd>{selected.profissional.nome}</dd></div><div><dt>Data e horário</dt><dd>{dateLabel(selected.data)} · {hour(selected.horaInicio)}</dd></div></dl>{!rescheduling && <label>Observações <span className="optional">(opcional)</span><textarea name="observacoes" rows={3} maxLength={1000} placeholder="Algo que gostaria que soubéssemos?" /></label>}<button className="button primary full" disabled={busy}>{busy ? 'Confirmando…' : !session ? 'Entrar para confirmar' : rescheduling ? 'Confirmar remarcação' : 'Confirmar agendamento'}<ArrowRight size={17} /></button></form> : <ol className="care-steps"><li><span>1</span><div><strong>Encontre seu horário</strong><p>Escolha a sessão e o profissional.</p></div></li><li><span>2</span><div><strong>Confirme com tranquilidade</strong><p>Entre ou crie sua conta em instantes.</p></div></li><li><span>3</span><div><strong>Venha cuidar de você</strong><p>Estaremos esperando por você.</p></div></li></ol>}
          <div className="summary-note"><ShieldCheck size={18} /><span>Seus agendamentos ficam disponíveis na sua conta.</span></div>
        </aside>
      </div> : <section className="appointments-panel" aria-label="Meus agendamentos">
        {loading ? <div className="empty" role="status"><span className="spinner" />Carregando suas sessões…</div> : !error && appointments.itens.length === 0 ? <div className="empty"><CalendarDays size={32} /><h3>Seu primeiro passo começa aqui.</h3><p>Você ainda não tem sessões agendadas.</p><button className="button primary" onClick={() => setTab('agendar')}>Encontrar um horário <ArrowRight size={16} /></button></div> : !error && <><div className="appointment-list">{appointments.itens.map(item => <article className="appointment" key={item.id}><div className="appointment-date"><strong>{item.horario.data.slice(8)}</strong><span>{dateLabel(item.horario.data, { month: 'short' })}</span><small>{item.horario.data.slice(0, 4)}</small></div><div className="appointment-info"><span className={`badge ${item.status === 'CANCELADO' ? 'cancelled' : ''}`}>{item.status === 'CONFIRMADO' ? 'Confirmado' : 'Cancelado'}</span><h3>{sessionNames[item.horario.tipoSessao]}</h3><p>{item.horario.profissional.nome} <span>·</span> {hour(item.horario.horaInicio)} – {hour(item.horario.horaFim)}</p>{item.observacoes && <p className="observation">{item.observacoes}</p>}</div>{item.status === 'CONFIRMADO' && item.horario.data >= clinicToday() && <div className="appointment-actions"><button className="button outline" onClick={() => startReschedule(item)}>Remarcar</button><button className="text-button danger" onClick={() => setCancelTarget(item)}>Cancelar</button></div>}</article>)}</div>{appointments.totalPaginas > 1 && <div className="pagination"><button className="button outline" disabled={page === 0} onClick={() => setPage(p => p - 1)}>Anterior</button><span>Página {page + 1} de {appointments.totalPaginas}</span><button className="button outline" disabled={page + 1 >= appointments.totalPaginas} onClick={() => setPage(p => p + 1)}>Próxima</button></div>}</>}
      </section>}
      <section className="values"><div><HeartPulse size={24} /><span><strong>Cuidado que acolhe</strong><small>Cada pessoa, uma história. Cada sessão, um cuidado.</small></span></div><div><CalendarDays size={24} /><span><strong>Sua rotina em equilíbrio</strong><small>Agende e acompanhe tudo em um só lugar.</small></span></div><div><Leaf size={24} /><span><strong>Movimento com propósito</strong><small>Mais autonomia e bem-estar no seu dia a dia.</small></span></div></section>
    </main>
    <footer className="shell footer"><span className="footer-brand"><HeartPulse size={20} /> fisio clínica</span><span>Seu movimento merece cuidado.</span><span><MapPin size={14} /> Horário local da clínica</span></footer>
    {auth && <AuthDialog initialMode={auth.mode} resetToken={auth.token} onClose={() => setAuth(null)} onSuccess={login} />}
    {cancelTarget && <dialog ref={cancelRef} className="auth-dialog" aria-labelledby="cancel-title" onCancel={event => { event.preventDefault(); if (!busy) setCancelTarget(null); }}><h2 id="cancel-title">Cancelar esta sessão?</h2><p className="muted">{sessionNames[cancelTarget.horario.tipoSessao]} em {dateLabel(cancelTarget.horario.data)}, às {hour(cancelTarget.horario.horaInicio)}. O horário será liberado para outra pessoa.</p><div className="dialog-actions"><button className="button outline" disabled={busy} onClick={() => setCancelTarget(null)}>Manter sessão</button><button className="button destructive" disabled={busy} onClick={cancel}>{busy ? 'Cancelando…' : 'Sim, cancelar'}</button></div></dialog>}
  </>;
}
