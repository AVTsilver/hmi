/* offline cache — HMI fault log app */
const V='hmi-v3';
const FONTS=["app/fonts/ibm-plex-sans-arabic-arabic-400-normal.woff2", "app/fonts/ibm-plex-sans-arabic-arabic-500-normal.woff2", "app/fonts/ibm-plex-sans-arabic-arabic-600-normal.woff2", "app/fonts/ibm-plex-sans-arabic-arabic-700-normal.woff2", "app/fonts/ibm-plex-sans-arabic-latin-400-normal.woff2", "app/fonts/ibm-plex-sans-arabic-latin-500-normal.woff2", "app/fonts/ibm-plex-sans-arabic-latin-600-normal.woff2", "app/fonts/ibm-plex-sans-arabic-latin-700-normal.woff2"];
const ENGINE=["pp/det.b64.txt", "pp/keys.txt", "pp/ort-wasm-simd-threaded.mjs", "pp/ort-wasm-simd-threaded.wasm", "pp/ort.wasm.min.js", "pp/rec.b64.txt", "ocr/eng.b64.txt", "ocr/tesseract-core-lstm.wasm.js", "ocr/tesseract-core-simd-lstm.wasm.js", "ocr/tesseract.min.js", "ocr/worker.min.js"];
const CORE=['./','index.html','manifest.json','app/shim.js','app/fonts.css','lib/html2canvas.min.js','lib/jspdf.umd.min.js','icons/icon-192.png','icons/icon-512.png',...FONTS];
self.addEventListener('install',e=>{e.waitUntil(caches.open(V).then(c=>c.addAll(CORE)).then(()=>self.skipWaiting()))});
self.addEventListener('activate',e=>{e.waitUntil(caches.keys().then(k=>Promise.all(k.filter(x=>x!==V).map(x=>caches.delete(x)))).then(()=>self.clients.claim()));
  /* reading engine (~44MB) is fetched in the background once, so later the whole app works offline */
  caches.open(V).then(c=>ENGINE.reduce((p,u)=>p.then(()=>c.match(u).then(h=>h||c.add(u).catch(()=>{}))),Promise.resolve()))});
self.addEventListener('fetch',e=>{const r=e.request;if(r.method!=='GET'||new URL(r.url).origin!==location.origin)return;
  const isPage=r.mode==='navigate'||/index\.html$/.test(r.url);
  /* page: network first (gets updates), falls back to cache offline. engine files: cache first (big, never change) */
  if(isPage){e.respondWith(fetch(r).then(res=>{const c=res.clone();caches.open(V).then(x=>x.put('index.html',c));return res}).catch(()=>caches.match('index.html')));return}
  e.respondWith(caches.match(r).then(hit=>hit||fetch(r).then(res=>{if(res.ok){const c=res.clone();caches.open(V).then(x=>x.put(r,c))}return res})))});
