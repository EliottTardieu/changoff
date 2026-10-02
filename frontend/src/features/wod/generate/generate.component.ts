import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '../../../core/i18n/i18n';
import { WodStore } from '../wod.store';
@Component({
  selector: 'app-wod-generate',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './generate.component.html',
  host: { style: 'display: contents' },
})
export class WodGenerateComponent {
  readonly vm = inject(WodStore);
}
