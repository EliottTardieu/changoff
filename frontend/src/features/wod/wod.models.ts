export interface Choice {
  id: string;
  name: string;
}
export interface Movement {
  id: string;
  name: string;
  family: string;
  category: string;
  movementType: string;
  difficulty: number;
  unit: string;
  note: string;
  equipment: string[];
  patterns: string[];
  muscles: string[];
}
export interface Restriction {
  kind: string;
  target: string;
  reason: string;
  active: boolean;
}
export interface Preferences {
  level: number;
  equipment: string[];
  restrictions: Restriction[];
}
export interface Config {
  type: string;
  stimulus: string;
  level: number;
  intensity: number;
  duration: number;
  count: number;
  rounds: number;
  scheduledOn: string;
  equipment: string[];
  bannedExercises: string[];
  bannedPatterns: string[];
  bannedMuscles: string[];
  bannedEquipment: string[];
}
export interface Prescription {
  exerciseId: string;
  quantity: number;
  weight: number;
}
export interface Item {
  exercise: Movement;
  quantity: number;
  weight: number;
  alternatives: Prescription[];
}
export interface Draft {
  config: Config;
  items: Item[];
  rounds: number;
  difficulty: number;
  instructions: string;
  explanations: string[];
}
export interface Catalog {
  exercises: Movement[];
  equipment: Choice[];
  patterns: Choice[];
  muscles: Choice[];
  levels: { id: number; name: string }[];
  types: Choice[];
  stimuli: Choice[];
}
export interface Summary {
  id: string;
  title: string;
  type_id: string;
  stimulus_id: string;
  level_id: number;
  duration_minutes: number;
  rounds: number;
  assessed_difficulty: number;
  scheduled_on: string;
  status: string;
  completed_on: string | null;
  owner_id: string;
  revision: number;
}
export interface Detail extends Summary {
  config: Config;
  items: Item[];
  instructions: string;
  isOwner: boolean;
  canEdit: boolean;
  participants: { id: string; name: string }[];
  duration_seconds: number | null;
  rounds_completed: number | null;
  extra_reps: number | null;
  effort: number | null;
  notes: string;
}
export interface Statistics {
  completed: number;
  trainingDays: number;
  loggedMinutes: number;
  averageEffort: number | null;
  upcoming: number;
  weekly: { week: string; count: number }[];
  muscles: Record<string, number>;
  types: Record<string, number>;
  mostHit: string[];
  leastHit: string[];
  uniqueMovements: number;
  windowStart: string;
  windowEnd: string;
}
export interface GenerateRequest {
  config: Config;
  exercises?: Prescription[];
}
export interface SaveRequest {
  title: string;
  config: Config;
  exercises: Prescription[];
  revision: number | null;
}
export interface WodResult {
  status: string;
  scheduledOn: string;
  completedOn: string | null;
  durationSeconds: number | null;
  roundsCompleted: number | null;
  extraReps: number | null;
  effort: number | null;
  notes: string;
}
