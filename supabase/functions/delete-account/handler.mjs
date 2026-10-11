// No client-supplied user ID is trusted. Administrative credentials stay in the function environment.
export function createHandler({ url, adminKey, origin, request }) {
  return async (req) => {
    const headers = {
      'Content-Type': 'application/json', 'Cache-Control': 'no-store',
      'Access-Control-Allow-Origin': origin, 'Vary': 'Origin',
      'Access-Control-Allow-Headers': 'authorization, apikey, content-type',
      'Access-Control-Allow-Methods': 'POST, OPTIONS',
    };
    const reply = (status, message) => new Response(JSON.stringify({ message }), { status, headers });
    if (req.headers.has('origin') && req.headers.get('origin') !== origin) return reply(403, 'Origin not allowed');
    if (req.method === 'OPTIONS') return new Response(null, { status: 204, headers });
    if (req.method !== 'POST') return reply(405, 'Use POST');
    if (!url || !adminKey) return reply(503, 'Service unavailable');
    const authorization = req.headers.get('authorization') || '';
    if (!/^Bearer [A-Za-z0-9._-]+$/.test(authorization)) return reply(401, 'Sign in required');
    try {
      const text = await req.text();
      if (text.length > 2048) return reply(413, 'Request too large');
      let body;
      try { body = JSON.parse(text); } catch { return reply(400, 'Invalid request'); }
      if (typeof body?.password !== 'string' || body.password.length === 0 || body.password.length > 128 || Object.keys(body).some(k => k !== 'password'))
        return reply(400, 'Current password required');
      const authRequest = (path, method, token, body) => request(url + '/auth/v1' + path, {
        method, headers: { apikey: adminKey, Authorization: token, 'Content-Type': 'application/json' },
        ...(body === undefined ? {} : { body: JSON.stringify(body) }),
        signal: AbortSignal.timeout(15000),
      });
      const identity = await authRequest('/user', 'GET', authorization);
      if (!identity.ok) return reply(identity.status >= 500 ? 503 : 401, 'Sign in required');
      const user = await identity.json();
      if (!/^[0-9a-f-]{36}$/i.test(user.id || '') || !user.email || !user.email_confirmed_at)
        return reply(401, 'Confirmed account required');
      const reauth = await authRequest('/token?grant_type=password', 'POST', '', { email: user.email, password: body.password });
      if (!reauth.ok) return reply(reauth.status === 429 ? 429 : 401, 'Unable to verify account');
      const fresh = await reauth.json();
      if (fresh.user?.id !== user.id || !fresh.access_token) return reply(401, 'Unable to verify account');
      // Revoke refresh sessions before hard deletion; access JWTs may live until their expiry.
      const revoked = await authRequest('/logout?scope=global', 'POST', 'Bearer ' + fresh.access_token, {});
      if (!revoked.ok) return reply(503, 'Unable to revoke sessions; try again');
      const removed = await authRequest('/admin/users/' + encodeURIComponent(user.id), 'DELETE', 'Bearer ' + adminKey);
      if (!removed.ok) return reply(503, 'Unable to delete account; try again');
      return reply(200, 'Account deleted');
    } catch {
      // Never log a request, password, token, or upstream response body.
      return reply(503, 'Service unavailable; try again');
    }
  };
}
