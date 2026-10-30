// 디자인 가이드(옥체 README·tokens) 준수 검사 — 배경 복사본(aria-hidden) 안은 건너뜀
const { chromium } = require('playwright'); const fs=require('fs');
// 필요: DESIGN=캔버스 project 폴더(끝에 /), RUNTIME=캔버스 dc-runtime.js, MODULES=react·react-dom이 든 node_modules, OUT=결과 폴더
const RT=process.env.RUNTIME, M=process.env.MODULES, OUT=process.env.OUT||'.';
const P=process.env.DESIGN||'/home/claude/okche_design/project/'; const canvas=JSON.parse(fs.readFileSync(P+'canvas.json'));
const names=process.argv.slice(2).length?process.argv.slice(2):Object.keys(canvas.boards).filter(f=>canvas.boards[f].page!=='killed'&&!/^(Flow|AppIcon)/.test(f)).map(f=>f.replace('.dc.html',''));
(async()=>{
  const b=await chromium.launch(); const out={};
  for (const name of names){
    const f=name+'.dc.html', bd=canvas.boards[f]; if(!bd) continue;
    const res=`<script>window.__resources={"https://cdn.jsdelivr.net/npm/react@18.3.1/umd/react.production.min.js":"file://${M}/react/umd/react.production.min.js","https://cdn.jsdelivr.net/npm/react-dom@18.3.1/umd/react-dom.production.min.js":"file://${M}/react-dom/umd/react-dom.production.min.js"}</script><script src="file://${RT}"></script>`;
    const tmp=`${OUT}/out_${name}.html`; fs.writeFileSync(tmp,fs.readFileSync(P+f,'utf8').replace('<script src="./support.js"></script>',res));
    const p=await b.newPage({viewport:{width:bd.w,height:bd.h}}); await p.goto('file://'+tmp); await p.waitForTimeout(1200);
    out[name]=await p.evaluate(()=>{
      const PAL=new Set(['251,247,239','255,255,255','43,37,34','110,100,94','220,211,198','133,122,112','47,75,124','30,50,87','174,184,207','225,231,242','201,161,74','126,98,34','231,203,138','169,130,58','162,59,50']);
      const FS=new Set([28,22,18,16,14,12,32]), H=new Set([40,44,52]), R=new Set([0,8,14,20,999]);
      const rgb=c=>{const m=c.match(/rgba?\(([^)]+)\)/); if(!m) return null; const a=m[1].split(',').map(s=>s.trim()); if(a.length===4&&+a[3]===0) return null; return {k:a.slice(0,3).join(','),a:a.length===4?+a[3]:1};};
      const hidden=e=>e.closest('[aria-hidden="true"]');
      const issues=[]; const add=(t,e,v)=>issues.push(t+' '+v+' :: '+(e.getAttribute('aria-label')||e.textContent||e.tagName).trim().replace(/\s+/g,' ').slice(0,24));
      let navy=0;
      for(const e of document.querySelectorAll('body *')){
        if(hidden(e)||e.closest('.sr-only')) continue; const r=e.getBoundingClientRect(); if(!r.width||!r.height) continue;
        const cs=getComputedStyle(e); if(cs.visibility==='hidden') continue;
        for(const [prop,on] of [['color',e.childNodes.length&&[...e.childNodes].some(n=>n.nodeType===3&&n.textContent.trim())],['backgroundColor',1],['borderTopColor',parseFloat(cs.borderTopWidth)>0]]){
          if(!on) continue; const c=rgb(cs[prop]); if(!c) continue;
          if(c.a<1 && c.k==='43,37,34') continue; // 시트 덮개
          if(!PAL.has(c.k)) add('색',e,prop+'='+c.k+(c.a<1?'/'+c.a:''));
        }
        const hasText=[...e.childNodes].some(n=>n.nodeType===3&&n.textContent.trim());
        if(hasText){const s=parseFloat(cs.fontSize); if(!FS.has(s)) add('글자크기',e,s+'px');}
        const tag=e.tagName, ctl=(tag==='BUTTON'||tag==='SELECT'||tag==='TEXTAREA'||(tag==='INPUT'&&!e.classList.contains('sr-only')&&!['radio','checkbox','file','hidden'].includes(e.type))||(tag==='A'&&(parseFloat(cs.borderTopWidth)>0||rgb(cs.backgroundColor)))||(tag==='LABEL'&&e.querySelector('input.sr-only')));
        if(ctl){ const h=Math.round(r.height); const txt=(e.textContent||'').trim().length>0 && !(e.getAttribute('aria-label')||'').includes('자세히');
          const quick=(e.getAttribute('aria-label')||'').includes('바로 기록');
          if(quick){ if(h<56) add('기록버튼높이',e,h+'px'); }
          else if(txt){ if(h!==40&&h!==52&&h<56) add('높이',e,h+'px'); }
          else if(h!==44&&h!==40&&h!==52) add('아이콘버튼높이',e,h+'px');
          const bg=rgb(cs.backgroundColor); if(bg&&bg.k==='47,75,124'&&(tag==='BUTTON'||tag==='A')) navy++; }
        const rad=parseFloat(cs.borderTopLeftRadius); if(rad && !cs.borderTopLeftRadius.endsWith('%') && !R.has(rad) && !(Math.abs(rad-Math.min(r.width,r.height)/2)<1) && rad<999) add('모서리',e,rad+'px');
      }
      if(navy>1) issues.push('남색 꽉 찬 버튼 '+navy+'개');
      return issues;
    });
    await p.close();
  }
  await b.close(); fs.writeFileSync(OUT+'/guide.json',JSON.stringify(out,null,1));
  // 가이드 예외(이유는 design/README.md): 0.85배 쉬운 말, 그래프 막대·눈금, 위젯 모서리, PDF 미리보기, 털색, 카메라 화면, 사진 칸, 온보딩 글자 로고, 일기 혈당 입력칸 56
  const OK=[/^글자크기 (13\.6|10\.2|11\.9|12\.75)px/,/^모서리 (3|22)px/,/^글자크기 8px/];
  const EX={Profile:/^색 backgroundColor/,ProfileDog:/^색 backgroundColor/,BodyCamera:/^(색|아이콘)/,Memory:/^아이콘/,Memorial:/^아이콘/,PhotoAdd:/^아이콘/,Onboarding:/^글자크기 56px/,RecordGlucose:/^아이콘버튼높이 56px :: INPUT/,RecordInsulinDone:/^아이콘버튼높이 56px :: INPUT/,RecordInsulinSkip:/^아이콘버튼높이 56px :: INPUT/};
  for(const n of Object.keys(out)) out[n]=out[n].filter(i=>!OK.some(r=>r.test(i))&&!(EX[n]&&EX[n].test(i)));
  const total=Object.values(out).reduce((a,v)=>a+v.length,0); console.log('가이드 어긴 곳', total);
  const tally={}; for(const [n,is] of Object.entries(out)) for(const i of is){const k=i.split(' :: ')[0]; (tally[k]=tally[k]||new Set()).add(n);}
  for(const [k,v] of Object.entries(tally).sort((a,b)=>b[1].size-a[1].size)) console.log(String(v.size).padStart(3),k,'|',[...v].slice(0,6).join(','));
})();
