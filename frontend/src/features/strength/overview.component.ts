import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '../../core/i18n/i18n';
import { StrengthStore } from './strength.store';

@Component({
  selector: 'app-strength-overview',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './overview.component.html',
  host: { style: 'display: contents' },
})
export class StrengthOverviewComponent {
  readonly vm = inject(StrengthStore);
}
