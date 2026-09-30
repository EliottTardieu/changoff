import { Component, computed, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from './i18n';

export interface Lift { id:string; exercise:string; weight:number; reps:number; bodyweight:number; standard:string; score:number; performed_on:string; }

export function bestSet(lifts: Lift[], metric: 'weight'|'reps'): Lift | undefined {
  const other = metric === 'weight' ? 'reps' : 'weight';
  return [...lifts].sort((a,b) => b[metric]-a[metric] || b[other]-a[other] || b.performed_on.localeCompare(a.performed_on))[0];
}

@Component({
  selector: 'app-progress', standalone: true, imports: [CommonModule, FormsModule, TranslatePipe],
  template: `
    <div class="progress-controls">
      <label>{{ 'Plot' | t }}<select [ngModel]="metric()" (ngModelChange)="metric.set($event); comparison.set(null)"><option value="weight">{{ 'Heaviest set per day (kg)' | t }}</option><option value="reps">{{ 'Most reps per day' | t }}</option></select></label>
      <label>{{ 'Time range' | t }}<select [ngModel]="days()" (ngModelChange)="days.set($event)"><option [ngValue]="0">{{ 'All history' | t }}</option><option [ngValue]="90">{{ 'Last 90 days' | t }}</option><option [ngValue]="30">{{ 'Last 30 days' | t }}</option></select></label>
      <label>{{ (metric()==='weight'?'Compare at reps':'Compare at weight (kg)') | t }}<input type="number" [min]="metric()==='weight'?1:0" [step]="metric()==='weight'?1:0.1" [ngModel]="comparison()" (ngModelChange)="comparison.set($event)" [placeholder]="('All sets' | t)"></label>
    </div>
    <p class="fine">{{ '{0} Each point is an actual daily best set, not an estimated maximum. Use the comparison filter to compare the same reps or load. Days without entries are not zero workouts.' | t:((addedLoad()?'Weights are added load; 0 kg means bodyweight alone.':'Weights are the external load you logged.') | t) }}</p>
    @if(points().length) {
      <div class="chart-summary" aria-live="polite"><strong>{{ '{0} sets · {1} training days' | t:(filtered().length | t):(points().length | t) }}</strong><span>{{ 'First → latest daily best: {0} → {1} {2}' | t:(points()[0].value | t):(points()[points().length-1].value | t):((metric()==='weight'?'kg':'reps') | t) }}</span></div>
      <svg class="training-chart" viewBox="0 0 760 280" role="img" [attr.aria-label]="'{0} {1} progress over time. Exact sets are available in the table below.' | t:(name() | t):((metric()==='weight'?'weight':'repetitions') | t)">
        @for(tick of ticks(); track tick.value) {
          <line x1="60" x2="730" [attr.y1]="tick.y" [attr.y2]="tick.y" class="chart-grid" />
          <text x="50" [attr.y]="tick.y+4" text-anchor="end">{{ tick.value|number:'1.0-1' | t }}</text>
        }
        <text x="60" y="16">{{ (metric()==='weight'?'kg':'reps') | t }}</text>
        <polyline [attr.points]="line()" class="chart-line" />
        @for(p of points(); track p.lift.performed_on) {
          <circle [attr.cx]="p.x" [attr.cy]="p.y" r="5" class="chart-point" tabindex="0" [attr.aria-label]="'{0}: {1} kg × {2} reps' | t:p.lift.performed_on:p.lift.weight:p.lift.reps">
            <title>{{ '{0}: {1} kg × {2} reps' | t:(p.lift.performed_on | t):(p.lift.weight | t):(p.lift.reps | t) }}</title>
          </circle>
        }
        <text x="60" y="266">{{ points()[0].lift.performed_on|date:'mediumDate' | t }}</text>
        @if(points().length>1){<text x="730" y="266" text-anchor="end">{{ points()[points().length-1].lift.performed_on|date:'mediumDate' | t }}</text>}
      </svg>
      @if(points().length===1){<p class="fine">{{ 'Your starting point is saved. Log another date to see a trend.' | t }}</p>}
      <details class="chart-data"><summary>{{ 'View daily best sets ({0})' | t:(points().length | t) }}</summary><div class="table-wrap"><table><caption>{{ '{0} — plotted sets' | t:(name() | t) }}</caption><thead><tr><th>{{ 'Date' | t }}</th><th>{{ (addedLoad()?'Added weight':'Weight') | t }}</th><th>{{ 'Reps' | t }}</th></tr></thead><tbody>@for(p of points();track p.lift.performed_on){<tr><td>{{ p.lift.performed_on|date:'mediumDate' | t }}</td><td>{{ '{0} kg' | t:(p.lift.weight | t) }}</td><td>{{ p.lift.reps | t }}</td></tr>}</tbody></table></div></details>
    } @else {
      <div class="empty"><h3>{{ (lifts().length?'No sets match these filters.':'Your progress starts with one set.') | t }}</h3><p>{{ (lifts().length?'Try all history or clear the comparison filter.':'Log a set for this exercise to start your graph.') | t }}</p></div>
    }
  `
})
export class ProgressComponent {
  lifts=input<Lift[]>([]); name=input('Exercise'); addedLoad=input(false);
  metric=signal<'weight'|'reps'>('weight'); days=signal(0); comparison=signal<number|null>(null);
  filtered=computed(()=>{
    const cutoff=new Date(); cutoff.setDate(cutoff.getDate()-this.days()+1);
    const start=`${cutoff.getFullYear()}-${String(cutoff.getMonth()+1).padStart(2,'0')}-${String(cutoff.getDate()).padStart(2,'0')}`;
    const other=this.metric()==='weight'?'reps':'weight';
    return this.lifts().filter(l=>(!this.days()||l.performed_on>=start)&&(this.comparison()===null||l[other]===this.comparison()));
  });
  daily=computed(()=>{
    const groups=new Map<string,Lift[]>();
    for(const lift of this.filtered()) groups.set(lift.performed_on,[...(groups.get(lift.performed_on)||[]),lift]);
    return [...groups].sort(([a],[b])=>a.localeCompare(b)).map(([,lifts])=>bestSet(lifts,this.metric())!);
  });
  ceiling=computed(()=>Math.max(1,...this.daily().map(l=>l[this.metric()])));
  ticks=computed(()=>[0,.25,.5,.75,1].map(f=>({value:f*this.ceiling(),y:236-f*204})));
  points=computed(()=>{
    const daily=this.daily(); if(!daily.length)return [];
    const start=Date.parse(daily[0].performed_on),span=Date.parse(daily[daily.length-1].performed_on)-start;
    return daily.map(lift=>({lift,value:lift[this.metric()],x:span?60+670*(Date.parse(lift.performed_on)-start)/span:395,y:236-204*lift[this.metric()]/this.ceiling()}));
  });
  line=computed(()=>this.points().map(p=>`${p.x},${p.y}`).join(' '));
}
