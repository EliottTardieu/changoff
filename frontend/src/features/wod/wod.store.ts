import { Injectable, EventEmitter, signal, computed, inject } from '@angular/core';
import { translate } from '../../core/i18n/i18n';
import { ApiError } from '../../core/http/api-client';
import { RequestState } from '../../core/feedback/request-state';
import { NavigationService, WodTab } from '../../core/navigation/navigation.service';
import { localDate } from '../../shared/dates/local-date';
import { WodApi } from './wod-api.service';
import {
  Choice,
  Restriction,
  Preferences,
  Config,
  Prescription,
  Item,
  Draft,
  Catalog,
  Summary,
  Detail,
  Statistics,
} from './wod.models';
@Injectable()
export class WodStore {
  private readonly api = inject(WodApi);
  private readonly feedback = inject(RequestState);
  private readonly navigation = inject(NavigationService);
  readonly busy = this.feedback.busy;
  readonly error = this.feedback.error;
  readonly notice = this.feedback.notice;
  readonly today = localDate;
  private unsubscribe = () => {};
  async act(action: () => Promise<void>) {
    await this.feedback.run(action, {
      clearNotice: true,
      onError: (error) => {
        if (error instanceof ApiError && error.status === 401) this.sessionExpired.emit();
      },
    });
  }
  async initialize() {
    this.unsubscribe();
    this.unsubscribe = this.navigation.onPopState(() => this.tab.set(this.navigation.readWodTab()));
    await this.act(async () => {
      const [catalog, prefs] = await Promise.all([this.api.catalog(), this.api.preferences()]);
      this.catalog.set(catalog);
      this.preferences = prefs;
      this.config.level = prefs.level;
      this.config.equipment = [...prefs.equipment];
      await this.refresh();
    });
    this.loading.set(false);
  }
  destroy() {
    this.unsubscribe();
  }
  changeTab(tab: string) {
    this.navigation.wodTab(tab as WodTab);
    this.tab.set(tab as WodTab);
    this.feedback.clear();
  }
  userId = '';
  sessionExpired = new EventEmitter<void>();
  tab = signal<WodTab>(this.navigation.readWodTab());
  loading = signal(true);
  catalog = signal<Catalog | null>(null);
  draft = signal<Draft | null>(null);
  history = signal<Summary[]>([]);
  detail = signal<Detail | null>(null);
  statistics = signal<Statistics | null>(null);
  preferences: Preferences = { level: 1, equipment: [], restrictions: [] };
  config: Config = {
    type: 'AUTO',
    stimulus: 'balanced',
    level: 1,
    intensity: 3,
    duration: 20,
    count: 4,
    rounds: 3,
    scheduledOn: this.today(),
    equipment: [],
    bannedExercises: [],
    bannedPatterns: [],
    bannedMuscles: [],
    bannedEquipment: [],
  };
  title = translate('My WOD');
  editingId = '';
  editingRevision: number | null = null;
  banSearch = '';
  restrictionKind = 'exercise';
  restrictionTarget = '';
  restrictionReason = '';
  participantEmail = '';
  confirmLeave = false;
  historyFilter = signal('all');
  historyQuery = signal('');
  historyFrom = signal('');
  historyTo = signal('');
  filtered = computed(() =>
    this.history().filter(
      (workout) =>
        (this.historyFilter() === 'all' ||
          (this.historyFilter() === 'upcoming'
            ? workout.status === 'planned' && workout.scheduled_on >= this.today()
            : workout.status === this.historyFilter())) &&
        workout.title.toLowerCase().includes(this.historyQuery().toLowerCase()) &&
        (!this.historyFrom() || workout.scheduled_on >= this.historyFrom()) &&
        (!this.historyTo() || workout.scheduled_on <= this.historyTo()),
    ),
  );
  result = {
    status: 'planned',
    scheduledOn: this.today(),
    completedOn: this.today(),
    durationSeconds: null as number | null,
    roundsCompleted: null as number | null,
    extraReps: null as number | null,
    effort: null as number | null,
    notes: '',
  };
  async refresh() {
    const [history, statistics] = await Promise.all([this.api.history(), this.api.statistics()]);
    this.history.set(history);
    this.statistics.set(statistics);
  }
  label(choices: Choice[] | undefined, id: string) {
    return translate(choices?.find((catalog) => catalog.id === id)?.name || id);
  }
  movement(id: string) {
    return this.catalog()?.exercises.find((exercise) => exercise.id === id);
  }
  typeName(type: string) {
    return this.label(this.catalog()?.types, type);
  }
  levelName(id: number) {
    return this.catalog()?.levels.find((level) => level.id === id)?.name || '';
  }
  toggle(list: string[], id: string) {
    const index = list.indexOf(id);
    index < 0 ? list.push(id) : list.splice(index, 1);
    this.invalidate();
  }
  invalidate() {
    this.draft.set(null);
  }
  matchingMovements() {
    return (
      this.catalog()?.exercises.filter((exercise) =>
        translate(exercise.name).toLowerCase().includes(this.banSearch.toLowerCase()),
      ) || []
    );
  }
  restrictionChoices(): Choice[] {
    const catalog = this.catalog();
    return !catalog
      ? []
      : this.restrictionKind === 'exercise'
        ? catalog.exercises
        : this.restrictionKind === 'pattern'
          ? catalog.patterns
          : this.restrictionKind === 'muscle'
            ? catalog.muscles
            : catalog.equipment;
  }
  restrictionName(restriction: Restriction) {
    const catalog = this.catalog();
    return this.label(
      restriction.kind === 'exercise'
        ? catalog?.exercises
        : restriction.kind === 'pattern'
          ? catalog?.patterns
          : restriction.kind === 'muscle'
            ? catalog?.muscles
            : catalog?.equipment,
      restriction.target,
    );
  }
  addRestriction() {
    if (!this.restrictionTarget) return;
    if (
      !this.preferences.restrictions.some(
        (restriction) =>
          restriction.kind === this.restrictionKind &&
          restriction.target === this.restrictionTarget,
      )
    ) {
      this.preferences.restrictions.push({
        kind: this.restrictionKind,
        target: this.restrictionTarget,
        reason: this.restrictionReason,
        active: true,
      });
    }
    this.restrictionTarget = '';
    this.restrictionReason = '';
  }
  async savePreferences() {
    await this.act(async () => {
      this.preferences.level = this.config.level;
      this.preferences.equipment = [...this.config.equipment];
      this.preferences = await this.api.savePreferences(this.preferences);
      this.invalidate();
      this.notice.set('Saved your level, available equipment and persistent restrictions.');
    });
  }
  prescriptions(items: Item[]): Prescription[] {
    return items.map((item) => ({
      exerciseId: item.exercise.id,
      quantity: item.quantity,
      weight: item.weight,
    }));
  }
  async generate() {
    await this.act(async () => {
      const draft = await this.api.generate({ config: this.config });
      this.draft.set(draft);
      this.config = structuredClone(draft.config);
    });
  }
  async recalculate() {
    const draft = this.draft();
    if (!draft) return;
    await this.act(async () => {
      this.draft.set(
        await this.api.generate({
          config: this.config,
          exercises: this.prescriptions(draft.items),
        }),
      );
    });
  }
  async substitute(index: number, id: string) {
    const draft = this.draft();
    if (!draft || !id) return;
    const suggestion = draft.items[index].alternatives.find(
      (alternative) => alternative.exerciseId === id,
    );
    if (!suggestion) return;
    await this.act(async () => {
      const sets = this.prescriptions(draft.items);
      sets[index] = suggestion;
      this.draft.set(await this.api.generate({ config: this.config, exercises: sets }));
      this.notice.set('Movement swapped and difficulty recalculated.');
    });
  }
  async save() {
    const draft = this.draft();
    if (!draft) return;
    await this.act(async () => {
      const saved = await this.api.save(
        {
          title: this.title,
          config: this.config,
          exercises: this.prescriptions(draft.items),
          revision: this.editingRevision,
        },
        this.editingId || undefined,
      );
      this.draft.set(null);
      this.editingId = '';
      this.editingRevision = null;
      await this.refresh();
      this.showDetail(saved);
      this.changeTab('history');
      this.notice.set('WOD saved. Plan it, record your result, or add a training partner below.');
    });
  }
  async open(id: string) {
    await this.act(async () => {
      this.showDetail(await this.api.detail(id));
    });
  }
  showDetail(workout: Detail) {
    this.detail.set(workout);
    this.confirmLeave = false;
    this.participantEmail = '';
    this.result = {
      status: workout.status,
      scheduledOn: workout.scheduled_on,
      completedOn: workout.completed_on || this.today(),
      durationSeconds: workout.duration_seconds,
      roundsCompleted: workout.rounds_completed,
      extraReps: workout.extra_reps,
      effort: workout.effort,
      notes: workout.notes,
    };
  }
  async useWorkout(edit: boolean) {
    const workout = this.detail();
    if (!workout) return;
    await this.act(async () => {
      this.config = structuredClone(workout.config);
      this.config.scheduledOn = edit ? workout.scheduled_on : this.today();
      this.title = edit ? workout.title : workout.title + ' · ' + translate('repeat');
      this.editingId = edit ? workout.id : '';
      this.editingRevision = edit ? workout.revision : null;
      this.draft.set(
        await this.api.generate({
          config: this.config,
          exercises: this.prescriptions(workout.items),
        }),
      );
      this.changeTab('generate');
    });
  }
  cancelEdit() {
    this.editingId = '';
    this.editingRevision = null;
    this.draft.set(null);
    this.title = translate('My WOD');
  }
  async saveResult() {
    const workout = this.detail();
    if (!workout) return;
    await this.act(async () => {
      this.showDetail(
        await this.api.result(workout.id, {
          ...this.result,
          completedOn: this.result.status === 'completed' ? this.result.completedOn : null,
        }),
      );
      await this.refresh();
      this.notice.set('Your schedule and result have been saved.');
    });
  }
  async share() {
    const workout = this.detail();
    if (!workout) return;
    await this.act(async () => {
      this.showDetail(await this.api.share(workout.id, this.participantEmail));
      this.notice.set('Participant added. This WOD is now in their personal WOD history.');
    });
  }
  async leave() {
    const workout = this.detail();
    if (!workout) return;
    await this.act(async () => {
      await this.api.leave(workout.id);
      this.detail.set(null);
      await this.refresh();
      this.notice.set('Removed from your WOD history. Other participants keep their records.');
    });
  }
  muscleRows() {
    return Object.entries(this.statistics()?.muscles || {}).sort(
      (alternative, b) => b[1] - alternative[1],
    );
  }
  typeRows() {
    return Object.entries(this.statistics()?.types || {});
  }
  maxMuscles() {
    return Math.max(1, ...Object.values(this.statistics()?.muscles || {}));
  }
  maxWeek() {
    return Math.max(1, ...(this.statistics()?.weekly.map((workout) => workout.count) || []));
  }
  names(ids: string[]) {
    return (
      ids.map((id) => this.label(this.catalog()?.muscles, id)).join(', ') ||
      translate('No completed WODs yet')
    );
  }
}
