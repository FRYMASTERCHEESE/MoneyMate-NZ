import assert from 'node:assert/strict';
import { chromium } from 'playwright';

// Non-destructive checks against the publicly deployed MoneyMate website.
// Uses fresh browser profiles and synthetic LOCAL GUEST data only.
const website = 'https://frymastercheese.github.io/MoneyMate-NZ/';
const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
const failures = [];

async function openSite(page) {
  await page.goto(website, { waitUntil: 'domcontentloaded', timeout: 30_000 });
  await page.locator('#view h1').first().waitFor({ state: 'visible', timeout: 30_000 });
  assert.ok(!(await page.locator('#view').innerText()).includes('Opening your budget...'), 'Website never finished loading');
}

async function saveDialog(page, fields) {
  await page.locator('#editor').waitFor({ state: 'visible', timeout: 10_000 });
  for (const [key, value] of Object.entries(fields)) {
    await page.locator('#editor [name="' + key + '"]').fill(value);
  }
  await page.locator('#submit-modal').click();
  await page.locator('#editor').waitFor({ state: 'hidden', timeout: 15_000 });
}

try {
  const desktop = await browser.newContext({
    viewport: { width: 1280, height: 800 },
    timezoneId: 'Pacific/Auckland',
    serviceWorkers: 'block',
    acceptDownloads: true,
  });
  const page = await desktop.newPage();
  page.on('pageerror', err => failures.push('Desktop: ' + err.message));

  await openSite(page);
  assert.match(await page.locator('#view h1').innerText(), /Your money, made clear/i);
  console.log('PASS: Live website renders from the deployed JavaScript');

  await page.locator('#view [data-action="expense"]').first().click();
  await saveDialog(page, { category: 'Groceries', note: 'Synthetic browser smoke test', amount: '12.34' });
  await page.locator('#nav-side [data-page="transactions"]').click();
  await page.locator('#transaction-records .entry').first().waitFor({ timeout: 10_000 });
  assert.equal(await page.locator('#transaction-records .entry').count(), 1);
  assert.match(await page.locator('#transaction-records').innerText(), /Synthetic browser smoke test/);
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.locator('#transaction-records .entry').first().waitFor({ timeout: 20_000 });
  assert.equal(await page.locator('#transaction-records .entry').count(), 1, 'Refresh duplicated or lost local transaction');
  console.log('PASS: Local expense entry persists after full reload without duplicates');

  await page.locator('#nav-side [data-page="budgets"]').click();
  await page.locator('#view [data-action="budget"]').click();
  await saveDialog(page, { category: 'Groceries', amount: '50.00' });
  assert.match(await page.locator('#view').innerText(), /Groceries/);
  assert.match(await page.locator('#view').innerText(), /12\.34/);
  console.log('PASS: Budget reflects a previously recorded expense');

  await page.locator('#nav-side [data-page="goals"]').click();
  await page.locator('#view [data-action="goal"]').click();
  await saveDialog(page, { name: 'Test holiday', amount: '500' });
  assert.match(await page.locator('#view').innerText(), /Test holiday/);
  console.log('PASS: Savings goal is created and displayed');

  await page.locator('#nav-side [data-page="bills"]').click();
  await page.locator('#view [data-action="bill"]').click();
  await saveDialog(page, { name: 'Test power', amount: '45' });
  assert.match(await page.locator('#view').innerText(), /Test power/);
  console.log('PASS: Unpaid bill is created and displayed without sending a payment');

  await page.locator('#nav-side [data-page="transactions"]').click();
  await page.locator('#view [data-action="recurring"]').click();
  await saveDialog(page, { category: 'Future test recurring', amount: '9', date: '2070-01-01' });
  assert.match(await page.locator('#view').innerText(), /Future test recurring/);
  console.log('PASS: Future repeating entry can be scheduled without billing');

  await page.locator('#nav-side [data-page="export"]').click();
  const downloadPromise = page.waitForEvent('download', { timeout: 12_000 });
  await page.locator('#view [data-action="backup"]').click();
  const download = await downloadPromise;
  assert.match(download.suggestedFilename(), /\.json$/i);
  console.log('PASS: Full JSON backup downloads');

  await page.locator('#nav-side [data-page="account"]').click();
  await page.locator('#signin-form').waitFor({ timeout: 15_000 });
  assert.equal(await page.locator('#signup-form').count(), 1);
  assert.ok(await page.locator('#view [data-action="google-login"]').count() > 0);
  assert.equal(await page.locator('[data-page="admin"]:visible').count(), 0, 'Public admin navigation is visible');
  console.log('PASS: Email and Google account controls are present; admin links are hidden');

  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.locator('#signin-form').waitFor({ timeout: 20_000 });
  await page.locator('#nav-side [data-page="transactions"]').click();
  await page.locator('#transaction-records .entry').first().waitFor({ timeout: 10_000 });
  assert.equal(await page.locator('#transaction-records .entry').count(), 1);
  console.log('PASS: Guest financial data remains after switching sections and refreshing');

  const mobile = await browser.newContext({
    viewport: { width: 390, height: 844 },
    isMobile: true,
    hasTouch: true,
    timezoneId: 'Pacific/Auckland',
    serviceWorkers: 'block',
  });
  const phone = await mobile.newPage();
  phone.on('pageerror', err => failures.push('Mobile: ' + err.message));
  await openSite(phone);
  assert.ok(await phone.locator('#nav-mobile').isVisible());
  await phone.locator('#nav-mobile [data-page="budgets"]').click();
  assert.match(await phone.locator('#view h1').innerText(), /Budgets/);
  await phone.locator('#nav-mobile .mobile-more summary').click();
  await phone.locator('#nav-mobile .mobile-more [data-page="account"]').click();
  await phone.locator('#signin-form').waitFor({ timeout: 10_000 });
  const overflows = await phone.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 3);
  assert.equal(overflows, false, 'Mobile layout exceeds viewport width');
  console.log('PASS: Mobile navigation, account forms and 390px viewport');

  assert.deepEqual(failures, [], 'Uncaught browser JavaScript errors detected');
  await mobile.close();
  await desktop.close();
  console.log('RESULT: All safe production guest-browser smoke checks passed');
} finally {
  await browser.close();
}
