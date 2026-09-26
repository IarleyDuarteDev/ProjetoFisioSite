import { expect, it, vi, afterEach } from 'vitest';
import { addDays, clinicToday, dateLabel } from './format';

afterEach(() => vi.useRealTimers());
it('usa o dia da clínica mesmo quando já virou o dia em UTC', () => {
  vi.useFakeTimers(); vi.setSystemTime(new Date('2026-09-26T02:00:00Z'));
  expect(clinicToday()).toBe('2026-09-25');
});
it('navega entre meses sem alterar o dia por conversão de fuso', () => {
  expect(addDays('2026-09-30', 1)).toBe('2026-10-01');
  expect(dateLabel('2026-10-01')).toBe('1 de outubro');
});
