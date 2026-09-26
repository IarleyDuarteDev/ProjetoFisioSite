export const clinicZone = import.meta.env.VITE_CLINIC_ZONE || 'America/Rio_Branco';
export const sessionNames = { AVALIACAO: 'Avaliação inicial', FISIOTERAPIA: 'Fisioterapia', PILATES: 'Pilates clínico' };
export function clinicToday() {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: clinicZone, year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
  const value = type => parts.find(p => p.type === type).value;
  return `${value('year')}-${value('month')}-${value('day')}`;
}
export function addDays(iso, days) {
  const date = new Date(`${iso}T12:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}
export function dateLabel(iso, options = { day: 'numeric', month: 'long' }) {
  return new Intl.DateTimeFormat('pt-BR', { ...options, timeZone: 'UTC' }).format(new Date(`${iso}T12:00:00Z`));
}
export const hour = value => value.slice(0, 5);
export const initials = name => name.split(' ').filter(Boolean).slice(0, 2).map(n => n[0]).join('');
