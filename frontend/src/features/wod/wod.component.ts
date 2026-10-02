import {
  Component,
  Input,
  Output,
  OnInit,
  OnDestroy,
  ViewEncapsulation,
  inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslatePipe } from '../../core/i18n/i18n';
import { RequestState } from '../../core/feedback/request-state';
import { WodStore } from './wod.store';
import { WodGenerateComponent } from './generate/generate.component';
import { WodHistoryComponent } from './history/history.component';
import { WodStatisticsComponent } from './statistics/statistics.component';
@Component({
  selector: 'app-wod',
  standalone: true,
  imports: [
    CommonModule,
    TranslatePipe,
    WodGenerateComponent,
    WodHistoryComponent,
    WodStatisticsComponent,
  ],
  providers: [WodStore, RequestState],
  templateUrl: './wod.component.html',
  styleUrl: './wod.css',
  encapsulation: ViewEncapsulation.None,
})
export class WodComponent implements OnInit, OnDestroy {
  readonly vm = inject(WodStore);
  @Input({ required: true }) set userId(value: string) {
    this.vm.userId = value;
  }
  @Output() readonly sessionExpired = this.vm.sessionExpired;
  ngOnInit() {
    void this.vm.initialize();
  }
  ngOnDestroy() {
    this.vm.destroy();
  }
}
