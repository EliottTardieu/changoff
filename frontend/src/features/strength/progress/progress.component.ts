import { Component, computed, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '../../../core/i18n/i18n';
import { Lift } from '../strength.models';
import {
  filterLifts,
  dailyBestSets,
  projectStrengthPoints,
  ProgressMetric,
} from './progress-calculations';
@Component({
  selector: 'app-progress',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './progress.component.html',
})
export class ProgressComponent {
  lifts = input<Lift[]>([]);
  name = input('Exercise');
  addedLoad = input(false);
  metric = signal<ProgressMetric>('weight');
  days = signal(0);
  comparison = signal<number | null>(null);
  filtered = computed(() =>
    filterLifts(this.lifts(), this.metric(), this.days(), this.comparison()),
  );
  daily = computed(() => dailyBestSets(this.filtered(), this.metric()));
  ceiling = computed(() => Math.max(1, ...this.daily().map((l) => l[this.metric()])));
  ticks = computed(() =>
    [0, 0.25, 0.5, 0.75, 1].map((f) => ({ value: f * this.ceiling(), y: 236 - f * 204 })),
  );
  points = computed(() => projectStrengthPoints(this.daily(), this.metric(), this.ceiling()));
  line = computed(() =>
    this.points()
      .map((p) => `${p.x},${p.y}`)
      .join(' '),
  );
}
