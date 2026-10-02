import { Component, EventEmitter, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, language, switchLanguage } from '../../core/i18n/i18n';
import { RequestState } from '../../core/feedback/request-state';
import { SessionStore } from './session.store';
@Component({
  selector: 'app-auth',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './auth.component.html',
  host: { style: 'display: contents' },
})
export class AuthComponent {
  private readonly session = inject(SessionStore);
  private readonly feedback = inject(RequestState);
  @Output() authenticated = new EventEmitter<void>();
  readonly busy = this.feedback.busy;
  readonly error = this.feedback.error;
  readonly language = language;
  readonly switchLanguage = switchLanguage;
  authMode = 'login';
  name = '';
  email = '';
  password = '';
  bodyweight = 75;
  standard = 'male';
  async authenticate() {
    await this.feedback.run(async () => {
      const login = { email: this.email, password: this.password };
      if (this.authMode === 'register')
        await this.session.register({
          ...login,
          name: this.name,
          bodyweight: this.bodyweight,
          standard: this.standard,
        });
      else await this.session.login(login);
      this.password = '';
      this.authenticated.emit();
    });
  }
}
