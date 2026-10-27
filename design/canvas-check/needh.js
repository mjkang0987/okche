// 화면 루트의 높이 제한을 풀었을 때 실제로 필요한 높이
const { chromium } = require('playwright'); const fs=require('fs');
const OUT=process.env.OUT||'.';
(async()=>{const b=await chromium.launch(); const out={};
for(const name of process.argv.slice(2)){const p=await b.newPage({viewport:{width:390,height:844}});
 await p.goto('file://'+OUT+'/out_'+name+'.html'); await p.waitForTimeout(1200);
 out[name]=await p.evaluate(()=>{const r=[...document.body.querySelectorAll('div')].find(d=>d.style.width==='390px'&&d.style.height); const old=r.style.height; r.style.height='auto'; r.style.minHeight=old; const h=Math.ceil(r.getBoundingClientRect().height); return [old,h];}); await p.close();}
await b.close(); console.log(JSON.stringify(out));})();
