import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '../../core/i18n/i18n';
import { RequestState } from '../../core/feedback/request-state';
import { NavigationService } from '../../core/navigation/navigation.service';
import { SessionStore } from './session.store';
import { StrengthStore } from '../strength/strength.store';
@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './profile.component.html',
  host: { style: 'display: contents' },
})
export class ProfileComponent implements OnInit {
  private readonly session = inject(SessionStore);
  private readonly strength = inject(StrengthStore);
  private readonly feedback = inject(RequestState);
  private readonly navigation = inject(NavigationService);
  readonly user = this.session.user;
  readonly busy = this.feedback.busy;
  profileName = '';
  profileWeight = 75;
  profileStandard = 'male';
  profileRanks = true;
  ngOnInit() {
    const user = this.user()!;
    this.profileName = user.name;
    this.profileWeight = user.bodyweight;
    this.profileStandard = user.standard;
    this.profileRanks = user.ranks_enabled;
  }
  async saveProfile() {
    await this.feedback.run(async () => {
      await this.session.update({
        name: this.profileName,
        bodyweight: this.profileWeight,
        standard: this.profileStandard,
        ranksEnabled: this.profileRanks,
      });
      await this.strength.refresh();
      this.feedback.notice.set('Profile updated. New sets will use these settings.');
    });
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
