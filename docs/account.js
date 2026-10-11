'use strict';
(() => {
  const field = id => document.getElementById(id);
  let config, busy = false;
  const status = message => { field('status').textContent = message; };
  const enable = () => ['delete', 'send-reset', 'reset'].forEach(id => {field(id).disabled = busy || !config;});
  const call = async (path, method, body, bearer) => {
    const response = await fetch(config.url + path, {
      method, headers: { apikey: config.publishableKey, 'Content-Type': 'application/json', ...(bearer ? {Authorization: 'Bearer ' + bearer} : {}) },
      body: JSON.stringify(body), redirect: 'error', credentials: 'omit', cache: 'no-store', signal: AbortSignal.timeout(20000),
    });
    if (!response.ok) throw Error(response.status === 429 ? 'Too many attempts. Please wait and try again.' : 'Request unsuccessful. Check your details and connection, or request a new code.');
    const text = await response.text();return text ? JSON.parse(text) : {};
  };
  const run = async work => {if (busy || !config) return;busy = true;enable();status('Contacting account service…');try {await work();} catch(error) {status(error.message || 'Service unavailable. Try again.');} finally {busy = false;enable();}};
  field('delete-form').addEventListener('submit', event => {event.preventDefault();run(async () => {
    if(field('confirmation').value !== 'DELETE') throw Error('Type DELETE to confirm.');
    const password = field('password').value;field('password').value = '';
    // Tokens stay in this function's memory, never in cookies or browser storage.
    const session = await call('/auth/v1/token?grant_type=password', 'POST', {email: field('email').value.trim(), password});
    try {
      await call('/functions/v1/delete-account', 'POST', {password}, session.access_token);
      field('delete-form').reset();status('Your account was deleted. Clear app storage on your devices and remove exported files separately.');
    } catch(error) {
      try {await call('/auth/v1/logout?scope=local','POST',{},session.access_token);} catch {}
      throw error;
    }
  });});
  field('send-reset').addEventListener('click', () => run(async () => {
    if (!field('email').checkValidity() || !field('email').value) throw Error('Enter a valid email address above.');
    await call('/auth/v1/recover','POST',{email:field('email').value.trim()});status('If eligible, a reset code will arrive by email. Check your inbox and spam folder.');
  }));
  field('reset').addEventListener('click', () => run(async () => {
    const password=field('new-password').value,code=field('code').value.trim();
    if(password.length<12||password.length>128||!/^\d{6,10}$/.test(code))throw Error('Enter the email code and a password of 12–128 characters.');
    field('new-password').value='';field('code').value='';
    const session=await call('/auth/v1/verify','POST',{email:field('email').value.trim(),token:code,type:'recovery'});
    try {await call('/auth/v1/user','PUT',{password},session.access_token);status('Password reset. Enter the new password above to delete your account.');}
    finally {await call('/auth/v1/logout?scope=global','POST',{},session.access_token);}
  }));
  fetch('account-config.json',{cache:'no-store'}).then(response=>{if(!response.ok)throw Error();return response.json();}).then(value=>{
    if(!/^https:\/\/[a-z0-9-]+\.supabase\.co$/.test(value.url)||!/^sb_publishable_[A-Za-z0-9_-]+$/.test(value.publishableKey))throw Error();
    config=value;enable();status('Account deletion requires your current password.');
  }).catch(()=>status('Accounts are not configured on this website yet. If your app already supports accounts, use Account → Delete account permanently.'));
})();
