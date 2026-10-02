import { CardioSession, Activity, Benchmark } from './cardio.models';
import { historyCutoff } from '../../shared/dates/local-date';
export const CARDIO_ACTIVITIES: Activity[] = [
  { id: 'rope', name: 'Jump rope', activity: 'rope', distance: null },
  ...[400, 1000, 5000, 10000, 20000].map((distance) => ({
    id: 'run-' + distance,
    name: 'Running',
    activity: 'running',
    distance,
  })),
  { id: 'cycling', name: 'Cycling', activity: 'cycling', distance: null },
];
export function sessionsForActivity(sessions: CardioSession[], activity: Activity) {
  return sessions.filter(
    (session) =>
      session.activity === activity.activity &&
      (!activity.distance || session.distance_meters === activity.distance),
  );
}
export function filterCardioHistory(
  sessions: CardioSession[],
  activity: Activity | null,
  days: number,
  comparison: number | null,
  now = new Date(),
) {
  if (!activity) return [];
  const cutoff = historyCutoff(days, now);
  return sessionsForActivity(sessions, activity).filter(
    (session) =>
      (!days || session.performed_on >= cutoff) &&
      (comparison === null ||
        (activity.activity === 'rope'
          ? session.duration_seconds === comparison
          : session.distance_meters === Math.round(comparison * 1000))),
  );
}
/** Each point is one actual session; never combine another session's distance and time. */
export function projectCardioPoints(sessions: CardioSession[]) {
  const groups = new Map<string, CardioSession>();
  for (const session of sessions) {
    const old = groups.get(session.performed_on);
    if (!old || session.rate > old.rate) groups.set(session.performed_on, session);
  }
  const rows = [...groups.values()].sort((left, right) =>
    left.performed_on.localeCompare(right.performed_on),
  );
  if (!rows.length) return [];
  const start = Date.parse(rows[0].performed_on),
    span = Date.parse(rows.at(-1)!.performed_on) - start,
    max = Math.max(1, ...rows.map((session) => session.rate));
  return rows.map((session) => ({
    s: session,
    x: span ? 60 + (670 * (Date.parse(session.performed_on) - start)) / span : 395,
    y: 230 - (190 * session.rate) / max,
  }));
}
export function bestCardioRank(
  sessions: CardioSession[],
  benchmarks: Benchmark[],
  standard: string,
) {
  const rankLevel = (session: CardioSession) =>
    benchmarks
      .find(
        (benchmark) =>
          benchmark.activity === session.activity &&
          benchmark.distanceMeters === session.distance_meters,
      )
      ?.labels.indexOf(session.rank || '') ?? -1;
  return sessions
    .filter((session) => session.standard === standard && session.rank)
    .sort((left, right) => rankLevel(right) - rankLevel(left))[0]?.rank;
}
export function formatDuration(value: number) {
  const hours = Math.floor(value / 3600),
    minutes = Math.floor((value % 3600) / 60),
    seconds = (value % 60).toFixed(2).replace(/\.00$/, '');
  return (
    (hours ? hours + ':' + String(minutes).padStart(2, '0') : String(minutes)) +
    ':' +
    seconds.padStart(seconds.includes('.') ? 5 : 2, '0')
  );
}
