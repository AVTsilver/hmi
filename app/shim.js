/* Standalone runtime for «سجل أعطال HMI» — م. محمد علي محمد صبيح.
   Replaces the hosted services: data lives in the phone (IndexedDB), files download directly. */
(function(){
  const DBN='hmi-fault-log',ST='docs';let idb=null;const subs=[];
  function open(){return idb||(idb=new Promise((ok,no)=>{const r=indexedDB.open(DBN,1);r.onupgradeneeded=()=>r.result.createObjectStore(ST,{keyPath:'_id'});r.onsuccess=()=>ok(r.result);r.onerror=()=>no(r.error)}))}
  async function tx(mode,fn){const d=await open();return new Promise((ok,no)=>{const t=d.transaction(ST,mode);const s=t.objectStore(ST);let out;Promise.resolve(fn(s)).then(v=>out=v);t.oncomplete=()=>ok(out);t.onerror=()=>no(t.error);t.onabort=()=>no(t.error||new Error('quota'))})}
  const all=()=>tx('readonly',s=>new Promise(ok=>{const r=s.getAll();r.onsuccess=()=>ok(r.result)}));
  async function emit(){const L=(await all()).sort((a,b)=>(b.ts||0)-(a.ts||0));const snap={metadata:{fromCache:false},docs:L.map(o=>{const {_id,...d}=o;return {id:_id,data:()=>d}})};subs.forEach(f=>{try{f(snap)}catch(e){console.error(e)}})}
  const coll={doc:id=>({set:async o=>{await tx('readwrite',s=>s.put({...JSON.parse(JSON.stringify(o)),_id:id}));emit()},delete:async()=>{await tx('readwrite',s=>s.delete(id));emit()}}),
    orderBy:()=>({onSnapshot:(cb,err)=>{subs.push(cb);emit().catch(e=>err&&err(e));return ()=>{}}})};
  const db={collection:()=>coll};
  /* one-time migration of anything saved by an older offline build in localStorage */
  (async()=>{try{if(localStorage.getItem('hmi-migrated'))return;const r=JSON.parse(localStorage.getItem('hmi-recs')||'[]'),n=JSON.parse(localStorage.getItem('hmi-notes')||'[]'),l=JSON.parse(localStorage.getItem('hmi-lims')||'null');
    for(const x of [...r,...n])if(x&&x.id)await coll.doc(x.id).set(x);if(l&&l.length)await coll.doc('limits').set({items:l});localStorage.setItem('hmi-migrated','1')}catch(e){}})();
  const A=window.HMIAndroid;
  const downloads={save:async({filename,data})=>{
    if(A){const b64=await new Promise((ok,no)=>{const r=new FileReader();r.onload=()=>ok(String(r.result).split(',')[1]);r.onerror=no;r.readAsDataURL(data)});
      const res=A.save(filename,b64,data.type||'application/octet-stream');if(res!=='ok')throw new Error(res);return {status:'saved'}}const u=URL.createObjectURL(data);const a=document.createElement('a');a.href=u;a.download=filename;document.body.appendChild(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(u),60000);return {status:'saved'}}};
  if(!window.claude)window.claude={use:async n=>n==='db'?db:n==='user'?{id:async()=>'local'}:n==='downloads'?downloads:null};
  if(navigator.storage&&navigator.storage.persist)navigator.storage.persist().catch(()=>{});
  if(!A&&'serviceWorker' in navigator&&location.protocol!=='file:')window.addEventListener('load',()=>navigator.serviceWorker.register('sw.js').catch(()=>{}));
})();
