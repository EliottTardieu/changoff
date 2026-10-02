import { Injectable, inject } from '@angular/core';
import { ApiClient } from '../../core/http/api-client';
import { Standing, Lift, Target, LiftInput, LoggedLift } from './strength.models';
@Injectable({ providedIn: 'root' })
export class StrengthApi {
  private readonly http = inject(ApiClient);
  dashboard() {
    return this.http.request<Standing[]>('/api/dashboard');
  }
  history() {
    return this.http.request<Lift[]>('/api/lifts');
  }
  targets(exercise: string, reps: number) {
    return this.http.request<Target[]>(
      `/api/exercises/${encodeURIComponent(exercise)}/targets?reps=${reps}`,
    );
  }
  log(input: LiftInput) {
    return this.http.request<LoggedLift>('/api/lifts', 'POST', input);
  }
  remove(id: string) {
    return this.http.request<void>('/api/lifts/' + encodeURIComponent(id), 'DELETE');
  }
}
