/* Run with Node >=22 and an installed Chrome. No npm dependencies. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { spawn } = require('node:child_process');
const { setTimeout: delay } = require('node:timers/promises');

const root = path.resolve(__dirname, '../..');
const output = path.join(root, 'build/page-qa');
const port = Number(process.env.QA_CDP_PORT || 9334);
fs.mkdirSync(output, { recursive: true });
let chrome;
if (!process.env.QA_CDP_PORT) {
  const executable = process.env.CHROME_PATH || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
  assert.ok(fs.existsSync(executable), 'set CHROME_PATH to an installed Chrome executable');
  chrome = spawn(executable, ['--headless=new', `--remote-debugging-port=${port}`, `--user-data-dir=${path.join(output, 'test-profile')}`, '--no-first-run', '--no-default-browser-check', '--disable-background-networking', 'about:blank'], { windowsHide: true, stdio: 'ignore' });
}
class CDP {
  constructor(socket) {
    this.socket = socket; this.id = 0; this.pending = new Map(); this.errors = []; this.requests = [];
    socket.addEventListener('message', event => {
      const message = JSON.parse(event.data);
      if (message.id) {
        const waiter = this.pending.get(message.id);
        if (!waiter) return;
        this.pending.delete(message.id); clearTimeout(waiter.timer);
        message.error ? waiter.reject(new Error(JSON.stringify(message.error))) : waiter.resolve(message.result);
      } else if (message.method === 'Runtime.exceptionThrown') this.errors.push(message.params.exceptionDetails.text + ': ' + (message.params.exceptionDetails.exception?.description || ''));
      else if (message.method === 'Runtime.consoleAPICalled' && message.params.type === 'error') this.errors.push(JSON.stringify(message.params.args));
      else if (message.method === 'Network.requestWillBeSent') this.requests.push(message.params.request.url);
    });
  }
  send(method, params = {}) {
    const id = ++this.id;
    return new Promise((resolve,reject) => {
      const timer = setTimeout(() => { this.pending.delete(id); reject(new Error(`CDP timeout: ${method}`)); },15000);
      this.pending.set(id, {resolve,reject,timer});
      this.socket.send(JSON.stringify({id,method,params}));
    });
  }
  async evaluate(expression) {
    const result = await this.send('Runtime.evaluate', { expression, returnByValue:true, awaitPromise:true });
    if (result.exceptionDetails) throw new Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text);
    return result.result.value;
  }
  async settle() { await this.evaluate('new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))'); }
  async capture(name) {
    await this.settle();
    const result = await this.send('Page.captureScreenshot', {format:'png',captureBeyondViewport:false});
    fs.writeFileSync(path.join(output, name+'.png'),Buffer.from(result.data,'base64'));
  }
}
async function main() {
  let version;
  for (let attempt=0;attempt<80;attempt++) {
    try { version = await fetch(`http://127.0.0.1:${port}/json/version`).then(r=>r.json()); break; } catch { await delay(100); }
  }
  assert.ok(version, 'Chrome debugging endpoint must be available');
  const target = await fetch(`http://127.0.0.1:${port}/json/new?about:blank`, {method:'PUT'}).then(r=>r.json());
  const socket = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve,reject) => {socket.addEventListener('open',resolve,{once:true}); socket.addEventListener('error',reject,{once:true});});
  const cdp = new CDP(socket);
  const results = [];
  const check = async (name,fn) => {
    try { await fn(); results.push({name,pass:true}); console.log('PASS',name); }
    catch(error) { results.push({name,pass:false,error:error.message}); console.error('FAIL',name,error.message); }
  };
  try {
    await cdp.send('Page.enable'); await cdp.send('Runtime.enable'); await cdp.send('Network.enable');
    await cdp.send('Network.emulateNetworkConditions',{offline:true,latency:0,downloadThroughput:-1,uploadThroughput:-1});
    await cdp.send('Emulation.setDeviceMetricsOverride',{width:1440,height:1000,deviceScaleFactor:1,mobile:false});
    const url = pathToFileURL(path.join(root,'doc/index.html')).href;
    await cdp.send('Page.navigate',{url});
    for (let i=0;i<40;i++) { if (await cdp.evaluate('document.documentElement.classList.contains("js-ready")')) break; await delay(50); }
    await cdp.settle();
    await check('file:// works offline without external assets',async()=> {
      assert.equal(await cdp.evaluate('document.documentElement.classList.contains("js-ready")'),true);
      assert.equal(await cdp.evaluate('document.querySelectorAll("[data-lab] .scene-count").length'),2);
      assert.equal(cdp.requests.filter(url=>/^https?:/.test(url)).length,0);
    });
    await check('unique IDs and valid internal anchors',async()=> {
      const broken = await cdp.evaluate(`(() => {const ids=[...document.querySelectorAll('[id]')].map(e=>e.id); return {duplicates:ids.filter((id,i)=>ids.indexOf(id)!==i),missing:[...document.querySelectorAll('a[href^="#"]')].filter(a=>!document.getElementById(a.hash.slice(1))).map(a=>a.hash)};})()`);
      assert.deepEqual(broken,{duplicates:[],missing:[]});
    });
    await cdp.capture('desktop-hero');
    await check('320/390/768/1024/1440 widths: no page overflow and readable code size',async()=> {
      for (const width of [320,390,768,1024,1440]) {
        await cdp.send('Emulation.setDeviceMetricsOverride',{width,height:1000,deviceScaleFactor:1,mobile:false}); await cdp.settle();
        const metrics = await cdp.evaluate(`({width:innerWidth,scroll:document.documentElement.scrollWidth,font:parseFloat(getComputedStyle(document.querySelector('.scene-code pre')).fontSize)})`);
        assert.ok(metrics.scroll <= metrics.width+1,JSON.stringify(metrics)); assert.ok(metrics.font>=12);
      }
    });
    await check('the first mobile animation controls are reachable before the full diagram',async()=> {
      await cdp.send('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:false}); await cdp.settle();
      const metrics = await cdp.evaluate(`(() => {const lab=document.getElementById('proxy-lab');return {control:lab.querySelector('[data-action="next"]').getBoundingClientRect().top,graph:lab.querySelector('.graph').getBoundingClientRect().top};})()`);
      assert.ok(metrics.control<metrics.graph,'a learner should not scroll past the whole scene to find Next');
    });
    await cdp.evaluate(`document.getElementById('proxy-lab').scrollIntoView({behavior:'instant',block:'start'})`); await cdp.capture('mobile-proxy');
    await check('anchor headings sit directly below the sticky navigation',async()=> {
      for (const width of [390,1440]) {
        await cdp.send('Emulation.setDeviceMetricsOverride',{width,height:1000,deviceScaleFactor:1,mobile:false});await cdp.settle();
        const position=await cdp.evaluate(`(() => {const lab=document.getElementById('proxy-lab');lab.scrollIntoView({behavior:'instant',block:'start'});const mobile=document.querySelector('.mobile-nav');return {top:lab.getBoundingClientRect().top,header:innerWidth<=760?mobile.getBoundingClientRect().bottom:document.querySelector('.topbar').getBoundingClientRect().bottom};})()`);
        assert.ok(position.top>=position.header && position.top<=position.header+40,JSON.stringify(position));
      }
    });
    await cdp.send('Emulation.setDeviceMetricsOverride',{width:1440,height:1000,deviceScaleFactor:1,mobile:false});
    await check('mapper scenes synchronize input, annotation, SQL and returned object',async()=> {
      const result=await cdp.evaluate(`(() => {const lab=document.getElementById('proxy-lab');const click=a=>lab.querySelector('[data-action="'+a+'"]').click();click('restart');for(let i=0;i<6;i++)click('next');const bound=lab.querySelector('.values').textContent;click('next');click('next');return {bound,result:lab.querySelector('.values').textContent,highlight:lab.querySelectorAll('.code-line.active').length};})()`);
      assert.match(result.bound,/setObject\(1, 1L\)/);assert.match(result.result,/Alice/);assert.ok(result.highlight>0);
    });
    await cdp.evaluate(`document.getElementById('proxy-lab').scrollIntoView({behavior:'instant',block:'start'})`); await cdp.capture('desktop-proxy');
    await check('all mapper branches show the right outcome without stale result values',async()=> {
      const outputs=await cdp.evaluate(`(() => {const lab=document.getElementById('proxy-lab');return ['missing','object','mapper'].map(name=>{const select=lab.querySelector('[data-action="scenario"]');select.value=name;select.dispatchEvent(new Event('change',{bubbles:true}));for(let i=0;i<12;i++)lab.querySelector('[data-action="next"]').click();return lab.querySelector('.values').textContent;});})()`);
      assert.match(outputs[0],/UnsupportedOperationException/);assert.doesNotMatch(outputs[0],/Alice/);assert.match(outputs[1],/MapperProxy/);assert.match(outputs[2],/Alice/);
    });
    await check('three transaction paths yield 100/100, 70/100 and 100/100',async()=> {
      const outcomes=await cdp.evaluate(`(() => {const lab=document.getElementById('transaction-lab');return ['external','self','outer'].map(name=>{const select=lab.querySelector('[data-action="scenario"]');select.value=name;select.dispatchEvent(new Event('change',{bubbles:true}));for(let i=0;i<10;i++)lab.querySelector('[data-action="next"]').click();return [...lab.querySelectorAll('[data-account]')].map(e=>Number(e.textContent));});})()`);
      assert.deepEqual(outcomes,[[100,100],[70,100],[100,100]]);
    });
    await cdp.evaluate(`document.getElementById('transaction-lab').scrollIntoView({behavior:'instant',block:'start'})`);await cdp.capture('desktop-transaction');
    await check('keyboard navigation preserves focus and updates the scene',async()=> {
      await cdp.evaluate(`document.querySelector('#proxy-lab [data-action="restart"]').click();document.querySelector('#proxy-lab .graph').focus()`);
      await cdp.send('Input.dispatchKeyEvent',{type:'keyDown',key:'ArrowRight',code:'ArrowRight',windowsVirtualKeyCode:39});
      await cdp.send('Input.dispatchKeyEvent',{type:'keyUp',key:'ArrowRight',code:'ArrowRight',windowsVirtualKeyCode:39});
      assert.match(await cdp.evaluate('document.querySelector("#proxy-lab .scene-count").textContent'),/02 \/ 09/);
      assert.equal(await cdp.evaluate('document.activeElement.classList.contains("graph")'),true);
    });
    await check('scenario switch and reset stop a running browser timer',async()=> {
      await cdp.evaluate(`(() => {const lab=document.getElementById('proxy-lab');const speed=lab.querySelector('[data-action="speed"]');speed.value='2';speed.dispatchEvent(new Event('change',{bubbles:true}));lab.querySelector('[data-action="play"]').click();const select=lab.querySelector('[data-action="scenario"]');select.value='missing';select.dispatchEvent(new Event('change',{bubbles:true}));lab.querySelector('[data-action="restart"]').click();})()`);
      const before=await cdp.evaluate('document.querySelector("#proxy-lab .scene-count").textContent');await delay(2800);
      assert.equal(await cdp.evaluate('document.querySelector("#proxy-lab .scene-count").textContent'),before);
      assert.equal(await cdp.evaluate('document.querySelector("#proxy-lab [data-action=play]").getAttribute("aria-pressed")'),'false');
    });
    await check('reduced motion retains content and removes token animation',async()=> {
      await cdp.send('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'reduce'}]});
      await cdp.evaluate('document.querySelector("#proxy-lab [data-action=next]").click()');await cdp.settle();
      assert.equal(await cdp.evaluate('getComputedStyle(document.querySelector(".flow-dot")).display'),'none');
      assert.equal(await cdp.evaluate('document.querySelector(".motion-note").hidden'),false);
      assert.ok(await cdp.evaluate('document.querySelector(".scene-description").textContent.length>20'));
    });
    await check('400% desktop-equivalent reflow retains all content',async()=> {
      await cdp.send('Emulation.setDeviceMetricsOverride',{width:360,height:250,deviceScaleFactor:1,mobile:false});await cdp.settle();
      assert.ok(await cdp.evaluate('document.documentElement.scrollWidth<=innerWidth+1'));
      assert.ok(await cdp.evaluate('document.querySelectorAll("[data-action=next]").length>=2'));
    });
    await cdp.send('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:false});
    await cdp.evaluate("scrollTo({top:0,behavior:'instant'})");await cdp.capture('mobile-hero');
    await check('no-JavaScript mode retains explanations and complete static sequences',async()=> {
      await cdp.send('Emulation.setScriptExecutionDisabled',{value:true});
      await cdp.send('Page.navigate',{url});await delay(150);
      assert.equal(await cdp.evaluate('document.querySelectorAll(".static-steps[open]").length'),2);
      assert.equal(await cdp.evaluate('getComputedStyle(document.querySelector(".lab-dynamic")).display'),'none');
      assert.ok(await cdp.evaluate('document.querySelector("#proxy-lab .static-steps").textContent.includes("getAnnotation") || document.querySelector("#proxy-lab .static-steps").textContent.includes("@Select")'));
    });
    await check('no uncaught browser exceptions or console errors',async()=>assert.deepEqual(cdp.errors,[]));
    fs.writeFileSync(path.join(output,'browser-results.json'),JSON.stringify({browser:version.Browser,results,errors:cdp.errors},null,2));
    console.log(`Browser: ${version.Browser}; ${results.filter(r=>r.pass).length}/${results.length} checks passed; screenshots: ${output}`);
    if (results.some(r=>!r.pass)) process.exitCode=1;
  } finally {
    await fetch(`http://127.0.0.1:${port}/json/close/${target.id}`).catch(()=>{});socket.close();
    if(chrome){ const browserSocket=new WebSocket(version.webSocketDebuggerUrl);await new Promise(resolve=>browserSocket.addEventListener('open',resolve,{once:true}));browserSocket.send(JSON.stringify({id:1,method:'Browser.close'}));browserSocket.close(); }
  }
}
main().catch(error=>{console.error(error);process.exitCode=1;if(chrome)chrome.kill();});
