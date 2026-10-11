import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createHandler } from './handler.mjs';
const id = '11111111-1111-4111-8111-111111111111';
const user = { id, email: 'test@example.invalid', email_confirmed_at: '2026-10-12' };
function harness(responses = []) {
  const calls = [];
  const handler = createHandler({ url: 'https://example.supabase.co', adminKey: 'server-only', origin: 'https://frymastercheese.github.io',
    request: async (url, options) => { calls.push({ url, ...options }); const next = responses.shift(); if (!next) throw Error('unexpected call'); return new Response(JSON.stringify(next.body || {}), { status: next.status || 200 }); } });
  const invoke = (body = { password: 'valid password' }, extra = {}) => handler(new Request('https://example.supabase.co/functions/v1/delete-account', { method: 'POST', headers: { Authorization: 'Bearer existing-token', ...extra }, body: JSON.stringify(body) }));
  return { handler, calls, invoke };
}
test('rejects missing bearer token without contacting Auth', async () => {
  const h = harness();const response = await h.handler(new Request('https://test', { method: 'POST' }));assert.equal(response.status, 401);assert.equal(h.calls.length, 0);
});
test('rejects foreign web origin', async () => { const h = harness();assert.equal((await h.invoke(undefined, { Origin: 'https://evil.invalid' })).status, 403);assert.equal(h.calls.length, 0); });
test('CORS preflight and method restrictions', async () => { const h = harness();assert.equal((await h.handler(new Request('https://test', { method: 'OPTIONS' }))).status, 204);assert.equal((await h.handler(new Request('https://test'))).status, 405); });
test('rejects client supplied target ID', async () => { const h = harness();assert.equal((await h.invoke({ password: 'abc', user_id: id })).status, 400);assert.equal(h.calls.length, 0); });
test('deleted or invalid user cannot reuse an old JWT', async () => { const h = harness([{ status: 401 }]);assert.equal((await h.invoke()).status, 401);assert.equal(h.calls.length, 1); });
test('requires confirmed email', async () => { const h = harness([{ body: { ...user, email_confirmed_at: null } }]);assert.equal((await h.invoke()).status, 401);assert.equal(h.calls.length, 1); });
test('wrong password never reaches deletion', async () => { const h = harness([{ body: user }, { status: 400 }]);assert.equal((await h.invoke()).status, 401);assert.equal(h.calls.length, 2); });
test('reauthentication must match the bearer identity', async () => { const h = harness([{ body: user }, { body: { user: { id: 'someone-else' }, access_token: 'fresh' } }]);assert.equal((await h.invoke()).status, 401);assert.equal(h.calls.length, 2); });
test('revocation failure prevents deletion', async () => { const h = harness([{ body: user }, { body: { user, access_token: 'fresh' } }, { status: 500 }]);assert.equal((await h.invoke()).status, 503);assert.equal(h.calls.length, 3); });
test('hard deletes only authenticated ID after revoking refresh sessions', async () => {
  const h = harness([{ body: user }, { body: { user, access_token: 'fresh' } }, {}, {}]);assert.equal((await h.invoke()).status, 200);
  assert.ok(h.calls[2].url.endsWith('/logout?scope=global'));assert.equal(h.calls[2].headers.Authorization, 'Bearer fresh');
  assert.ok(h.calls[3].url.endsWith('/admin/users/' + id));assert.equal(h.calls[3].method, 'DELETE');assert.equal(h.calls[3].headers.Authorization, 'Bearer server-only');
});
test('backend failure is not reported as deletion success', async () => { const h = harness([{ body: user }, { body: { user, access_token: 'fresh' } }, {}, { status: 500 }]);assert.equal((await h.invoke()).status, 503); });
test('malformed and oversized input is rejected', async () => {
  const h = harness();assert.equal((await h.handler(new Request('https://test', {method:'POST',headers:{Authorization:'Bearer token'},body:'{' }))).status,400);
  assert.equal((await h.invoke({password:'x'.repeat(3000)})).status,413);assert.equal(h.calls.length,0);
});
