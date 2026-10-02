/** Training dates are calendar days, not UTC instants. Never use toISOString() here. */
export function localDate(date = new Date()): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}
export function historyCutoff(days: number, now = new Date()): string {
  const cutoff = new Date(now);
  cutoff.setDate(cutoff.getDate() - days + 1);
  return localDate(cutoff);
}
