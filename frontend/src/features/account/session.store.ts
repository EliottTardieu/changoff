import { Injectable, inject, signal, DestroyRef } from '@angular/core';
import { User, LoginInput, RegistrationInput, ProfileInput } from './account.models';
import { AccountApi } from './account-api.service';
import { ApiClient, ApiError } from '../../core/http/api-client';
@Injectable({ providedIn: 'root' })
export class SessionStore {
  constructor() {
    const subscription = inject(ApiClient).unauthorized.subscribe(() => this.user.set(null));
    inject(DestroyRef).onDestroy(() => subscription.unsubscribe());
  }
  private readonly api = inject(AccountApi);
  readonly user = signal<User | null>(null);
  async restore() {
    try {
      this.user.set(await this.api.me());
    } catch (error) {
      this.handleError(error);
      throw error;
    }
  }
  async login(input: LoginInput) {
    this.user.set(await this.api.login(input));
  }
  async register(input: RegistrationInput) {
    this.user.set(await this.api.register(input));
  }
  async logout() {
    await this.api.logout();
    this.user.set(null);
  }
  async update(input: ProfileInput) {
    try {
      this.user.set(await this.api.update(input));
    } catch (error) {
      this.handleError(error);
      throw error;
    }
  }
  handleError(error: unknown) {
    if (error instanceof ApiError && error.status === 401) this.user.set(null);
  }
}
