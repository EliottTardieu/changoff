import { Injectable, inject, signal, computed } from '@angular/core';
import { StrengthApi } from './strength-api.service';
import { Standing, Lift, Target } from './strength.models';
import { bestSet } from './progress/progress-calculations';
import { SessionStore } from '../account/session.store';
import { RequestState, errorMessage } from '../../core/feedback/request-state';
import { NavigationService } from '../../core/navigation/navigation.service';
import { translate } from '../../core/i18n/i18n';
import { localDate } from '../../shared/dates/local-date';
import { isBackdropClick, trapFocus } from '../../shared/dialog/modal.directive';
@Injectable({ providedIn: 'root' })
export class StrengthStore {
  private readonly api = inject(StrengthApi);
  private readonly session = inject(SessionStore);
  private readonly feedback = inject(RequestState);
  private readonly navigation = inject(NavigationService);
  readonly user = this.session.user;
  readonly page = this.navigation.page;
  readonly busy = this.feedback.busy;
  readonly error = this.feedback.error;
  readonly notice = this.feedback.notice;
  readonly localDate = localDate;
  readonly message = errorMessage;
  readonly trapFocus = trapFocus;
  today() {
    return localDate();
  }
  standings = signal<Standing[]>([]);
  lifts = signal<Lift[]>([]);
  targets = signal<Target[]>([]);
  search = signal('');
  group = signal('All');
  groups = ['All', 'Chest', 'Back', 'Shoulders', 'Arms', 'Legs'];
  tiers = ['Bronze', 'Silver', 'Gold', 'Platinum', 'Emerald', 'Diamond', 'Master', 'Chang'];
  selected = 'bench-press';
  targetReps = 5;
  showLog = signal(false);
  logExercise = 'bench-press';
  weight = 20;
  reps = 5;
  date = this.today();
  deleteId = signal('');
  progressOpen = signal(false);
  progressExercise = signal('bench-press');
  historyExercise = signal('');
  progressLifts = computed(() =>
    this.lifts().filter((lift) => lift.exercise === this.progressExercise()),
  );
  historyLifts = computed(() =>
    this.lifts().filter(
      (lift) => !this.historyExercise() || lift.exercise === this.historyExercise(),
    ),
  );
  tracked = computed(() => new Set(this.lifts().map((lift) => lift.exercise)).size);
  records = computed(() =>
    Object.fromEntries(
      this.standings().map((standing) => {
        const sets = this.lifts().filter((lift) => lift.exercise === standing.exercise.id);
        return [
          standing.exercise.id,
          { weight: bestSet(sets, 'weight'), reps: bestSet(sets, 'reps') },
        ];
      }),
    ),
  );
  filtered = computed(() =>
    this.standings().filter(
      (standing) =>
        (this.group() === 'All' || standing.exercise.group === this.group()) &&
        translate(standing.exercise.name).toLowerCase().includes(this.search().toLowerCase()),
    ),
  );
  weekly = computed(() => {
    const cutoff = new Date();
    cutoff.setDate(cutoff.getDate() - 6);
    return this.lifts().filter((lift) => lift.performed_on >= this.localDate(cutoff)).length;
  });
  async refresh() {
    const [standing, lift] = await Promise.all([this.api.dashboard(), this.api.history()]);
    this.standings.set(standing);
    this.lifts.set(lift);
  }
  async loadTargets() {
    try {
      this.targets.set(await this.api.targets(this.selected, this.targetReps));
    } catch (error) {
      this.error.set(this.message(error));
    }
  }
  current() {
    return this.standings().find((standing) => standing.exercise.id === this.selected);
  }
  exercise(id: string) {
    return this.standings().find((standing) => standing.exercise.id === id)?.exercise;
  }
  tier(level: number) {
    return level === 0 ? 'unranked' : this.tiers[Math.floor((level - 1) / 3)].toLowerCase();
  }
  async saveLift() {
    this.busy.set(true);
    this.error.set('');
    try {
      const result = await this.api.log({
        exercise: this.logExercise,
        weight: this.weight,
        reps: this.reps,
        performedOn: this.date,
      });
      await this.refresh();
      this.showLog.set(false);
      this.notice.set(
        this.user()?.ranks_enabled && this.reps <= 12
          ? translate('Set logged. This performance earns {0}.', translate(result.rank))
          : 'Set logged. Your progress has been updated.',
      );
      if (this.page() === 'ranks') await this.loadTargets();
    } catch (error) {
      this.error.set(this.message(error));
    } finally {
      this.busy.set(false);
    }
  }
  async removeLift(id: string) {
    this.busy.set(true);
    try {
      await this.api.remove(id);
      await this.refresh();
      this.deleteId.set('');
      this.notice.set('Set deleted. Your progress has been updated.');
    } catch (error) {
      this.error.set(this.message(error));
    } finally {
      this.busy.set(false);
    }
  }
  showProgress(id: string) {
    this.progressExercise.set(id);
    this.progressOpen.set(true);
  }
  closeProgress() {
    this.progressOpen.set(false);
  }
  dismissProgressBackdrop(event: MouseEvent) {
    if (isBackdropClick(event)) this.closeProgress();
  }
  async viewRanks(id: string) {
    this.closeProgress();
    this.selected = id;
    this.feedback.clear();
    this.navigation.go('ranks');
    await this.loadTargets();
  }
  openLog(id = 'bench-press') {
    this.closeProgress();
    this.logExercise = id;
    this.weight = this.exercise(id)?.bodyweightMovement ? 0 : 20;
    this.reps = 5;
    this.date = this.today();
    this.error.set('');
    this.showLog.set(true);
  }
  clear() {
    this.standings.set([]);
    this.lifts.set([]);
    this.targets.set([]);
  }
}
