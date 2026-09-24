import { Component, EventEmitter, Input, OnInit, Output, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

interface Choice { id:string; name:string; }
interface Movement { id:string; name:string; family:string; category:string; movementType:string; difficulty:number; unit:string; note:string; equipment:string[]; patterns:string[]; muscles:string[]; }
interface Restriction { kind:string; target:string; reason:string; active:boolean; }
interface Preferences { level:number; equipment:string[]; restrictions:Restriction[]; }
interface Config { type:string; stimulus:string; level:number; intensity:number; duration:number; count:number; rounds:number; scheduledOn:string; equipment:string[]; bannedExercises:string[]; bannedPatterns:string[]; bannedMuscles:string[]; bannedEquipment:string[]; }
interface Prescription { exerciseId:string; quantity:number; weight:number; }
interface Item { exercise:Movement; quantity:number; weight:number; alternatives:Prescription[]; }
interface Draft { config:Config; items:Item[]; rounds:number; difficulty:number; instructions:string; explanations:string[]; }
interface Catalog { exercises:Movement[]; equipment:Choice[]; patterns:Choice[]; muscles:Choice[]; levels:{id:number;name:string}[]; types:Choice[]; stimuli:Choice[]; }
interface Summary { id:string; title:string; type_id:string; stimulus_id:string; level_id:number; duration_minutes:number; rounds:number; assessed_difficulty:number; scheduled_on:string; status:string; completed_on:string|null; owner_id:string; revision:number; }
interface Detail extends Summary { config:Config; items:Item[]; instructions:string; isOwner:boolean; canEdit:boolean; participants:{id:string;name:string}[]; duration_seconds:number|null; rounds_completed:number|null; extra_reps:number|null; effort:number|null; notes:string; }
interface Statistics { completed:number; trainingDays:number; loggedMinutes:number; averageEffort:number|null; upcoming:number; weekly:{week:string;count:number}[]; muscles:Record<string,number>; types:Record<string,number>; mostHit:string[]; leastHit:string[]; uniqueMovements:number; windowStart:string; windowEnd:string; }

@Component({selector:'app-wod',standalone:true,imports:[CommonModule,FormsModule],templateUrl:'./wod.html',styleUrl:'./wod.css'})
export class WodComponent implements OnInit {
  @Input({required:true}) userId='';
  @Output() sessionExpired=new EventEmitter<void>();
  tab=signal('generate'); loading=signal(true); busy=signal(false); error=signal(''); notice=signal('');
  catalog=signal<Catalog|null>(null); draft=signal<Draft|null>(null); history=signal<Summary[]>([]); detail=signal<Detail|null>(null); statistics=signal<Statistics|null>(null);
  preferences:Preferences={level:1,equipment:[],restrictions:[]};
  config:Config={type:'AUTO',stimulus:'balanced',level:1,intensity:3,duration:20,count:4,rounds:3,scheduledOn:this.today(),equipment:[],bannedExercises:[],bannedPatterns:[],bannedMuscles:[],bannedEquipment:[]};
  title='My WOD'; editingId=''; editingRevision:number|null=null; banSearch=''; restrictionKind='exercise'; restrictionTarget=''; restrictionReason=''; participantEmail=''; confirmLeave=false;
  historyFilter=signal('all'); historyQuery=signal(''); historyFrom=signal(''); historyTo=signal('');
  filtered=computed(()=>this.history().filter(w=>(this.historyFilter()==='all'||(this.historyFilter()==='upcoming'?w.status==='planned'&&w.scheduled_on>=this.today():w.status===this.historyFilter()))&&w.title.toLowerCase().includes(this.historyQuery().toLowerCase())&&(!this.historyFrom()||w.scheduled_on>=this.historyFrom())&&(!this.historyTo()||w.scheduled_on<=this.historyTo())));
  result={status:'planned',scheduledOn:this.today(),completedOn:this.today(),durationSeconds:null as number|null,roundsCompleted:null as number|null,extraReps:null as number|null,effort:null as number|null,notes:''};
  today(){const d=new Date();return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;}
  async api<T>(path:string,method='GET',body?:unknown):Promise<T>{
    const response=await fetch('/api/wods'+path,{method,credentials:'same-origin',headers:{'Content-Type':'application/json','X-Requested-With':'changoff'},body:body===undefined?undefined:JSON.stringify(body)});
    if(!response.ok){if(response.status===401)this.sessionExpired.emit();const data=await response.json().catch(()=>({message:'Request failed. Please try again.'}));throw new Error(data.message||'Request failed. Please try again.');}
    return response.status===204?undefined as T:response.json();
  }
  async act(action:()=>Promise<void>){if(this.busy())return;this.busy.set(true);this.error.set('');this.notice.set('');try{await action();}catch(e){this.error.set(e instanceof Error?e.message:'Something went wrong.');}finally{this.busy.set(false);}}
  async ngOnInit(){await this.act(async()=>{const [catalog,prefs]=await Promise.all([this.api<Catalog>('/catalog'),this.api<Preferences>('/preferences')]);this.catalog.set(catalog);this.preferences=prefs;this.config.level=prefs.level;this.config.equipment=[...prefs.equipment];await this.refresh();});this.loading.set(false);}
  async refresh(){const [history,statistics]=await Promise.all([this.api<Summary[]>(''),this.api<Statistics>('/statistics')]);this.history.set(history);this.statistics.set(statistics);}
  changeTab(tab:string){this.tab.set(tab);this.error.set('');this.notice.set('');}
  label(choices:Choice[]|undefined,id:string){return choices?.find(c=>c.id===id)?.name||id;}
  movement(id:string){return this.catalog()?.exercises.find(e=>e.id===id);}
  typeName(type:string){return this.label(this.catalog()?.types,type);}
  levelName(id:number){return this.catalog()?.levels.find(l=>l.id===id)?.name||'';}
  toggle(list:string[],id:string){const index=list.indexOf(id);index<0?list.push(id):list.splice(index,1);this.invalidate();}
  invalidate(){this.draft.set(null);}
  matchingMovements(){return this.catalog()?.exercises.filter(e=>e.name.toLowerCase().includes(this.banSearch.toLowerCase()))||[];}
  restrictionChoices():Choice[]{const c=this.catalog();return !c?[]:this.restrictionKind==='exercise'?c.exercises:this.restrictionKind==='pattern'?c.patterns:this.restrictionKind==='muscle'?c.muscles:c.equipment;}
  restrictionName(r:Restriction){const c=this.catalog();return this.label(r.kind==='exercise'?c?.exercises:r.kind==='pattern'?c?.patterns:r.kind==='muscle'?c?.muscles:c?.equipment,r.target);}
  addRestriction(){if(!this.restrictionTarget)return;if(!this.preferences.restrictions.some(r=>r.kind===this.restrictionKind&&r.target===this.restrictionTarget)){this.preferences.restrictions.push({kind:this.restrictionKind,target:this.restrictionTarget,reason:this.restrictionReason,active:true});}this.restrictionTarget='';this.restrictionReason='';}
  async savePreferences(){await this.act(async()=>{this.preferences.level=this.config.level;this.preferences.equipment=[...this.config.equipment];this.preferences=await this.api<Preferences>('/preferences','PUT',this.preferences);this.invalidate();this.notice.set('Saved your level, available equipment and persistent restrictions.');});}
  prescriptions(items:Item[]):Prescription[]{return items.map(i=>({exerciseId:i.exercise.id,quantity:i.quantity,weight:i.weight}));}
  async generate(){await this.act(async()=>{const d=await this.api<Draft>('/generate','POST',{config:this.config});this.draft.set(d);this.config=structuredClone(d.config);});}
  async recalculate(){const d=this.draft();if(!d)return;await this.act(async()=>{this.draft.set(await this.api<Draft>('/generate','POST',{config:this.config,exercises:this.prescriptions(d.items)}));});}
  async substitute(index:number,id:string){const d=this.draft();if(!d||!id)return;const suggestion=d.items[index].alternatives.find(a=>a.exerciseId===id);if(!suggestion)return;await this.act(async()=>{const sets=this.prescriptions(d.items);sets[index]=suggestion;this.draft.set(await this.api<Draft>('/generate','POST',{config:this.config,exercises:sets}));this.notice.set('Movement swapped and difficulty recalculated.');});}
  async save(){const d=this.draft();if(!d)return;await this.act(async()=>{const saved=await this.api<Detail>(this.editingId?'/'+this.editingId:'',this.editingId?'PUT':'POST',{title:this.title,config:this.config,exercises:this.prescriptions(d.items),revision:this.editingRevision});this.draft.set(null);this.editingId='';this.editingRevision=null;await this.refresh();this.showDetail(saved);this.tab.set('history');this.notice.set('WOD saved. Plan it, record your result, or add a training partner below.');});}
  async open(id:string){await this.act(async()=>{this.showDetail(await this.api<Detail>('/'+id));});}
  showDetail(w:Detail){this.detail.set(w);this.confirmLeave=false;this.participantEmail='';this.result={status:w.status,scheduledOn:w.scheduled_on,completedOn:w.completed_on||this.today(),durationSeconds:w.duration_seconds,roundsCompleted:w.rounds_completed,extraReps:w.extra_reps,effort:w.effort,notes:w.notes};}
  async useWorkout(edit:boolean){const w=this.detail();if(!w)return;await this.act(async()=>{this.config=structuredClone(w.config);this.config.scheduledOn=edit?w.scheduled_on:this.today();this.title=edit?w.title:w.title+' · repeat';this.editingId=edit?w.id:'';this.editingRevision=edit?w.revision:null;this.draft.set(await this.api<Draft>('/generate','POST',{config:this.config,exercises:this.prescriptions(w.items)}));this.tab.set('generate');});}
  cancelEdit(){this.editingId='';this.editingRevision=null;this.draft.set(null);this.title='My WOD';}
  async saveResult(){const w=this.detail();if(!w)return;await this.act(async()=>{this.showDetail(await this.api<Detail>('/'+w.id+'/result','PUT',{...this.result,completedOn:this.result.status==='completed'?this.result.completedOn:null}));await this.refresh();this.notice.set('Your schedule and result have been saved.');});}
  async share(){const w=this.detail();if(!w)return;await this.act(async()=>{this.showDetail(await this.api<Detail>('/'+w.id+'/participants','POST',{email:this.participantEmail}));this.notice.set('Participant added. This WOD is now in their personal WOD history.');});}
  async leave(){const w=this.detail();if(!w)return;await this.act(async()=>{await this.api('/'+w.id+'/participation','DELETE');this.detail.set(null);await this.refresh();this.notice.set('Removed from your WOD history. Other participants keep their records.');});}
  muscleRows(){return Object.entries(this.statistics()?.muscles||{}).sort((a,b)=>b[1]-a[1]);}
  typeRows(){return Object.entries(this.statistics()?.types||{});}
  maxMuscles(){return Math.max(1,...Object.values(this.statistics()?.muscles||{}));}
  maxWeek(){return Math.max(1,...(this.statistics()?.weekly.map(w=>w.count)||[]));}
  names(ids:string[]){return ids.map(id=>this.label(this.catalog()?.muscles,id)).join(', ')||'No completed WODs yet';}
}
