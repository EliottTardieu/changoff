import { Injectable, inject } from '@angular/core';
import { ApiClient } from '../../core/http/api-client';
import {
  Catalog,
  Preferences,
  Draft,
  Summary,
  Detail,
  Statistics,
  GenerateRequest,
  SaveRequest,
  WodResult,
} from './wod.models';
@Injectable({ providedIn: 'root' })
export class WodApi {
  private readonly http = inject(ApiClient);
  catalog() {
    return this.http.request<Catalog>('/api/wods/catalog');
  }
  preferences() {
    return this.http.request<Preferences>('/api/wods/preferences');
  }
  savePreferences(input: Preferences) {
    return this.http.request<Preferences>('/api/wods/preferences', 'PUT', input);
  }
  generate(input: GenerateRequest) {
    return this.http.request<Draft>('/api/wods/generate', 'POST', input);
  }
  history() {
    return this.http.request<Summary[]>('/api/wods');
  }
  statistics() {
    return this.http.request<Statistics>('/api/wods/statistics');
  }
  save(input: SaveRequest, id?: string) {
    return this.http.request<Detail>(id ? this.path(id) : '/api/wods', id ? 'PUT' : 'POST', input);
  }
  detail(id: string) {
    return this.http.request<Detail>(this.path(id));
  }
  result(id: string, input: WodResult) {
    return this.http.request<Detail>(this.path(id) + '/result', 'PUT', input);
  }
  share(id: string, email: string) {
    return this.http.request<Detail>(this.path(id) + '/participants', 'POST', { email });
  }
  leave(id: string) {
    return this.http.request<void>(this.path(id) + '/participation', 'DELETE');
  }
  private path(id: string) {
    return '/api/wods/' + encodeURIComponent(id);
  }
}
