import { Injectable, inject } from '@angular/core';
import { ApiClient } from '../../core/http/api-client';
import { CardioSession, Benchmark, CardioInput } from './cardio.models';
@Injectable({ providedIn: 'root' })
export class CardioApi {
  private readonly http = inject(ApiClient);
  benchmarks() {
    return this.http.request<Benchmark[]>('/api/cardio/benchmarks');
  }
  history() {
    return this.http.request<CardioSession[]>('/api/cardio');
  }
  save(input: CardioInput) {
    return this.http.request<CardioSession>('/api/cardio', 'POST', input);
  }
  remove(id: string) {
    return this.http.request<void>('/api/cardio/' + encodeURIComponent(id), 'DELETE');
  }
}
