export interface CardioSession {
  id: string;
  activity: string;
  distance_meters: number | null;
  reps: number | null;
  duration_seconds: number;
  performed_on: string;
  standard: string;
  notes: string;
  rank: string | null;
  rate: number;
  source: string | null;
  reference: string | null;
}
export interface Benchmark {
  activity: string;
  distanceMeters: number;
  source: string;
  reference: string;
  labels: string[];
  male: number[];
  female: number[];
}
export interface Activity {
  id: string;
  name: string;
  activity: string;
  distance: number | null;
}
export interface CardioInput {
  activity: string;
  distanceMeters: number | null;
  reps: number | null;
  durationSeconds: number;
  performedOn: string;
  notes: string;
}
