export interface Exercise {
  id: string;
  name: string;
  group: string;
  note: string;
  bodyweightMovement: boolean;
  source: string;
}
export interface Standing {
  exercise: Exercise;
  level: number;
  rank: string;
  score: number;
  progress: number;
  nextRank: string;
}
export interface Target {
  level: number;
  rank: string;
  ratio: number;
  weight: number;
  reps: number;
}
export interface Lift {
  id: string;
  exercise: string;
  weight: number;
  reps: number;
  bodyweight: number;
  standard: string;
  score: number;
  performed_on: string;
}
export interface LiftInput {
  exercise: string;
  weight: number;
  reps: number;
  performedOn: string;
}
export interface LoggedLift {
  id: string;
  rank: string;
}
