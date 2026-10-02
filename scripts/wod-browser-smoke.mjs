import { chromium } from '../frontend/node_modules/playwright/index.mjs';
import assert from 'node:assert/strict';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
const base = process.env.CHANGOFF_URL || 'http://localhost:8080';
const browser = await chromium.launch({ headless: true });
const errors = [];
const headers = { 'X-Requested-With': 'changoff' };
const today = new Date().toLocaleDateString('en-CA');
const planned = new Date();
planned.setDate(planned.getDate() + 7);
const future = planned.toLocaleDateString('en-CA');
const ownerContext = await browser.newContext({ viewport: { width: 1440, height: 1050 } });
const partnerContext = await browser.newContext({ viewport: { width: 1440, height: 1050 } });
const suffix = Date.now();
async function account(context, name, email) {
  const r = await context.request.post(base + '/api/auth/register', {
    headers,
    data: { name, email, password: 'wod-browser-password', bodyweight: 75, standard: 'male' },
  });
  assert.equal(r.status(), 201, await r.text());
  return r.json();
}
async function workshop(page) {
  await page.goto(base);
  await page.getByRole('navigation').getByRole('button', { name: 'WOD workshop' }).click();
  await page.getByRole('heading', { name: 'Build your session', exact: true }).waitFor();
  await page.getByLabel('Workout format').waitFor();
}
try {
  const owner = await account(ownerContext, 'WOD Owner', `wod-browser-owner-${suffix}@example.com`);
  const partner = await account(
    partnerContext,
    'WOD Partner',
    `wod-browser-partner-${suffix}@example.com`,
  );
  const page = await ownerContext.newPage(),
    other = await partnerContext.newPage();
  page.on('pageerror', (e) => errors.push(e.message));
  other.on('pageerror', (e) => errors.push(e.message));
  await workshop(page);
  await page.getByLabel('Workout format').selectOption('EMOM');
  await page.getByLabel('Experience level').selectOption({ label: 'Initiated' });
  await page.getByLabel('Duration / time cap (min)').fill('12');
  await page.getByLabel('Movements', { exact: true }).fill('3');
  await page.getByLabel('Planned date', { exact: true }).fill(future);
  const equipment = page.locator('.wod-options').first();
  for (const check of await equipment.getByRole('checkbox').all()) await check.uncheck();
  await page.getByText('Not today · temporary bans', { exact: false }).click();
  await page.getByRole('checkbox', { name: 'Quads', exact: true }).check();
  const generated = page.waitForResponse(
    (r) => r.url().endsWith('/api/wods/generate') && r.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Generate my WOD' }).click();
  const generation = await (await generated).json();
  assert.equal(generation.config.type, 'EMOM');
  assert.equal(generation.items.length, 3);
  for (const item of generation.items) {
    assert.equal(item.exercise.equipment.length, 0);
    assert.ok(!item.exercise.muscles.includes('quads'));
    assert.ok(item.quantity * item.exercise.secondsPerUnit <= 40);
  }
  await page.locator('.prescription-item').nth(2).waitFor();
  const variation = generation.items.findIndex((i) => i.alternatives.length > 0);
  if (variation >= 0) {
    const changed = page.waitForResponse((r) => r.url().endsWith('/api/wods/generate'));
    await page
      .locator('.prescription-item')
      .nth(variation)
      .getByLabel('Need a variation?')
      .selectOption(generation.items[variation].alternatives[0].exerciseId);
    assert.equal((await changed).status(), 200);
    await page.getByRole('status').filter({ hasText: 'Movement swapped' }).waitFor();
  }
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: join(tmpdir(), 'changoff-wod-generator.png'), fullPage: true });
  await page.getByLabel('WOD name', { exact: true }).fill('Browser partner session');
  const saving = page.waitForResponse(
    (r) => r.url().endsWith('/api/wods') && r.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Save WOD to history' }).click();
  const saved = await (await saving).json();
  assert.ok(saved.id);
  assert.equal(saved.scheduled_on, future);
  await page
    .getByRole('heading', { name: 'Browser partner session', exact: true, level: 2 })
    .waitFor();
  await page.getByLabel('Add an existing user by email').fill(partner.email);
  await page.getByRole('button', { name: 'Add participant', exact: true }).click();
  await page.getByRole('status').filter({ hasText: 'Participant added' }).waitFor();
  await page.getByRole('button', { name: 'Edit planned workout' }).click();
  await page.getByLabel('WOD name', { exact: true }).fill('Browser partner session edited');
  await page.getByRole('button', { name: 'Update planned WOD' }).click();
  await page
    .getByRole('heading', { name: 'Browser partner session edited', exact: true, level: 2 })
    .waitFor();
  await workshop(other);
  await other.getByRole('tab', { name: 'History & plans' }).click();
  const detailResponse = other.waitForResponse((r) => r.url().endsWith('/api/wods/' + saved.id));
  await other
    .locator('.history-card')
    .filter({ hasText: 'Browser partner session edited' })
    .click();
  const loaded = await detailResponse;
  assert.equal(loaded.status(), 200, await loaded.text());
  await other.locator('.saved-wod').waitFor();
  await other.getByLabel('Status', { exact: false }).selectOption('completed');
  await other.getByLabel('Completed on', { exact: true }).fill(today);
  await other.getByLabel('Time / duration (seconds)').fill('360');
  await other.getByLabel('Full rounds / cycles').fill('3');
  await other.getByLabel('Extra reps', { exact: true }).fill('2');
  await other.getByLabel('Perceived effort (1–10)').fill('6');
  await other.getByLabel('Personal notes').fill('Private partner result');
  await other.getByRole('button', { name: 'Save my result & schedule' }).click();
  await other.getByRole('status').filter({ hasText: 'Your schedule and result' }).waitFor();
  await other.getByRole('tab', { name: 'Statistics', exact: true }).click();
  assert.equal(
    await other.locator('.wod-stat-cards article').first().locator('strong').innerText(),
    '1',
  );
  assert.equal(
    await other.locator('.wod-stat-cards article').nth(1).locator('strong').innerText(),
    '6',
  );
  const ownerStats = await (await ownerContext.request.get(base + '/api/wods/statistics')).json();
  assert.equal(ownerStats.completed, 0);
  const ownerDetail = await (await ownerContext.request.get(base + '/api/wods/' + saved.id)).json();
  assert.equal(ownerDetail.notes, '');
  assert.equal(ownerDetail.status, 'planned');
  assert.equal(ownerDetail.canEdit, false);
  await other.setViewportSize({ width: 390, height: 844 });
  await other.screenshot({ path: join(tmpdir(), 'changoff-wod-mobile.png'), fullPage: true });
  assert.ok(
    await other.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
    'Mobile statistics must not overflow',
  );
  await other.getByRole('tab', { name: 'History & plans' }).click();
  await other.getByRole('button', { name: 'Remove myself from this WOD' }).click();
  await other.getByRole('button', { name: 'Confirm removal', exact: true }).click();
  await other.getByRole('status').filter({ hasText: 'Removed from your WOD history' }).waitFor();
  assert.equal((await partnerContext.request.get(base + '/api/wods/' + saved.id)).status(), 404);
  assert.equal((await ownerContext.request.get(base + '/api/wods/' + saved.id)).status(), 200);
  // Remove only the synthetic WOD. Isolated test accounts remain, as with the original browser smoke test.
  assert.equal(
    (
      await ownerContext.request.delete(base + '/api/wods/' + saved.id + '/participation', {
        headers,
      })
    ).status(),
    204,
  );
  assert.deepEqual(errors, []);
  console.log(
    'PASS WOD browser: hard bans, EMOM, substitutions, planning, editing, sharing, private results, statistics, mobile layout, self-removal and access revocation.',
  );
} catch (e) {
  console.error('Browser errors:', errors);
  for (const context of [ownerContext, partnerContext]) {
    const p = context.pages()[0];
    if (p) {
      console.error((await p.locator('body').innerText()).slice(-7000));
      await p.screenshot({
        path: join(
          tmpdir(),
          context === ownerContext
            ? 'changoff-wod-owner-failure.png'
            : 'changoff-wod-partner-failure.png',
        ),
        fullPage: true,
      });
    }
  }
  throw e;
} finally {
  await browser.close();
}
