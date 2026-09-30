import {Component,Input,Output,EventEmitter,OnInit,signal,computed} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {apiRequest,ApiError} from './api';
import {TranslatePipe,translate} from './i18n';
interface Session {id:string;activity:string;distance_meters:number|null;reps:number|null;duration_seconds:number;performed_on:string;standard:string;notes:string;rank:string|null;rate:number;source:string|null;reference:string|null;}
interface Benchmark {activity:string;distanceMeters:number;source:string;reference:string;labels:string[];male:number[];female:number[];}
interface Activity {id:string;name:string;activity:string;distance:number|null;}
@Component({selector:'app-cardio',standalone:true,imports:[CommonModule,FormsModule,TranslatePipe],templateUrl:'./cardio.html'})
export class CardioComponent implements OnInit {
  @Input() ranksEnabled=true; @Input() standard='male';
  @Output() sessionExpired=new EventEmitter<void>();
  sessions=signal<Session[]>([]);benchmarks=signal<Benchmark[]>([]);busy=signal(false);error=signal('');notice=signal('');ready=signal(false);
  activities:Activity[]=[{id:'rope',name:'Jump rope',activity:'rope',distance:null},...[400,1000,5000,10000,20000].map(d=>({id:'run-'+d,name:'Running',activity:'running',distance:d})),{id:'cycling',name:'Cycling',activity:'cycling',distance:null}];
  selected=signal<Activity|null>(null);days=signal(0);comparison=signal<number|null>(null);deleteId=signal('');
  distanceKm=20;reps=100;minutes=1;seconds=0;date=this.today();notes='';
  title(a:Activity){return translate(a.name)+(a.distance?' · '+(a.distance<1000?a.distance+' m':a.distance/1000+' km'):'');}
  today(){const d=new Date();return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;}
  forActivity(a:Activity){return this.sessions().filter(s=>s.activity===a.activity&&(!a.distance||s.distance_meters===a.distance));}
  history=computed(()=>{const a=this.selected();if(!a)return [];const start=new Date();start.setDate(start.getDate()-this.days()+1);const cutoff=`${start.getFullYear()}-${String(start.getMonth()+1).padStart(2,'0')}-${String(start.getDate()).padStart(2,'0')}`;return this.forActivity(a).filter(s=>(!this.days()||s.performed_on>=cutoff)&&(this.comparison()===null||(a.activity==='rope'?s.duration_seconds===this.comparison():s.distance_meters===Math.round(this.comparison()!*1000))));});
  // Daily best pace/cadence; preserve the full sessions separately below the graph.
  points=computed(()=>{const groups=new Map<string,Session>();for(const s of this.history()){const old=groups.get(s.performed_on);if(!old||s.rate>old.rate)groups.set(s.performed_on,s);}const rows=[...groups.values()].sort((a,b)=>a.performed_on.localeCompare(b.performed_on));if(!rows.length)return [];const start=Date.parse(rows[0].performed_on),span=Date.parse(rows.at(-1)!.performed_on)-start,max=Math.max(1,...rows.map(s=>s.rate));return rows.map(s=>({s,x:span?60+670*(Date.parse(s.performed_on)-start)/span:395,y:230-190*s.rate/max}));});
  line=computed(()=>this.points().map(p=>`${p.x},${p.y}`).join(' '));
  best(a:Activity){return [...this.forActivity(a)].sort((a,b)=>b.rate-a.rate)[0];}
  bestRank(a:Activity){const rows=this.forActivity(a).filter(s=>s.standard===this.standard&&s.rank);return rows.sort((a,b)=>this.rankLevel(b)-this.rankLevel(a))[0]?.rank;}
  rankLevel(s:Session){return this.benchmarks().find(b=>b.activity===s.activity&&b.distanceMeters===s.distance_meters)?.labels.indexOf(s.rank||'')??-1;}
  time(value:number){const hours=Math.floor(value/3600),minutes=Math.floor(value%3600/60),seconds=(value%60).toFixed(2).replace(/\.00$/,'');return (hours?hours+':'+String(minutes).padStart(2,'0'):String(minutes))+':'+seconds.padStart(seconds.includes('.')?5:2,'0');}
  async ngOnInit(){await this.act(async()=>{this.benchmarks.set(await apiRequest<Benchmark[]>('/api/cardio/benchmarks'));await this.refresh();});this.ready.set(true);}
  async refresh(){this.sessions.set(await apiRequest<Session[]>('/api/cardio'));}
  async act(action:()=>Promise<void>){this.busy.set(true);this.error.set('');try{await action();}catch(e){if(e instanceof ApiError&&e.status===401)this.sessionExpired.emit();this.error.set(e instanceof Error?e.message:'Something went wrong.');}finally{this.busy.set(false);}}
  open(a:Activity){this.selected.set(a);this.days.set(0);this.comparison.set(null);this.deleteId.set('');this.error.set('');this.notice.set('');this.distanceKm=a.distance?a.distance/1000:20;this.reps=100;this.minutes=a.activity==='rope'?1:a.activity==='cycling'?60:5;this.seconds=0;this.date=this.today();this.notes='';setTimeout(()=>document.querySelector<HTMLDialogElement>('.cardio-modal')?.showModal());}
  close(){if(this.busy())return;document.querySelector<HTMLDialogElement>('.cardio-modal')?.close();this.selected.set(null);}
  backdrop(e:MouseEvent){if(e.target!==e.currentTarget)return;const r=(e.currentTarget as HTMLElement).getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)this.close();}
  async save(){const a=this.selected()!;await this.act(async()=>{await apiRequest('/api/cardio','POST',{activity:a.activity,distanceMeters:a.activity==='rope'?null:a.distance??Math.round(this.distanceKm*1000),reps:a.activity==='rope'?this.reps:null,durationSeconds:this.minutes*60+this.seconds,performedOn:this.date,notes:this.notes});await this.refresh();this.notice.set('Cardio session saved.');});}
  async remove(id:string){await this.act(async()=>{await apiRequest('/api/cardio/'+id,'DELETE');await this.refresh();this.deleteId.set('');this.notice.set('Cardio session deleted.');});}
  currentBenchmark(){const a=this.selected();return this.benchmarks().find(b=>b.activity===a?.activity&&b.distanceMeters===(a?.distance??Math.round(this.distanceKm*1000)));}
  thresholds(b:Benchmark){return this.standard==='female'?b.female:b.male;}
  cyclingDistances(){return this.benchmarks().filter(b=>b.activity==='cycling').map(b=>b.distanceMeters/1000).join(', ');}
}
