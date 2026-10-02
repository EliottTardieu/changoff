import { Injectable, inject } from '@angular/core';
import { ApiClient } from '../../core/http/api-client';
import { User, LoginInput, RegistrationInput, ProfileInput } from './account.models';
@Injectable({ providedIn: 'root' })
export class AccountApi {
  private readonly http = inject(ApiClient);
  me() {
    return this.http.request<User>('/api/me');
  }
  login(input: LoginInput) {
    return this.http.request<User>('/api/auth/login', 'POST', input);
  }
  register(input: RegistrationInput) {
    return this.http.request<User>('/api/auth/register', 'POST', input);
  }
  logout() {
    return this.http.request<void>('/api/auth/logout', 'POST');
  }
  update(input: ProfileInput) {
    return this.http.request<User>('/api/me', 'PUT', input);
  }
}
