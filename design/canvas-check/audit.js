// 모든 보드를 실제 런타임으로 그려 링크·넘침·라벨·콘솔 오류를 점검
const { chromium } = require('playwright'); const fs=require('fs');
// 필요: DESIGN, RUNTIME, MODULES, OUT — guide.js와 같음
const RT=process.env.RUNTIME, M=process.env.MODULES, OUT=process.env.OUT||'.';
const P=process.env.DESIGN||'/home/claude/okche_design/project/'; const canvas=JSON.parse(fs.readFileSync(P+'canvas.json'));
const names=Object.keys(canvas.boards);
(async()=>{
  const b=await chromium.launch(); const out={};
  for (const f of names){
    const bd=canvas.boards[f]; const issues=[];
    const res=`<script>window.__resources={"https://cdn.jsdelivr.net/npm/react@18.3.1/umd/react.production.min.js":"file://${M}/react/umd/react.production.min.js","https://cdn.jsdelivr.net/npm/react-dom@18.3.1/umd/react-dom.production.min.js":"file://${M}/react-dom/umd/react-dom.production.min.js"}</script><script src="file://${RT}"></script>`;
    const html=fs.readFileSync(P+f,'utf8').replace('<script src="./support.js"></script>',res);
    const tmp=`${OUT}/a_${f}.html`; fs.writeFileSync(tmp,html);
    const p=await b.newPage({viewport:{width:bd.w,height:bd.h}});
    p.on('console',m=>{ if(m.type()==='error'||m.type()==='warning') issues.push('console: '+m.text().slice(0,160)); });
    p.on('pageerror',e=>issues.push('pageerror: '+e.message.slice(0,160)));
    await p.goto('file://'+tmp); await p.waitForTimeout(1200);
    const r=await p.evaluate(([W,H])=>{
      const iss=[]; const vis=e=>{const s=getComputedStyle(e); return s.display!=='none'&&s.visibility!=='hidden'&&!e.closest('.sr-only')&&!e.classList.contains('sr-only')};
      for(const a of document.querySelectorAll('a[href]')){ const h=a.getAttribute('href'); if(h==='#'||h==='') iss.push('dead link: '+a.textContent.trim().slice(0,20)); else if(h.endsWith('.dc.html')) iss.push('LINK '+h); }
      for(const e of document.querySelectorAll('body *')){ if(!vis(e)||e.closest('svg')&&e.tagName!=='svg') continue; const r=e.getBoundingClientRect(); if(r.width===0&&r.height===0) continue;
        if(r.right>W+1||r.bottom>H+1||r.left<-1) { if(!e.closest('[aria-hidden="true"]')) iss.push(`overflow: <${e.tagName.toLowerCase()}> "${(e.textContent||'').trim().slice(0,18)}" ${Math.round(r.right)}x${Math.round(r.bottom)}`); }
        if(e.scrollWidth>e.clientWidth+2 && getComputedStyle(e).overflowX!=='visible' && e.clientWidth>0 && !['svg'].includes(e.tagName.toLowerCase())) iss.push(`clipped-x: <${e.tagName.toLowerCase()}> "${(e.textContent||'').trim().slice(0,18)}"`);
      }
      for(const i of document.querySelectorAll('input,select,textarea')){ const id=i.id; const lab=i.closest('label')||(id&&document.querySelector(`label[for="${id}"]`))||i.getAttribute('aria-label'); if(!lab) iss.push('unlabeled '+i.type); }
      for(const btn of document.querySelectorAll('button,a[href]')){ const t=(btn.textContent||'').trim()||btn.getAttribute('aria-label'); if(!t) iss.push('no-name '+btn.tagName); const r=btn.getBoundingClientRect(); if(r.height&&r.height<24) iss.push(`small-target ${Math.round(r.width)}x${Math.round(r.height)} "${t&&t.slice(0,15)}"`); }
      const ids={}; for(const e of document.querySelectorAll('[id]')) ids[e.id]=(ids[e.id]||0)+1; for(const k in ids) if(ids[k]>1) iss.push('dup id '+k);
      const lum=c=>{const m=c.match(/[\d.]+/g).map(Number); const [r,g,b]=m.slice(0,3).map(v=>{v/=255;return v<=0.03928?v/12.92:Math.pow((v+0.055)/1.055,2.4)}); return 0.2126*r+0.7152*g+0.0722*b};
      const bgOf=e=>{ for(let n=e;n;n=n.parentElement){ const c=getComputedStyle(n).backgroundColor; const m=c.match(/[\d.]+/g); if(m&&(m.length<4||+m[3]>0.5)) return c; } return 'rgb(251,247,239)'; };
      for(const e of document.querySelectorAll('body *')){ if(!vis(e)||e.closest('svg')||e.closest('[aria-hidden="true"]')) continue; const t=[...e.childNodes].filter(n=>n.nodeType===3&&n.textContent.trim()).map(n=>n.textContent.trim()).join(''); if(!t) continue;
        const cs=getComputedStyle(e); const L1=lum(cs.color), L2=lum(bgOf(e)); const cr=(Math.max(L1,L2)+0.05)/(Math.min(L1,L2)+0.05); const big=parseFloat(cs.fontSize)>=24||(parseFloat(cs.fontSize)>=18.66&&+cs.fontWeight>=700);
        if(cr<(big?3:4.5)) iss.push(`contrast ${cr.toFixed(2)} "${t.slice(0,16)}"`); }
      return iss; },[bd.w,bd.h]);
    out[f]=[...issues,...r]; await p.close();
  }
  await b.close();
  const links=new Set(); const exist=new Set(names);
  for(const [f,l] of Object.entries(out)){ out[f]=l.filter(x=>{ if(x.startsWith('LINK ')){ const t=x.slice(5); if(!exist.has(t)) return true; links.add(t); return false;} return true; }); }
  const orphan=names.filter(n=>!links.has(n)&&!n.startsWith('AppIcon')&&n!=='Flow.dc.html');
  for(const [f,l] of Object.entries(out)) if(l.length) console.log('##',f,'\n  '+[...new Set(l)].join('\n  '));
  console.log('unreached boards (no link points to them):',orphan.join(', '));
})();
