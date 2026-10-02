import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '../../core/i18n/i18n';
import { StrengthStore } from './strength.store';

@Component({
  selector: 'app-strength-history',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './history.component.html',
  host: { style: 'display: contents' },
})
export class StrengthHistoryComponent {
  readonly vm = inject(StrengthStore);
}
