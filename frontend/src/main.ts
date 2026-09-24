import { Component, signal, computed, OnInit } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { WodComponent } from './wod';

interface User { id:string; name:string; email:string; bodyweight:number; standard:string; }
interface Exercise { id:string; name:string; group:string; note:string; bodyweightMovement:boolean; source:string; }
interface Standing { exercise:Exercise; level:number; rank:string; score:number; progress:number; nextRank:string; }
interface Lift { id:string; exercise:string; weight:number; reps:number; bodyweight:number; standard:string; score:number; performed_on:string; }
interface Target { level:number; rank:string; ratio:number; weight:number; reps:number; }

@Component({selector:'app-root',standalone:true,imports:[CommonModule,FormsModule,WodComponent],templateUrl:'./app.html'})
class App implements OnInit {
  user=signal<User|null>(null); ready=signal(false); busy=signal(false); error=signal(''); notice=signal('');
  page=signal('overview'); authMode='login'; name=''; email=''; password=''; bodyweight=75; standard='male';
  standings=signal<Standing[]>([]); lifts=signal<Lift[]>([]); targets=signal<Target[]>([]);
  search=signal(''); group=signal('All'); groups=['All','Chest','Back','Shoulders','Arms','Legs'];
  tiers=['Bronze','Silver','Gold','Platinum','Emerald','Diamond','Master','Chang'];
  selected='bench-press'; targetReps=5; showLog=signal(false); logExercise='bench-press'; weight=20; reps=5; date=this.today();
  deleteId=signal(''); profileName=''; profileWeight=75; profileStandard='male';
  filtered=computed(()=>this.standings().filter(s=>(this.group()==='All'||s.exercise.group===this.group())&&s.exercise.name.toLowerCase().includes(this.search().toLowerCase())));
  ranked=computed(()=>this.standings().filter(s=>s.level>0).length);
  best=computed(()=>[...this.standings()].sort((a,b)=>b.level-a.level).at(0));
  weekly=computed(()=>{const cutoff=new Date();cutoff.setDate(cutoff.getDate()-6);return this.lifts().filter(l=>l.performed_on>=this.localDate(cutoff)).length;});
  today(){return this.localDate(new Date());}
  localDate(d:Date){return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;}
  async api<T>(path:string,method='GET',body?:unknown):Promise<T>{
    const res=await fetch('/api'+path,{method,credentials:'same-origin',headers:{'Content-Type':'application/json','X-Requested-With':'changoff'},body:body===undefined?undefined:JSON.stringify(body)});
    if(!res.ok){const data=await res.json().catch(()=>({message:'Request failed. Please try again.'}));if(res.status===401&&!path.startsWith('/auth'))this.user.set(null);throw new Error(data.message||'Request failed. Please try again.');}
    return res.status===204?undefined as T:await res.json();
  }
  async ngOnInit(){try{this.user.set(await this.api<User>('/me'));await this.refresh();}catch(e){if(this.user())this.error.set(this.message(e));}finally{this.ready.set(true);}}
  message(e:unknown){return e instanceof Error?e.message:'Something went wrong.';}
  async refresh(){const [s,l]=await Promise.all([this.api<Standing[]>('/dashboard'),this.api<Lift[]>('/lifts')]);this.standings.set(s);this.lifts.set(l);}
  async authenticate(){this.busy.set(true);this.error.set('');try{const body=this.authMode==='register'?{name:this.name,email:this.email,password:this.password,bodyweight:this.bodyweight,standard:this.standard}:{email:this.email,password:this.password};this.user.set(await this.api<User>('/auth/'+this.authMode,'POST',body));this.password='';await this.refresh();}catch(e){this.error.set(this.message(e));}finally{this.busy.set(false);}}
  async logout(){try{await this.api('/auth/logout','POST');this.authMode='login';this.user.set(null);this.standings.set([]);this.lifts.set([]);this.error.set('');this.notice.set('');this.page.set('overview');}catch(e){this.error.set(this.message(e));}}
  async navigate(page:string){this.page.set(page);this.error.set('');this.notice.set('');if(page==='ranks')await this.loadTargets();if(page==='profile'){const u=this.user()!;this.profileName=u.name;this.profileWeight=u.bodyweight;this.profileStandard=u.standard;}}
  async loadTargets(){try{this.targets.set(await this.api<Target[]>(`/exercises/${this.selected}/targets?reps=${this.targetReps}`));}catch(e){this.error.set(this.message(e));}}
  async viewRanks(id:string){this.selected=id;await this.navigate('ranks');}
  current(){return this.standings().find(s=>s.exercise.id===this.selected);}
  exercise(id:string){return this.standings().find(s=>s.exercise.id===id)?.exercise;}
  tier(level:number){return level===0?'unranked':this.tiers[Math.floor((level-1)/3)].toLowerCase();}
  openLog(id='bench-press'){this.logExercise=id;this.weight=this.exercise(id)?.bodyweightMovement?0:20;this.reps=5;this.date=this.today();this.error.set('');this.showLog.set(true);setTimeout(()=>document.querySelector<HTMLSelectElement>('.modal select')?.focus());}
  trapFocus(event:KeyboardEvent){if(event.key!=='Tab')return;const dialog=event.currentTarget as HTMLElement;const items=Array.from(dialog.querySelectorAll<HTMLElement>('button:not([disabled]),input,select'));const first=items[0],last=items.at(-1);if(event.shiftKey&&document.activeElement===first){event.preventDefault();last?.focus();}else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first?.focus();}}
  async saveLift(){this.busy.set(true);this.error.set('');try{const result=await this.api<{rank:string}>('/lifts','POST',{exercise:this.logExercise,weight:this.weight,reps:this.reps,performedOn:this.date});await this.refresh();this.showLog.set(false);this.notice.set(`Set logged. This performance earns ${result.rank}.`);if(this.page()==='ranks')await this.loadTargets();}catch(e){this.error.set(this.message(e));}finally{this.busy.set(false);}}
  async removeLift(id:string){this.busy.set(true);try{await this.api('/lifts/'+id,'DELETE');await this.refresh();this.deleteId.set('');this.notice.set('Set deleted. Your ranks have been recalculated.');}catch(e){this.error.set(this.message(e));}finally{this.busy.set(false);}}
  async saveProfile(){this.busy.set(true);this.error.set('');try{this.user.set(await this.api<User>('/me','PUT',{name:this.profileName,bodyweight:this.profileWeight,standard:this.profileStandard}));await this.refresh();this.notice.set('Profile updated. New sets will use these settings.');}catch(e){this.error.set(this.message(e));}finally{this.busy.set(false);}}
}
bootstrapApplication(App).catch(console.error);
