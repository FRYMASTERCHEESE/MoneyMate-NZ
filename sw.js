const CACHE="moneymate-nz-web-v7-points-banks";
const STATIC=["./","./index.html","./web.css","./web-app.js","./web-core.mjs","./auth-session.mjs","./sync-policy.mjs","./wallet.svg","./app-theme.json"];
self.addEventListener("install",event=>event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(STATIC)).then(()=>self.skipWaiting())));
self.addEventListener("activate",event=>event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(key=>key.startsWith("moneymate-nz-web-")&&key!==CACHE).map(key=>caches.delete(key)))).then(()=>self.clients.claim())));
self.addEventListener("fetch",event=>{
 const url=new URL(event.request.url);
 if(event.request.method!=="GET"||url.origin!==self.location.origin)return;
 // Never intercept Supabase authentication, financial exports or private information.
 if(url.pathname.endsWith("/account-config.json"))return;
 if(event.request.mode==="navigate")event.respondWith(fetch(event.request).catch(()=>caches.match("./index.html")));
 else event.respondWith(caches.match(event.request).then(cached=>cached||fetch(event.request)));
});
