import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, language, switchLanguage } from '../core/i18n/i18n';
import { RequestState, errorMessage } from '../core/feedback/request-state';
import { NavigationService, Page } from '../core/navigation/navigation.service';
import { SessionStore } from '../features/account/session.store';
import { AuthComponent } from '../features/account/auth.component';
import { ProfileComponent } from '../features/account/profile.component';
import { StrengthStore } from '../features/strength/strength.store';
import { StrengthOverviewComponent } from '../features/strength/overview.component';
import { StrengthRanksComponent } from '../features/strength/ranks.component';
import { StrengthHistoryComponent } from '../features/strength/history.component';
import { StrengthDialogsComponent } from '../features/strength/strength-dialogs.component';
import { CardioComponent } from '../features/cardio/cardio.component';
import { WodComponent } from '../features/wod/wod.component';
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TranslatePipe,
    AuthComponent,
    ProfileComponent,
    StrengthOverviewComponent,
    StrengthRanksComponent,
    StrengthHistoryComponent,
    StrengthDialogsComponent,
    CardioComponent,
    WodComponent,
  ],
  templateUrl: './app.component.html',
})
export class AppComponent implements OnInit, OnDestroy {
  private readonly session = inject(SessionStore);
  private readonly feedback = inject(RequestState);
  private readonly navigation = inject(NavigationService);
  readonly strength = inject(StrengthStore);
  readonly user = this.session.user;
  readonly page = this.navigation.page;
  readonly ready = signal(false);
  readonly error = this.feedback.error;
  readonly notice = this.feedback.notice;
  readonly language = language;
  readonly switchLanguage = switchLanguage;
  private unsubscribe = () => {};
  async ngOnInit() {
    this.navigation.go(this.page(), true);
    this.unsubscribe = this.navigation.onPopState(() => {
      void this.navigate(this.navigation.readPage(), false);
    });
    try {
      await this.session.restore();
      await this.authenticated();
    } catch (error) {
      if (this.user()) this.error.set(errorMessage(error));
    } finally {
      this.ready.set(true);
    }
  }
  ngOnDestroy() {
    this.unsubscribe();
  }
  async authenticated() {
    try {
      await this.strength.refresh();
      await this.navigate(this.page(), false);
    } catch (error) {
      this.error.set(errorMessage(error));
    }
  }
  async navigate(page: Page, updateUrl = true) {
    if (page === 'ranks' && !this.user()?.ranks_enabled) {
      page = 'overview';
      if (!updateUrl) this.navigation.go(page, true);
    }
    this.strength.closeProgress();
    this.strength.showLog.set(false);
    this.feedback.clear();
    if (updateUrl) this.navigation.go(page);
    else this.page.set(page);
    if (page === 'ranks') await this.strength.loadTargets();
  }
  async logout() {
    await this.feedback.run(async () => {
      await this.session.logout();
      this.strength.clear();
      this.feedback.clear();
      this.navigation.go('overview');
    });
  }
}
