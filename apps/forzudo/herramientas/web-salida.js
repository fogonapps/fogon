// Corre la lógica ORIGINAL de index.html con DOM simulado y fecha fija; imprime resultados en JSON.
const fs=require('fs'), vm=require('vm');
const html=fs.readFileSync(process.argv[2],'utf8'); const prueba=JSON.parse(fs.readFileSync(process.argv[3]));
const body=html.match(/<script id="body-model"[^>]*>([\s\S]*?)<\/script>/)[1];
const photo=html.match(/<script id="photo-map"[^>]*>([\s\S]*?)<\/script>/)[1];
const main=html.match(/<script>\s*\/\* ===== Rutina[\s\S]*?<\/script>/)[0].replace(/^<script>/,'').replace(/<\/script>$/,'');
const FIX=new Date(2026,9,11,12,0,0).getTime();
const RealDate=Date;
class FDate extends RealDate{ constructor(...a){ if(a.length) super(...a); else super(FIX); } static now(){ return FIX; } }
const dummy=()=>new Proxy(function(){},{get:(t,k)=>{ if(k==='textContent'||k==='innerHTML'||k==='value') return ''; if(k==='classList') return {add(){},remove(){},toggle(){},contains(){return false}}; if(k==='style') return {}; return dummy(); },set:()=>true,apply:()=>dummy()});
const doc={getElementById:id=>id==='body-model'?{textContent:body}:id==='photo-map'?{textContent:photo}:dummy(),querySelector:()=>dummy(),querySelectorAll:()=>[],addEventListener(){},createElement:()=>dummy(),body:dummy(),visibilityState:'hidden'};
const store={'gym-angel-v1':JSON.stringify(prueba.estado)};
const ctx={document:doc,window:{addEventListener(){},open(){},scrollTo(){}},localStorage:{getItem:k=>store[k]??null,setItem:(k,v)=>{store[k]=v}},navigator:{onLine:false},location:{protocol:'file:'},Date:FDate,Math,JSON,console,setTimeout:()=>0,clearTimeout(){},setInterval:()=>0,clearInterval(){},confirm:()=>true,fetch:()=>Promise.reject(),URL:{},Blob:function(){},FileReader:function(){},Intl,Number,String,Object,Array,Set,Map,parseFloat,parseInt,isNaN,encodeURIComponent,Promise};
vm.createContext(ctx); vm.runInContext(main,ctx);
const out=vm.runInContext(`(()=>{
 // Mi Gym Fogón ya no usa marcas manuales de semana: se comparan sin ellas.
 S.weekMarks={};
 const o={};
 o.streaks=streaks(); o.diaHoy=todayDay();
 const ws=new Set(); S.sessions.forEach(s=>ws.add(weekStart(s.date))); Object.keys(S.weekMarks).forEach(w=>ws.add(w));
 o.semanas={}; [...ws].sort().forEach(w=>o.semanas[w]=weekDone(w));
 o.weekMarks=S.weekMarks; o.titulos=S.sessions.map(s=>s.title||null);
 o.weekStart=["2026-10-11","2026-10-05","2026-10-06","2026-03-01","2026-01-01"].map(weekStart);
 o.monthWeeks=["2026-02","2026-03","2026-10","2027-01"].map(monthWeeks);
 o.weekLabel=["2026-09-28","2026-10-05"].map(weekLabel);
 o.fk=[20,12.5,7.25,0.333333,1234.5,null,80.20000001].map(fk);
 o.fmtVol=[1234.4,0,98765.6].map(fmtVol);
 o.dur=[0,65000,3725000].map(dur); o.durWords=[1800000,3900000].map(durWords);
 const ids=Object.keys(EX);
 o.ej={}; ids.forEach(id=>{ const e=EX[id]; const r=exRank(id); const nt=r&&nextTarget(id,r);
   o.ej[id]={prev:prevFor(id),maxKg:maxKg(id),maxReps:maxReps(id),goal:goal(exCfg({id,sets:3})),stalled:stalled(id),
    alts:(()=>{const a=altsFor(id);return [a.variants.map(x=>x.id),a.related.map(x=>x.id)]})(),
    rank:r?{s:r.s,name:r.name,pct:r.pct,e1:r.e1}:null, next:nt||null}; });
 o.musculos={}; MUSCLES.forEach(m=>{ const r=muscleRank(m); o.musculos[m.id]=r?{s:r.s,name:r.name,list:r.list.map(x=>x.id),tip:r.tip?{kg:r.tip.kg,reps:r.tip.reps,rank:r.tip.rank,id:r.tip.id}:null}:null; });
 const ov=overallRank(); o.general=ov?{s:ov.s,name:ov.name,count:ov.count}:null;
 o.seriesSemana=weekSets(); o.peso=bodyW();
 o.vol=S.sessions.map(s=>volOf(s.entries)); o.sets=S.sessions.map(s=>setsOf(s.entries));
 o.rangos=[0,0.5,1,1.34,2.99,3.66,4.0,5.2,5.99].map(s=>({...rankOf(s),dp:divProgress(s),nb:nextBoundary(s)}));
 const m=mergeState(S,${JSON.stringify(prueba.nube)}); o.union={ids:m.sessions.map(s=>s.id),body:m.body,weekMarks:m.weekMarks,deleted:m.deleted,program:m.program,updatedAt:m.updatedAt,active:m.active?m.active.id:null};
 return o; })()`,ctx);
console.log(JSON.stringify(out,null,0));
