export const clinicZone = 'America/Rio_Branco';
export const sessionNames: Record<string, string> = { AVALIACAO: 'Avaliação inicial', FISIOTERAPIA: 'Fisioterapia', PILATES: 'Pilates clínico' };
/** Cada tipo de sessão tem a cor de uma faixa elástica de fisioterapia. */
export const sessionBand: Record<string, string> = { AVALIACAO: 'aval', FISIOTERAPIA: 'fisio', PILATES: 'pil' };

export function clinicToday(): string {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: clinicZone, year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
  const get = (type: string) => parts.find(p => p.type === type)!.value;
  return `${get('year')}-${get('month')}-${get('day')}`;
}
export function addDays(iso: string, days: number): string {
  const date = new Date(`${iso}T12:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}
export function dateLabel(iso: string, options: Intl.DateTimeFormatOptions = { day: 'numeric', month: 'long' }): string {
  return new Intl.DateTimeFormat('pt-BR', { ...options, timeZone: 'UTC' }).format(new Date(`${iso}T12:00:00Z`));
}
export const hour = (value: string) => value.slice(0, 5);
export const initials = (name: string) => name.split(' ').filter(Boolean).slice(0, 2).map(n => n[0]).join('');

/** Aplica a máscara 000.000.000-00 enquanto a pessoa digita. */
export function formatCpf(value: string): string {
  const d = value.replace(/\D/g, '').slice(0, 11);
  return d.replace(/^(\d{3})(\d)/, '$1.$2').replace(/^(\d{3})\.(\d{3})(\d)/, '$1.$2.$3').replace(/\.(\d{3})(\d)/, '.$1-$2');
}
