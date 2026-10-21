// Genera un estado de prueba determinista con el mismo formato que la web.
const fs=require('fs');
let seed=42; const rnd=()=>{ seed=(seed*1103515245+12345)%2147483648; return seed/2147483648; };
const lib=JSON.parse(fs.readFileSync(process.argv[2]+'/ejercicios.json')); const progs=JSON.parse(fs.readFileSync(process.argv[2]+'/programas.json'));
const routines=progs[0].routines; const sessions=[]; const body=[];
let d=new Date(2026,3,6,12); // 6 abr 2026
const end=new Date(2026,9,10,12);
const base={}; lib.forEach(e=>base[e.id]=e.kind==='w'?(10+Math.floor(rnd()*60)):0);
let n=0;
while(d<=end){
  const dow=d.getDay(); const r=routines.find(x=>x.dow===dow);
  if(r && rnd()<0.8){
    const entries={};
    r.items.forEach((it,k)=>{ const e=lib.find(x=>x.id===it.id); const sets=[];
      const grow=Math.floor(n/6)*(e.kind==='w'?2.5:1);
      for(let i=0;i<(it.sets||3);i++){ const kg=e.kind==='w'?Math.round((base[e.id]+grow+(rnd()<0.3?-2.5:0))*2)/2:null; const reps=e.kind==='s'?30+Math.floor(rnd()*20):(5+Math.floor(rnd()*12)); sets.push({kg,reps:(rnd()<0.05?null:reps),done:true}); }
      if(k===2&&rnd()<0.3) sets[0].kg=null;
      entries[it.id]=sets; });
    const iso=d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');
    sessions.push({id:'s-'+iso,date:iso,day:r.id,title:(rnd()<0.2?undefined:r.title),ms:3000000+Math.floor(rnd()*2000000),entries}); n++;
  }
  if(dow===1){ const iso=d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0'); body.push({date:iso,kg:Math.round((74+n*0.05+rnd())*10)/10}); }
  d.setDate(d.getDate()+1);
}
const weekMarks={'2026-04-13':true,'2026-05-04':false,'2026-08-31':true};
const st={v:2,updatedAt:1760000000000,sessions,body,weekMarks,deleted:['s-x'],routines,program:'vol5',progEdited:false,
 active:{id:'s-act',date:'2026-10-10',day:'V',title:'Pierna B',start:1760100000000,items:routines[4].items,entries:{smith:[{kg:60,reps:8,done:true},{kg:60,reps:null,done:false}],"rdl-barra":[{kg:70,reps:10,done:true}]}},
 weeks:{'2026-03':[true,false,true,null]}};
// Segundo estado (como si viniera de la nube) para probar la unión
const nube={v:2,updatedAt:1760050000000,sessions:[sessions[3],{id:'s-nube',date:'2026-10-09',day:'J',title:'Torso B',entries:{"press-inclinado":[{kg:22,reps:9,done:true}]}}],
 body:[{date:'2026-10-05',kg:79.9},{date:'2026-10-11',kg:80.2}],weekMarks:{'2026-05-04':true,'2026-10-05':true},deleted:[sessions[1].id],routines:progs[1].routines,program:'vol4',progEdited:true};
fs.writeFileSync(process.argv[3],JSON.stringify({estado:st,nube}));
console.log('sesiones',sessions.length,'pesos',body.length);
