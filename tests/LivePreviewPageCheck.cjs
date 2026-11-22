const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const html = fs.readFileSync('showcase/dist/live-preview.html', 'utf8');
const source = html.match(/<script>([\s\S]*?)<\/script>/)[1];
function open(channel, prepare=false) {
  const handlers = {}, windowEvents = {}, documentEvents = {}, timers = [], scripts = [];
  let player;
  function Player(id, options) {
    assert.equal(id, 'player');assert.equal(options.parent[0], 'vuedevops.github.io');
    assert.equal(options.muted, true);
    player = this;this.autoplay=options.autoplay;this.muted=options.muted;this.volume=1;this.channel=options.channel;this.paused=false;this.playCalls=0;this.pauseCalls=0;
  }
  for(const event of ['READY','PLAYING','PLAYBACK_BLOCKED','OFFLINE','ENDED','PAUSE'])Player[event]=event;
  Player.prototype.addEventListener=(name,callback)=>{handlers[name]=callback;};
  Player.prototype.setMuted=function(value){this.muted=value;};
  Player.prototype.setVolume=function(value){this.volume=value;};
  Player.prototype.getMuted=function(){return this.muted;};
  Player.prototype.getVolume=function(){return this.volume;};
  Player.prototype.getChannel=function(){return this.channel;};
  Player.prototype.isPaused=function(){return this.paused;};
  Player.prototype.setChannel=function(value){this.channel=value;};
  Player.prototype.play=function(){assert.equal(this.muted,true);this.playCalls++;};
  Player.prototype.pause=function(){this.paused=true;this.pauseCalls++;};
  const window={addEventListener:(event,callback)=>{windowEvents[event]=callback;},setInterval:callback=>{timers.push(callback);}};
  const document={hidden:false,createElement:()=>({}),head:{appendChild:script=>scripts.push(script)},addEventListener:(event,callback)=>{documentEvents[event]=callback;}};
  vm.runInNewContext(source,{window,document,location:{hostname:'vuedevops.github.io',search:(prepare?'?prepare=':'?channel=')+encodeURIComponent(channel)},URLSearchParams,Twitch:{Player}});
  return {window,document,documentEvents,windowEvents,handlers,timers,scripts,player:()=>player};
}
for(const channel of ['', 'bad/channel', '<script>alert(1)</script>']){
  const page=open(channel);assert.equal(page.window.vuePreviewStatus.status,'error');assert.equal(page.scripts.length,0);
}
const page=open('XARYU');assert.equal(page.scripts[0].src,'https://player.twitch.tv/js/embed/v1.js');
page.scripts[0].onload();page.handlers.READY();page.handlers.PLAYING();
assert.equal(page.player().autoplay,true);
assert.equal(page.window.vuePreviewStatus.status,'playing');assert.equal(page.player().channel,'xaryu');
assert.equal(page.window.vuePreviewSelect('Ludwig'),true);assert.equal(page.window.vuePreviewStatus.status,'loading');
assert.equal(page.player().pauseCalls,0);assert.equal(page.player().playCalls,1);
page.handlers.PLAYING();assert.equal(page.player().channel,'ludwig');assert.equal(page.window.vuePreviewStatus.status,'playing');
assert.equal(page.scripts.length,1);assert.equal(page.window.vuePreviewSelect('../bad'),false);assert.equal(page.player().channel,'ludwig');
page.player().muted=false;page.player().volume=1;page.timers[0]();
assert.equal(page.player().muted,true);
page.document.hidden=true;page.documentEvents.visibilitychange();assert.equal(page.player().paused,true);assert.equal(page.window.vuePreviewStatus.status,'paused');
page.windowEvents.pagehide();assert.equal(page.player().muted,true);
page.document.hidden=false;page.window.vuePreviewSelect('ludwig');
for(const [event,status] of [['OFFLINE','offline'],['ENDED','ended'],['PLAYBACK_BLOCKED','blocked']]){page.handlers[event]();assert.equal(page.window.vuePreviewStatus.status,status);}
const failed=open('xaryu');failed.scripts[0].onerror();assert.equal(failed.window.vuePreviewStatus.status,'error');
const booting=open('xaryu');booting.window.vuePreviewSelect('Ludwig');booting.scripts[0].onload();booting.handlers.READY();assert.equal(booting.player().channel,'ludwig');
// Twitch changes channels asynchronously: old playback events can arrive while
// getChannel() still names the previous stream.
const switching=open('xaryu');switching.scripts[0].onload();switching.handlers.READY();switching.handlers.PLAYING();
switching.player().setChannel=function(value){this.pendingChannel=value;};
switching.window.vuePreviewSelect('Ludwig');
for(const event of ['PLAYING','PAUSE','OFFLINE','ENDED','PLAYBACK_BLOCKED']){
  switching.handlers[event]();assert.equal(switching.window.vuePreviewStatus.status,'loading');
}
switching.player().channel='ludwig';switching.handlers.PLAYING();assert.equal(switching.window.vuePreviewStatus.status,'playing');
switching.window.vuePreviewSelect('summit1g');switching.window.vuePreviewSelect('ohnepixel');
switching.handlers.PLAYING();assert.equal(switching.window.vuePreviewStatus.status,'loading');
switching.player().channel='ohnepixel';switching.handlers.PLAYING();assert.equal(switching.window.vuePreviewStatus.status,'playing');
assert.equal(switching.player().pauseCalls,0);assert.equal(switching.player().playCalls,1);
const warm=open('xaryu',true);warm.scripts[0].onload();assert.equal(warm.player().autoplay,false);
warm.handlers.READY();assert.equal(warm.player().playCalls,0);assert.equal(warm.window.vuePreviewStatus.status,'paused');
warm.handlers.PLAYING();assert.equal(warm.window.vuePreviewStatus.status,'paused');assert.equal(warm.player().playCalls,0);
warm.window.vuePreviewSelect('xaryu');assert.equal(warm.player().playCalls,1);warm.handlers.PLAYING();assert.equal(warm.window.vuePreviewStatus.status,'playing');
warm.window.vuePreviewSuspend();assert.equal(warm.window.vuePreviewStatus.status,'paused');
for(const event of ['PLAYING','OFFLINE','ENDED','PLAYBACK_BLOCKED']){warm.handlers[event]();assert.equal(warm.window.vuePreviewStatus.status,'paused');}
warm.window.vuePreviewSelect('ludwig');warm.handlers.PLAYING();assert.equal(warm.window.vuePreviewStatus.status,'playing');assert.equal(warm.scripts.length,1);
const resume=open('xaryu',true);resume.scripts[0].onload();resume.handlers.READY();
resume.timers[0]();assert.equal(resume.player().playCalls,0);
resume.window.vuePreviewSelect('xaryu');resume.timers[0]();assert.equal(resume.player().playCalls,2);
resume.player().paused=false;resume.timers[0]();assert.equal(resume.player().playCalls,2);
resume.player().paused=true;for(let i=0;i<20;i++)resume.timers[0]();assert.equal(resume.player().playCalls,7);
resume.window.vuePreviewSuspend();resume.timers[0]();assert.equal(resume.player().playCalls,7);
const early=open('xaryu',true);early.window.vuePreviewSelect('ludwig');early.scripts[0].onload();early.handlers.READY();assert.equal(early.player().channel,'ludwig');assert.equal(early.player().autoplay,true);
const leave=open('xaryu');leave.window.vuePreviewSuspend();leave.scripts[0].onload();leave.handlers.READY();assert.equal(leave.player().autoplay,false);assert.equal(leave.player().playCalls,0);
console.log('PASS: channel validation, muted autoplay, async switching, paused warm-up, resume, suspension during boot, mute enforcement and fallback states');
