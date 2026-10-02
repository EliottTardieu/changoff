import {
  Component,
  Input,
  Output,
  EventEmitter,
  OnInit,
  signal,
  computed,
  inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiError } from '../../core/http/api-client';
import { RequestState } from '../../core/feedback/request-state';
import { TranslatePipe, translate } from '../../core/i18n/i18n';
import { ModalDirective, isBackdropClick } from '../../shared/dialog/modal.directive';
import { localDate } from '../../shared/dates/local-date';
import { CardioApi } from './cardio-api.service';
import { CardioSession, Benchmark, Activity } from './cardio.models';
import {
  CARDIO_ACTIVITIES,
  sessionsForActivity,
  filterCardioHistory,
  projectCardioPoints,
  bestCardioRank,
  formatDuration,
} from './cardio-calculations';
@Component({
  selector: 'app-cardio',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, ModalDirective],
  providers: [RequestState],
  templateUrl: './cardio.component.html',
})
export class CardioComponent implements OnInit {
  private readonly api = inject(CardioApi);
  private readonly feedback = inject(RequestState);
  readonly busy = this.feedback.busy;
  readonly error = this.feedback.error;
  readonly notice = this.feedback.notice;
  sessions = signal<CardioSession[]>([]);
  benchmarks = signal<Benchmark[]>([]);
  readonly activities = CARDIO_ACTIVITIES;
  readonly time = formatDuration;
  readonly today = localDate;
  history = computed(() =>
    filterCardioHistory(this.sessions(), this.selected(), this.days(), this.comparison()),
  );
  points = computed(() => projectCardioPoints(this.history()));
  forActivity(activity: Activity) {
    return sessionsForActivity(this.sessions(), activity);
  }
  bestRank(activity: Activity) {
    return bestCardioRank(this.forActivity(activity), this.benchmarks(), this.standard);
  }
  async ngOnInit() {
    await this.act(async () => {
      this.benchmarks.set(await this.api.benchmarks());
      await this.refresh();
    });
    this.ready.set(true);
  }
  async refresh() {
    this.sessions.set(await this.api.history());
  }
  async act(action: () => Promise<void>) {
    await this.feedback.run(action, {
      onError: (error) => {
        if (error instanceof ApiError && error.status === 401) this.sessionExpired.emit();
      },
    });
  }
  backdrop(event: MouseEvent) {
    if (isBackdropClick(event)) this.close();
  }
  @Input() ranksEnabled = true;
  @Input() standard = 'male';
  @Output() sessionExpired = new EventEmitter<void>();
  ready = signal(false);
  selected = signal<Activity | null>(null);
  days = signal(0);
  comparison = signal<number | null>(null);
  deleteId = signal('');
  distanceKm = 20;
  reps = 100;
  minutes = 1;
  seconds = 0;
  date = this.today();
  notes = '';
  title(a: Activity) {
    return (
      translate(a.name) +
      (a.distance
        ? ' · ' + (a.distance < 1000 ? a.distance + ' m' : a.distance / 1000 + ' km')
        : '')
    );
  }
  line = computed(() =>
    this.points()
      .map((p) => `${p.x},${p.y}`)
      .join(' '),
  );
  best(a: Activity) {
    return [...this.forActivity(a)].sort((a, b) => b.rate - a.rate)[0];
  }
  open(a: Activity) {
    this.selected.set(a);
    this.days.set(0);
    this.comparison.set(null);
    this.deleteId.set('');
    this.error.set('');
    this.notice.set('');
    this.distanceKm = a.distance ? a.distance / 1000 : 20;
    this.reps = 100;
    this.minutes = a.activity === 'rope' ? 1 : a.activity === 'cycling' ? 60 : 5;
    this.seconds = 0;
    this.date = this.today();
    this.notes = '';
  }
  close() {
    if (this.busy()) return;
    this.selected.set(null);
  }
  async save() {
    const a = this.selected()!;
    await this.act(async () => {
      await this.api.save({
        activity: a.activity,
        distanceMeters:
          a.activity === 'rope' ? null : (a.distance ?? Math.round(this.distanceKm * 1000)),
        reps: a.activity === 'rope' ? this.reps : null,
        durationSeconds: this.minutes * 60 + this.seconds,
        performedOn: this.date,
        notes: this.notes,
      });
      await this.refresh();
      this.notice.set('Cardio session saved.');
    });
  }
  async remove(id: string) {
    await this.act(async () => {
      await this.api.remove(id);
      await this.refresh();
      this.deleteId.set('');
      this.notice.set('Cardio session deleted.');
    });
  }
  currentBenchmark() {
    const a = this.selected();
    return this.benchmarks().find(
      (b) =>
        b.activity === a?.activity &&
        b.distanceMeters === (a?.distance ?? Math.round(this.distanceKm * 1000)),
    );
  }
  thresholds(b: Benchmark) {
    return this.standard === 'female' ? b.female : b.male;
  }
  cyclingDistances() {
    return this.benchmarks()
      .filter((b) => b.activity === 'cycling')
      .map((b) => b.distanceMeters / 1000)
      .join(', ');
  }
}
