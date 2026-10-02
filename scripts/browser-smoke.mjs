import { chromium } from '../frontend/node_modules/playwright/index.mjs';
import assert from 'node:assert/strict';
const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } });
const errors = [];
page.on('pageerror', (e) => errors.push(e.message));
await page.goto(process.env.CHANGOFF_URL || 'http://localhost:8080');
await page.getByRole('button', { name: 'Create account', exact: true }).click();
await page.getByLabel('Your name').fill('Alex Morgan');
await page.getByLabel('Email address').fill(`browser-${Date.now()}@example.com`);
await page.getByLabel('Password', { exact: true }).fill('browser-test-password');
await page.getByRole('button', { name: 'Create account →', exact: true }).click();
await page.getByRole('heading', { name: 'Keep showing up, Alex.' }).waitFor();
assert.equal(await page.locator('.exercise-card').count(), 12);
await page.getByRole('button', { name: '＋ Log a set', exact: true }).click();
await page.getByLabel('Weight (kg)', { exact: true }).fill('75');
await page.getByLabel('Reps', { exact: true }).fill('1');
await page.getByRole('button', { name: 'Save set →' }).click();
await page.getByRole('status').filter({ hasText: 'Silver 1' }).waitFor();
await page
  .getByRole('navigation')
  .getByRole('button', { name: 'Rank ladder', exact: false })
  .click();
await page.locator('.ladder-card').nth(23).waitFor();
assert.equal(await page.locator('.ladder-card').count(), 24);
assert.match(await page.locator('.ladder-card').nth(3).innerText(), /64.3/);
await page.getByLabel('Reps per set').selectOption({ label: '1 reps' });
await page.waitForFunction(() =>
  document.querySelectorAll('.ladder-card')[3]?.textContent.includes('75'),
);
await page.getByRole('button', { name: 'Training log', exact: false }).click();
await page.getByRole('cell', { name: '75 kg × 1', exact: true }).waitFor();
await page.getByRole('button', { name: 'Delete Bench press set' }).click();
await page.getByRole('button', { name: 'Confirm delete' }).click();
await page.getByRole('heading', { name: 'Your first entry is waiting.' }).waitFor();
await page.getByRole('button', { name: 'Overview', exact: false }).click();
await page.setViewportSize({ width: 390, height: 844 });
assert.ok(
  await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
  'No horizontal overflow at mobile size',
);
await page.getByRole('button', { name: '＋ Log a set', exact: true }).click();
await page.getByRole('dialog').waitFor();
await page.keyboard.press('Escape');
await page.getByRole('dialog').waitFor({ state: 'hidden' });
await page
  .getByRole('navigation')
  .getByRole('button', { name: 'My profile', exact: false })
  .click();
await page.getByLabel('Body weight (kg)').fill('80');
await page.getByRole('button', { name: 'Save changes', exact: true }).click();
await page.getByRole('status').filter({ hasText: 'Profile updated' }).waitFor();
await page.getByRole('button', { name: 'Sign out of account', exact: true }).click();
await page.getByRole('heading', { name: 'Welcome back.' }).waitFor();
assert.deepEqual(errors, []);
console.log(
  'PASS browser: registration, dashboard, log set, ranks, rep targets, history deletion, mobile layout, dialog, logout; no browser errors.',
);
await browser.close();
