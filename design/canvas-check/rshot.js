// Render .dc.html files through the real canvas runtime (local React) and screenshot them.
const { chromium } = require('playwright'); const fs=require('fs'); const path=require('path');
// 필요: DESIGN, RUNTIME, MODULES, OUT — guide.js와 같음
const RT=process.env.RUNTIME, M=process.env.MODULES, OUT=process.env.OUT||'.';
const P=process.env.DESIGN||'/home/claude/okche_design/project/'; const canvas=JSON.parse(fs.readFileSync(P+'canvas.json'));
(async()=>{
  const b=await chromium.launch(); const errs={};
  for (const name of process.argv.slice(2)){
    const f=name+'.dc.html', bd=canvas.boards[f];
    const res=`<script>window.__resources={"https://cdn.jsdelivr.net/npm/react@18.3.1/umd/react.production.min.js":"file://${M}/react/umd/react.production.min.js","https://cdn.jsdelivr.net/npm/react-dom@18.3.1/umd/react-dom.production.min.js":"file://${M}/react-dom/umd/react-dom.production.min.js"}</script><script src="file://${RT}"></script>`;
    const html=fs.readFileSync(P+f,'utf8').replace('<script src="./support.js"></script>',res);
    const tmp=`${OUT}/out_${name}.html`; fs.writeFileSync(tmp,html);
    const p=await b.newPage({viewport:{width:bd.w,height:bd.h},deviceScaleFactor:+(process.env.DSF||1)}); errs[name]=[];
    p.on('console',m=>{ if(['error','warning'].includes(m.type())) errs[name].push(m.text().slice(0,200)); });
    p.on('pageerror',e=>errs[name].push('PAGEERR '+e.message.slice(0,200)));
    await p.goto('file://'+tmp); await p.waitForTimeout(1500);
    await p.screenshot({path:`${OUT}/${name}.png`}); await p.close();
  }
  await b.close(); console.log(JSON.stringify(errs,null,1));
})();
