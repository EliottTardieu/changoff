import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '../../core/i18n/i18n';
import { StrengthStore } from './strength.store';
import { ProgressComponent } from './progress/progress.component';
import { ModalDirective, AutofocusDirective } from '../../shared/dialog/modal.directive';
@Component({
  selector: 'app-strength-dialogs',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TranslatePipe,
    ProgressComponent,
    ModalDirective,
    AutofocusDirective,
  ],
  templateUrl: './strength-dialogs.component.html',
  host: { style: 'display: contents' },
})
export class StrengthDialogsComponent {
  readonly vm = inject(StrengthStore);
}
