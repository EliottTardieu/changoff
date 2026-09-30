import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import ts from '../frontend/node_modules/typescript/lib/typescript.js';
import {parseTemplate} from '../frontend/node_modules/@angular/compiler/fesm2022/compiler.mjs';
const root=new URL('../',import.meta.url);
const read=p=>readFileSync(new URL(p,root),'utf8');
const FR=JSON.parse(read('frontend/src/locales/fr.ts').split(' = ')[1].trim().replace(/;$/,''));
for(const [key,value] of Object.entries(FR))assert.deepEqual([...key.matchAll(/\{\d+\}/g)].map(m=>m[0]).sort(),[...value.matchAll(/\{\d+\}/g)].map(m=>m[0]).sort(),'Placeholders: '+key);
const missing=new Set();
function expression(node){
  if(!node||typeof node!=='object')return;
  if(node.constructor.name==='BindingPipe'&&node.name==='t'){
    const inspect=n=>{if(!n||typeof n!=='object')return;if(n.constructor.name==='LiteralPrimitive'&&typeof n.value==='string'&&/[A-Za-z]/.test(n.value)&&!FR[n.value]&&!FR[n.value.trim()])missing.add(n.value);if(n.constructor.name==='Conditional'){inspect(n.trueExp);inspect(n.falseExp);}};
    inspect(node.exp);
  }
  for(const [key,value] of Object.entries(node))if(!['sourceSpan','span','keySpan','nameSpan','valueSpan'].includes(key)){if(Array.isArray(value))value.forEach(expression);else if(value&&typeof value==='object')expression(value);}
}
for(const p of ['frontend/src/app.html','frontend/src/wod.html','frontend/src/cardio.html','frontend/src/progress.ts']){
  const text=read(p),template=p.endsWith('.ts')?text.match(/template: `([\s\S]*?)`/)[1]:text;
  const parsed=parseTemplate(template,p);assert.equal(parsed.errors,null);expression(parsed.nodes);
}
assert.deepEqual([...missing].filter(k=>!['kg','km/h','Alex Morgan'].includes(k)),[],'Every literal passed to the translation pipe has a French translation');
const source=ts.transpileModule(read('frontend/src/i18n.ts'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022,experimentalDecorators:true}}).outputText;
function translations(lang){const ctx={exports:{},require:name=>name==='@angular/core'?{Pipe:()=>()=>{}}:{FR},location:{pathname:`/${lang}/wods/history`},document:{documentElement:{}}};vm.runInNewContext(source,ctx);assert.equal(ctx.document.documentElement.lang,lang);return ctx.exports;}
const fr=translations('fr'),en=translations('en');
assert.equal(fr.translate('Silver 2'),'Argent 2');
assert.equal(fr.translate('Keep showing up, {0}','Running'),'Gardez le rythme, Running');
for(const [source,target] of Object.entries(FR).filter(([key])=>/^(For |Complete |Only |Coverage in this WOD: )/.test(key)&&key.includes('{0}'))){
  const values=source.startsWith('Coverage')?['quads, chest']:['15','20'];
  const input=source.replace(/\{(\d+)\}/g,(_,i)=>values[i]);
  const expected=target.replace(/\{(\d+)\}/g,(_,i)=>source.startsWith('Coverage')?'Quadriceps, Pectoraux':values[i]);
  assert.equal(fr.translate(input),expected,input);assert.equal(en.translate(input),input);
}
for(const exercise of JSON.parse(read('backend/src/main/resources/exercises.json'))){
  for(const key of ['name','group','note'])if(exercise[key])assert.ok(FR[exercise[key]],'Exercise catalog: '+exercise[key]);
}
for(const line of read('backend/src/main/resources/db/migration/V2__wod_workshop.sql').split('\n')){
  if(line.startsWith('INSERT INTO wod_exercise VALUES')){
    const values=[...line.matchAll(/'((?:[^']|'')*)'/g)].map(m=>m[1].replaceAll("''","'"));
    for(const value of [values[1],values.at(-1)])assert.ok(FR[value],'WOD movement: '+value);
  }
  if(/^INSERT INTO (wod_level|movement_pattern|equipment|muscle_group|wod_type|wod_stimulus) VALUES/.test(line)){
    for(const match of line.matchAll(/\((?:'[^']*'|\d+),'([^']*)'\)/g))assert.ok(FR[match[1]],'WOD choice: '+match[1]);
  }
}
console.log('PASS i18n: template keys, placeholder consistency, rank names, English fallback, all stored WOD instruction formats and strength/WOD catalogs.');
