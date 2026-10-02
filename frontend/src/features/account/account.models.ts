export interface User {
  id: string;
  name: string;
  email: string;
  bodyweight: number;
  standard: string;
  ranks_enabled: boolean;
}

export interface LoginInput {
  email: string;
  password: string;
}
export interface RegistrationInput extends LoginInput {
  name: string;
  bodyweight: number;
  standard: string;
}
export interface ProfileInput {
  name: string;
  bodyweight: number;
  standard: string;
  ranksEnabled: boolean;
}
