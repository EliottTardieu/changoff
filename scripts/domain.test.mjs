import test from 'node:test';
import assert from 'node:assert/strict';
import { loadTypeScript } from './helpers/load-typescript.mjs';
const load = (file) => loadTypeScript(new URL('../frontend/src/' + file, import.meta.url));
const { bestSet, filterLifts, dailyBestSets, projectStrengthPoints } = load(
  'features/strength/progress/progress-calculations.ts',
);
const {
  CARDIO_ACTIVITIES,
  filterCardioHistory,
  projectCardioPoints,
  bestCardioRank,
  formatDuration,
} = load('features/cardio/cardio-calculations.ts');
const { localDate, historyCutoff } = load('shared/dates/local-date.ts');
const lift = (id, weight, reps, performed_on) => ({
  id,
  exercise: 'bench-press',
  weight,
  reps,
  performed_on,
  bodyweight: 75,
  standard: 'male',
  score: 0,
});

test('daily records preserve paired weight/reps, break ties, and recalculate after deletion', () => {
  const sets = [
    lift('a', 50, 5, '2026-01-01'),
    lift('b', 50, 8, '2026-01-01'),
    lift('c', 40, 20, '2026-01-02'),
    lift('d', 50, 8, '2026-01-03'),
  ];
  assert.equal(bestSet(sets, 'weight').id, 'd');
  assert.equal(bestSet(sets, 'reps').id, 'c');
  assert.deepEqual(
    dailyBestSets(sets, 'weight').map((set) => set.id),
    ['b', 'c', 'd'],
  );
  assert.equal(
    bestSet(
      sets.filter((set) => set.id !== 'c'),
      'reps',
    ).reps,
    8,
  );
  assert.equal(bestSet([], 'weight'), undefined);
});

test('strength history compares the other metric and includes the cutoff day', () => {
  const sets = [
    lift('old', 50, 5, '2026-01-01'),
    lift('start', 50, 5, '2026-01-02'),
    lift('other', 40, 8, '2026-01-31'),
  ];
  const now = new Date(2026, 0, 31, 12);
  assert.deepEqual(
    filterLifts(sets, 'weight', 30, 5, now).map((set) => set.id),
    ['start'],
  );
  assert.deepEqual(
    filterLifts(sets, 'reps', 0, 40, now).map((set) => set.id),
    ['other'],
  );
  assert.deepEqual(filterLifts(sets, 'weight', 0, 999, now), []);
});

test('single-day zero-load graphs stay finite and missing dates are not inserted', () => {
  const points = projectStrengthPoints([lift('bodyweight', 0, 8, '2026-01-01')], 'weight', 1);
  assert.equal(points.length, 1);
  assert.equal(points[0].x, 395);
  assert.ok(Number.isFinite(points[0].y));
  assert.deepEqual(projectStrengthPoints([], 'weight', 1), []);
});

const session = (
  id,
  activity,
  date,
  rate,
  distance,
  duration = 60,
  rank = null,
  standard = 'male',
) => ({
  id,
  activity,
  performed_on: date,
  rate,
  distance_meters: distance,
  duration_seconds: duration,
  reps: activity === 'rope' ? rate : null,
  rank,
  standard,
  notes: '',
  source: null,
  reference: null,
});
test('cardio comparisons retain exact distances/durations and one actual fastest session per day', () => {
  const rope = CARDIO_ACTIVITIES.find((activity) => activity.id === 'rope');
  const cycling = CARDIO_ACTIVITIES.find((activity) => activity.id === 'cycling');
  const sessions = [
    session('a', 'rope', '2026-01-01', 100, null),
    session('b', 'rope', '2026-01-01', 120, null),
    session('c', 'rope', '2026-01-02', 140, null, 120),
    session('d', 'cycling', '2026-01-02', 30, 21000),
  ];
  assert.deepEqual(
    filterCardioHistory(sessions, rope, 0, 60).map((row) => row.id),
    ['a', 'b'],
  );
  assert.deepEqual(
    projectCardioPoints(filterCardioHistory(sessions, rope, 0, null)).map((point) => point.s.id),
    ['b', 'c'],
  );
  assert.equal(filterCardioHistory(sessions, cycling, 0, 20).length, 0);
  assert.equal(filterCardioHistory(sessions, cycling, 0, 21)[0].id, 'd');
  assert.deepEqual(projectCardioPoints([]), []);
});

test('cardio ranks use the selected dataset and do not infer ranks for unsupported sessions', () => {
  const benchmarks = [
    { activity: 'running', distanceMeters: 5000, labels: ['Beginner', 'Intermediate'] },
  ];
  const sessions = [
    session('a', 'running', '2026-01-01', 10, 5000, 1800, 'Beginner'),
    session('b', 'running', '2026-01-01', 15, 5000, 1200, 'Intermediate', 'female'),
    session('c', 'cycling', '2026-01-01', 50, 21000),
  ];
  assert.equal(bestCardioRank(sessions, benchmarks, 'male'), 'Beginner');
  assert.equal(bestCardioRank(sessions, benchmarks, 'female'), 'Intermediate');
  assert.equal(bestCardioRank([sessions[2]], benchmarks, 'male'), undefined);
  assert.equal(formatDuration(65.25), '1:05.25');
  assert.equal(formatDuration(3661), '1:01:01');
});

test('local calendar helpers handle month and year boundaries without UTC conversion', () => {
  assert.equal(localDate(new Date(2026, 0, 1, 0, 15)), '2026-01-01');
  assert.equal(historyCutoff(30, new Date(2026, 0, 15, 12)), '2025-12-17');
});
