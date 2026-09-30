import {chromium} from '../frontend/node_modules/playwright/index.mjs';
import assert from 'node:assert/strict';

const browser=await chromium.launch({headless:true});
try {
  const page=await browser.newPage({viewport:{width:1440,height:1100},timezoneId:'Europe/Paris'});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  const base=process.env.CHANGOFF_URL||'http://localhost:8080';
  for(let attempt=0;attempt<30;attempt++) {
    const health=await page.request.get(base+'/api/health').catch(()=>null);
    if(health?.ok())break;
    if(attempt===29)throw new Error('Application did not become healthy');
    await page.waitForTimeout(1000);
  }
  const headers={'X-Requested-With':'changoff'};
  const email=`progress-${Date.now()}@example.com`;
  const response=await page.request.post(base+'/api/auth/register',{headers,data:{name:'Progress Tester',email,password:'progress-test-password',bodyweight:75,standard:'male'}});
  assert.equal(response.status(),201);
  const ids=[];
  for(const [weight,reps,performedOn] of [[40,12,'2026-01-01'],[50,5,'2026-01-01'],[60,3,'2026-02-01'],[45,20,'2026-03-01']]) {
    const r=await page.request.post(base+'/api/lifts',{headers,data:{exercise:'bench-press',weight,reps,performedOn}});
    assert.equal(r.status(),201,await r.text());ids.push((await r.json()).id);
  }
  await page.goto(base);
  await page.getByRole('button',{name:'Bench press progress',exact:true}).waitFor();
  assert.equal(await page.locator('app-progress').count(),0,'Graph is not embedded in the overview');
  assert.equal(await page.getByRole('button',{name:'View progress',exact:false}).count(),0);
  await page.getByRole('button',{name:'Bench press progress',exact:true}).focus();
  await page.keyboard.press('Enter');
  await page.getByRole('dialog',{name:'Bench press',exact:true}).waitFor();
  await page.keyboard.press('Escape');
  await page.getByRole('dialog').waitFor({state:'hidden'});
  assert.equal(await page.getByRole('button',{name:'Bench press progress',exact:true}).evaluate(n=>n===document.activeElement),true);

  await page.getByRole('button',{name:'Bench press progress',exact:true}).click();
  await page.getByRole('dialog',{name:'Bench press',exact:true}).waitFor();
  assert.match(await page.locator('.personal-bests').innerText(),/60 kg × 3 reps/);
  assert.match(await page.locator('.personal-bests').innerText(),/20 reps at 45 kg/);
  assert.equal(await page.locator('.chart-point').count(),3);
  assert.match(await page.locator('.training-chart').textContent(),/Jan 1, 2026/,'Date-only records keep their calendar day in Europe/Paris');
  assert.deepEqual(await page.locator('.chart-point').evaluateAll(nodes=>nodes.map(n=>n.getAttribute('aria-label'))),[
    '2026-01-01: 50 kg × 5 reps','2026-02-01: 60 kg × 3 reps','2026-03-01: 45 kg × 20 reps'
  ]);
  await page.getByLabel('Plot').selectOption('reps');
  await page.waitForFunction(()=>document.querySelector('.chart-point')?.getAttribute('aria-label')==='2026-01-01: 40 kg × 12 reps');
  await page.getByLabel('Compare at weight (kg)').fill('45');
  await page.waitForFunction(()=>document.querySelectorAll('.chart-point').length===1);
  await page.getByLabel('Compare at weight (kg)').fill('999');
  await page.getByRole('heading',{name:'No sets match these filters.'}).waitFor();
  await page.getByLabel('Compare at weight (kg)').fill('');
  await page.waitForFunction(()=>document.querySelectorAll('.chart-point').length===3);
  await page.getByRole('button',{name:'Close progress'}).click();
  await page.getByRole('navigation').getByRole('button',{name:'My profile'}).click();
  await page.getByRole('checkbox',{name:'Enable ranks'}).uncheck();
  await page.getByRole('button',{name:'Save changes',exact:true}).click();
  await page.getByRole('status').filter({hasText:'Profile updated'}).waitFor();
  await page.reload();
  // Locale routes now retain the current page across reloads.
  await page.getByRole('navigation').getByRole('button',{name:'Overview'}).click();
  await page.getByRole('button',{name:'Bench press progress',exact:true}).click();
  await page.getByRole('dialog',{name:'Bench press',exact:true}).waitFor();
  assert.equal(await page.getByRole('navigation').getByRole('button',{name:'Rank ladder'}).count(),0);
  assert.equal(await page.locator('.progress-rank,.rank-row').count(),0);
  assert.equal(await page.locator('.chart-point').count(),3);
  assert.equal((await (await page.request.get(base+'/api/me')).json()).ranks_enabled,false);
  await page.getByRole('button',{name:'＋ Log this exercise',exact:true}).click();
  await page.getByLabel('Weight (kg)',{exact:true}).fill('55');
  await page.getByLabel('Reps',{exact:true}).fill('15');
  await page.getByRole('button',{name:'Save set →'}).click();
  await page.getByRole('status').filter({hasText:'Set logged. Your progress has been updated.'}).waitFor();
  await page.getByRole('navigation').getByRole('button',{name:'Training log'}).click();
  await page.getByRole('cell',{name:'55 kg × 15',exact:true}).waitFor();
  await page.getByLabel('Filter exercise').selectOption('squat');
  await page.getByRole('heading',{name:'No sets for this exercise yet.'}).waitFor();
  await page.getByRole('navigation').getByRole('button',{name:'My profile'}).click();
  await page.getByRole('checkbox',{name:'Enable ranks'}).check();
  await page.getByRole('button',{name:'Save changes',exact:true}).click();
  await page.getByRole('status').filter({hasText:'Profile updated'}).waitFor();
  await page.getByRole('navigation').getByRole('button',{name:'Overview'}).click();
  await page.getByRole('button',{name:'Bench press progress',exact:true}).click();
  await page.locator('.progress-rank').waitFor();
  assert.equal(await page.locator('.chart-point').count(),4);
  // Removing the heaviest set must recalculate the personal best and graph.
  assert.equal((await page.request.delete(base+'/api/lifts/'+ids[2],{headers})).status(),204);
  await page.reload();
  await page.getByRole('button',{name:'Bench press progress',exact:true}).click();
  await page.getByRole('dialog',{name:'Bench press',exact:true}).waitFor();
  assert.match(await page.locator('.personal-bests').innerText(),/55 kg × 15 reps/);
  await page.getByRole('button',{name:'Close progress'}).click();
  await page.getByRole('button',{name:'Pull-ups progress',exact:true}).click();
  await page.getByRole('heading',{name:'Your progress starts with one set.'}).waitFor();
  await page.getByRole('button',{name:'＋ Log this exercise',exact:true}).click();
  await page.getByLabel('Added weight (kg)').fill('0');
  await page.getByLabel('Reps',{exact:true}).fill('8');
  await page.getByRole('button',{name:'Save set →'}).click();
  await page.getByRole('dialog').waitFor({state:'hidden'});
  await page.getByRole('button',{name:'Pull-ups progress',exact:true}).click();
  assert.match(await page.locator('.personal-bests').innerText(),/0 kg × 8 reps/);
  assert.equal(await page.locator('.chart-point').count(),1);
  assert.ok(!/NaN|Infinity/.test(await page.locator('.training-chart').evaluate(node=>node.outerHTML)));
  await page.screenshot({path:'/tmp/changoff-progress-desktop.png',fullPage:true});
  await page.setViewportSize({width:390,height:844});
  await page.screenshot({path:'/tmp/changoff-progress-mobile.png',fullPage:true});
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'No mobile horizontal overflow');
  await page.mouse.click(2,2);
  await page.getByRole('dialog').waitFor({state:'hidden'});
  await page.locator('.exercise-card').first().getByRole('button',{name:'＋ Log set'}).click();
  await page.getByRole('dialog',{name:'Log a set',exact:true}).waitFor();
  assert.equal(await page.locator('.progress-modal').count(),0);
  await page.keyboard.press('Escape');
  assert.deepEqual(errors,[]);
  console.log('PASS progress: daily bests, paired records, graph filters, preference persistence, rank visibility, high-rep logging, history filter, deletion, zero-load graph, mobile layout.');
} finally {await browser.close();}
