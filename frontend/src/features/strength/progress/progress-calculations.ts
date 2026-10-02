import { Lift } from '../strength.models';
import { historyCutoff } from '../../../shared/dates/local-date';
export type ProgressMetric = 'weight' | 'reps';
export function bestSet(lifts: Lift[], metric: 'weight' | 'reps'): Lift | undefined {
  const other = metric === 'weight' ? 'reps' : 'weight';
  return [...lifts].sort(
    (a, b) =>
      b[metric] - a[metric] || b[other] - a[other] || b.performed_on.localeCompare(a.performed_on),
  )[0];
}

export function filterLifts(
  lifts: Lift[],
  metric: ProgressMetric,
  days: number,
  comparison: number | null,
  now = new Date(),
): Lift[] {
  const start = historyCutoff(days, now);
  const other = metric === 'weight' ? 'reps' : 'weight';
  return lifts.filter(
    (lift) =>
      (!days || lift.performed_on >= start) && (comparison === null || lift[other] === comparison),
  );
}
export function dailyBestSets(lifts: Lift[], metric: ProgressMetric): Lift[] {
  const groups = new Map<string, Lift[]>();
  for (const lift of lifts)
    groups.set(lift.performed_on, [...(groups.get(lift.performed_on) || []), lift]);
  return [...groups]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([, sets]) => bestSet(sets, metric)!);
}
export function projectStrengthPoints(daily: Lift[], metric: ProgressMetric, ceiling: number) {
  if (!daily.length) return [];
  const start = Date.parse(daily[0].performed_on),
    span = Date.parse(daily[daily.length - 1].performed_on) - start;
  return daily.map((lift) => ({
    lift,
    value: lift[metric],
    x: span ? 60 + (670 * (Date.parse(lift.performed_on) - start)) / span : 395,
    y: 236 - (204 * lift[metric]) / ceiling,
  }));
}
