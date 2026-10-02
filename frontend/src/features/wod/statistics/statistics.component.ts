import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '../../../core/i18n/i18n';
import { WodStore } from '../wod.store';
@Component({
  selector: 'app-wod-statistics',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './statistics.component.html',
  host: { style: 'display: contents' },
})
export class WodStatisticsComponent {
  readonly vm = inject(WodStore);
}
