'use strict';
const $ = selector => document.querySelector(selector);
const page = $('#page'), overlay = $('#overlay'), tv = $('#tv');
const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const icon = kind => `<svg class="nav-icon" viewBox="0 0 24 24" aria-hidden="true">${kind === 'search' ? '<circle cx="10" cy="10" r="6.5"/><path d="m15 15 6 6"/>' : '<path d="m9 3 1-2h4l1 2 2 1 2-.2 2 3-1 2v3l1 2-2 3-2-.2-2 1-1 3h-4l-1-3-2-1-2 .2-2-3 1-2V9L3 7l2-3 2 .2Z"/><circle cx="12" cy="11" r="3"/>'}</svg>`;
const state = {tab:'home',mode:'normal',banner:0,dialogs:[],query:'',mine:false,profile:'Vue',avatar:0,move:null,preview:true};
let data, toastTimer, returnFocus;
state.avatarStyle=VueAvatar.empty();
state.profiles=[];state.profileIndex=0;
function saveActiveProfile(){state.profiles[state.profileIndex]={name:state.profile,avatar:{...state.avatarStyle}};}
function selectProfile(index){saveActiveProfile();state.profileIndex=index;state.profile=state.profiles[index].name;state.avatarStyle={...state.profiles[index].avatar};}
function avatarImage(){const editor=state.dialogs.find(d=>d.type==='editor');return VueAvatar.render(editor?.name??state.profile,state.avatarStyle);}
function updateAvatar(){const preview=$('#avatar-preview');if(preview)preview.src=avatarImage();for(const button of overlay.querySelectorAll('[data-avatar-choice]')){const field=VueAvatar.fields[button.dataset.avatarChoice],resolved=VueAvatar.resolve(overlay.querySelector('.profile-name')?.value||state.profile,state.avatarStyle);button.querySelector('small').textContent=VueAvatar.options[field][resolved[field]]+(state.avatarStyle[field]<0?' · Auto':'');}}
const appNames = [['emby','Emby'],['youtube','YouTube'],['netflix','Netflix'],['twitch','Twitch'],['s0undtv','S0undTV'],['kick','Kick'],['viaplay','Viaplay'],['browser','BROWSER'],['emby-universal','Emby'],['max','HBO Max']];
let apps = appNames.map(([key,title]) => ({key,title}));
function fit(){tv.style.transform=`scale(${Math.min(innerWidth/960,innerHeight/540)})`;}
new ResizeObserver(fit).observe(document.body);fit();
function toast(message){clearTimeout(toastTimer);$('#toast').textContent=message;$('#toast').classList.add('show');toastTimer=setTimeout(()=>$('#toast').classList.remove('show'),3500);}
function status(item){return ({2:'Awaiting approval',3:'Requested · processing',4:'Partly available',5:'Available',6:'Unavailable to request'})[item.status] || (item.available?'Available':'Not requested');}
function facts(item){return [item.rating?`★ ${Number(item.rating).toFixed(1)} / 10`:null,item.year,item.minutes?`${item.minutes>=60?Math.floor(item.minutes/60)+'h ':''}${item.minutes%60}m`:null,...(item.genres||[]).slice(0,2)].filter(Boolean).join('  ·  ');}
function allMedia(){return [...data.picks,...data.resume,...data.latest,...Object.values(data.requests).flat(),data.featuredDetail,...data.live];}
function item(key){return allMedia().find(x=>x.key===key);}
function showMedia(media){if(!media)return;if(media.provider==='Requests')open('detail',{media});else open('notice',{title:media.title,copy:media.provider==='Twitch'?'On the TV, this opens the selected channel in S0undTV. The captured channel lineup stays local to this demo.':'On the TV, this opens the matching title in the installed Emby app. This website keeps you inside the demo.'});}
function heroPool(){return state.tab==='live'?data.live:data.picks;}
function setBackdrop(media){$('#backdrop').style.backgroundImage=media?.image?`url("${media.image}")`:'none';}
function setMode(mode){state.mode=mode;tv.classList.toggle('mode-card',mode==='card');tv.classList.toggle('mode-hero',mode==='hero');}
function heroMarkup(){const media=heroPool()[state.banner%Math.max(1,heroPool().length)];return `<button class="hero" data-action="hero" data-row="hero" aria-label="Featured recommendation${media?': '+esc(media.title):''}"><span class="hero-provider"></span><span class="hero-title"></span><span class="hero-subtitle"></span><span class="hero-description"></span><span class="hero-meta"></span><span class="hero-watch"></span><span class="hero-dots">${heroPool().slice(0,3).map((_,i)=>`<i class="${i===state.banner?'active':''}"></i>`).join('')}</span></button>`;}
function showHero(media){
 if(!media)return;setBackdrop(media);
 const title=$('.hero-title');if(!title)return;
 title.textContent=media.title;title.style.fontSize=(media.title.length>32?34:media.title.length>22?42:52)+'px';
 $('.hero-provider').textContent=state.mode==='card'?media.provider:media.provider.toUpperCase();
 $('.hero-subtitle').textContent=media.type==='live'?'Live from your follows':'Recommended for you';
 $('.hero-description').textContent=media.overview||media.game||'';
 $('.hero-meta').textContent=media.type==='live'?`LIVE · ${media.game} · ${Number(media.viewers).toLocaleString()} watching`:facts(media);
 $('.hero-watch').textContent=media.provider==='Requests'?'View details':'Watch now';
}
function header(){return `<nav class="tv-nav" data-row="nav" aria-label="App navigation"><button class="avatar-button" data-action="profiles" aria-label="Choose user"><img src="assets/demo/avatar${state.avatar?'-custom':''}.png" alt=""></button><div class="nav-pill"><button class="search-nav" data-action="search" aria-label="Search">${icon('search')}</button>${['home','apps','live','requests'].map(tab=>`<button data-tab="${tab}" class="${state.tab===tab?'selected':''}">${tab[0].toUpperCase()+tab.slice(1)}</button>`).join('')}</div><button class="settings-button" data-action="settings" aria-label="VueSettings">${icon('settings')}</button><span class="tv-clock">15:12</span><span class="tv-brand">| &nbsp; Vue TV</span></nav>`;}
function mediaRow(title,items,key,top=false){return `<section class="${state.tab==='requests'?'request-section':''}"><h2 class="section-title">${esc(title)}</h2><div class="rail" data-row="${key}">${items.map(m=>`<button class="media-card ${top?'top-pick':''}" data-media="${esc(m.key)}" aria-label="${esc(m.title)}, ${esc(m.provider)}"><div class="card-art"><img src="${esc(m.image)}" alt="" loading="lazy">${m.type==='live'?`<span class="live-badge">LIVE</span><span class="stream-name">${esc(m.title)}</span>`:''}</div><span class="card-caption">${esc(m.title)}<small>${esc(m.provider==='Requests'?status(m):m.type==='live'?m.game:facts(m))}</small></span></button>`).join('')}</div></section>`;}
function appRow(){return `<h2 class="apps-title">Your apps</h2><div class="app-rail" data-row="apps">${apps.map(app=>`<button class="app-icon" data-app="${app.key}" aria-label="Open ${esc(app.title)}"><img src="assets/demo/app-${app.key}.webp" alt=""><span>${esc(app.title)}</span></button>`).join('')}</div>`;}
function renderPage(focus=true){
 state.banner=0;setMode('normal');tv.classList.toggle('tab-apps',state.tab==='apps');page.scrollTop=0;
 let html=header();
 if(state.tab==='apps'){
  html+=`<div class="app-banners" data-row="banners">${['emby','netflix','youtube','twitch'].map(key=>`<button class="app-banner" data-app="${key}" aria-label="Open ${appNames.find(a=>a[0]===key)[1]}"><img src="assets/demo/${['emby','netflix'].includes(key)?'banner-':'app-'}${key}.webp" alt=""></button>`).join('')}</div>${appRow()}<button class="app-search" data-row="store" data-action="store">${icon('search')}Search for apps and games</button>`;setBackdrop(null);
 }else if(state.tab==='requests'){
  html+=`<h1 class="requests-title">Requests</h1><div class="request-controls" data-row="request-controls"><button class="tv-button" data-action="search">Search movies and shows</button><button class="tv-button" data-action="discover">Discover</button><button class="tv-button" data-action="mine">My requests</button><button class="tv-button" data-action="disconnect">Sign out</button></div>`;
  if(state.query){const found=[...new Map(allMedia().filter(m=>m.type!=='live'&&m.title.toLowerCase().includes(state.query.toLowerCase())).map(m=>[m.title,m])).values()];html+=mediaRow(`Results for “${state.query}”`,found,'results');if(!found.length)html+='<p class="empty-results">No titles found. Try another search.</p>';}
  else if(state.mine)html+=mediaRow('My latest requests',data.requests.mine||[],'mine')+(!data.requests.mine?.length?'<p class="empty-results">You haven’t requested anything yet.</p>':'');
  else html+=mediaRow('Trending now',data.requests.trending,'trending')+mediaRow('Popular movies',data.requests.movies,'movies')+mediaRow('Popular shows',data.requests.shows,'shows');setBackdrop(null);
 }else if(state.tab==='live')html+=heroMarkup()+mediaRow('Live from your follows',data.live,'live');
 else html+=heroMarkup()+mediaRow('Top picks for you',data.picks,'picks',true)+appRow()+mediaRow('Continue watching',data.resume,'resume')+mediaRow('Recently added',data.latest,'latest')+mediaRow('Live from your follows',data.live,'home-live');
 page.innerHTML=html;page.querySelectorAll('.avatar-button img').forEach(img=>img.src=avatarImage());
 if(['home','live'].includes(state.tab))showHero(heroPool()[0]);
 if(focus)page.querySelector(`[data-tab="${state.tab}"]`).focus({preventScroll:true});
}
function switchTab(tab){if(!['home','apps','live','requests'].includes(tab))return;state.tab=tab;state.query='';state.mine=false;state.dialogs=[];overlay.replaceChildren();renderPage();}
function open(type,extra={}){if(!state.dialogs.length)returnFocus=document.activeElement;state.dialogs.push({type,...extra});renderOverlay();}
function back(){if(state.dialogs.length){const closed=state.dialogs.pop();if(closed.type==='editor'&&closed.previousStyle)state.avatarStyle=closed.previousStyle;renderOverlay();if(!state.dialogs.length&&returnFocus?.isConnected)returnFocus.focus({preventScroll:true});}else if(state.move){state.move=null;toast('App order saved for this demo.');}else if(state.tab!=='home')switchTab('home');else{setMode('normal');page.scrollTop=0;page.querySelector('[data-tab=home]').focus({preventScroll:true});}}
function dialog(title,copy,body,actions=''){return `<div class="overlay-backdrop"><section class="tv-dialog" role="dialog" aria-modal="true" aria-label="${esc(title)}"><h1>${esc(title)}</h1>${copy?`<p>${esc(copy)}</p>`:''}${body}${actions?`<div class="dialog-actions" data-row="dialog-actions">${actions}</div>`:''}</section></div>`;}
function btn(label,action,extra=''){return `<button class="tv-button" data-action="${action}" ${extra}>${esc(label)}</button>`;}
function renderOverlay(){
 const current=state.dialogs.at(-1);if(!current){overlay.replaceChildren();return;}
 let html='', media=current.media;
 switch(current.type){
  case 'profiles':saveActiveProfile();html=dialog('Who’s watching?','Choose your user. Hold a user to edit their profile.',`<div class="profile-list" data-row="users">${state.profiles.map((profile,index)=>`<button class="profile-card" data-action="choose-profile" data-profile="${index}"><img src="${VueAvatar.render(profile.name,profile.avatar)}" alt=""><strong>${esc(profile.name)}</strong><small>${index===state.profileIndex?'Current user':''}</small></button>`).join('')}</div>`,btn('+ Create user','create-profile')+btn('Edit profile','edit-profile')+btn('VueSettings','settings')+btn('Close','back'));break;
  case 'editor':html=`<div class="overlay-backdrop"><section class="tv-dialog editor" role="dialog" aria-modal="true" aria-label="Edit profile"><h1>Edit profile</h1><p>Make this little face your own.</p><div class="editor-body"><div class="avatar-preview" data-row="avatar-actions"><img id="avatar-preview" src="assets/demo/avatar${state.avatar?'-custom':''}.png" alt="Avatar preview">${btn('Shuffle avatar','shuffle')}${btn('Reset to username','reset-avatar')}</div><div class="avatar-controls"><input class="profile-name" data-row="profile-name" aria-label="Profile name" value="${esc(current.name??state.profile)}" maxlength="24"><div class="choice-grid" data-row="avatar-options">${[['Background','Rose'],['Skin tone','Golden · Auto'],['Hair','Swoop · Auto'],['Hair color','Chestnut'],['Eyes','Happy'],['Accessory','None']].map(([label,value])=>`<button class="tv-button" data-avatar-choice="${label}">${label}<small>${value}</small></button>`).join('')}</div></div></div><div class="dialog-actions" data-row="editor-actions">${btn('Save changes','save-profile')}${btn('Cancel','back')}${btn('Delete profile','delete-profile','class="danger"')}</div></section></div>`;break;
  case 'create-profile':html=dialog('Create user','This profile lives only in this browser demo.','<input class="search-input" aria-label="New profile name" maxlength="24" placeholder="Profile name">',btn('Create','create-save')+btn('Cancel','back'));break;
  case 'delete-confirm':html=dialog(`Delete “${state.profile}”?`,'This removes only this profile from the browser demo.','',btn('Delete profile','delete-confirm')+btn('Cancel','back'));break;
  case 'avatar-choice':html=dialog(current.label,'',`<div class="settings-list" data-row="avatar-choice">${current.options.map(label=>btn(label,'set-avatar-choice',`data-value="${esc(label)}"`)).join('')}</div>`,btn('Cancel','back'));break;
  case 'settings':html=dialog('Your connections','',`<div class="settings-list" data-row="settings">${['Emby · connected','Twitch · connected','Refresh content','Disconnect Emby','Disconnect Twitch','Open original Google TV','Cache settings',`Live previews - ${state.preview?'muted':'off'}`].map((label,i)=>`<button data-setting="${i}">${esc(label)}</button>`).join('')}</div>`,btn('Close','back'));break;
  case 'cache':html=dialog('Artwork cache','Keep artwork ready for your next visit.',`<div class="settings-list" data-row="cache">${['24 hours','3 days','7 days','Clear artwork cache'].map(x=>btn(x,'demo-cache',`data-value="${x}"`)).join('')}</div>`,btn('Close','back'));break;
  case 'search':html=dialog(state.tab==='requests'?'Search for something to watch':'Search','',`<form id="demo-search-form"><input class="search-input" aria-label="${state.tab==='requests'?'Movie or TV show':'Search your Emby library'}" placeholder="${state.tab==='requests'?'Movie or TV show':'Search your Emby library'}" value="${esc(state.query)}"><div class="dialog-actions" data-row="search-actions"><button class="tv-button" type="submit">Search</button><button class="tv-button" type="button" data-action="back">Cancel</button></div></form>`);break;
  case 'detail':html=detailMarkup(media);break;
  case 'seasons':{const seasons=media.seasons||[];current.selected??=new Set();html=dialog('Request seasons of '+media.title,seasons.length?'':'Season information was not captured for this title.',`<div class="season-options" data-row="seasons">${seasons.map(n=>`<button data-season="${n}" aria-pressed="${current.selected.has(n)}">${current.selected.has(n)?'☑':'☐'} &nbsp; Season ${n}</button>`).join('')}</div>`,btn('Continue','season-confirm')+btn('Cancel','back'));break;}
  case 'confirm':html=dialog(`Request “${media.title}”?`,current.scope||'Movie','<p>Demo request only. Nothing is sent to Jellyseerr.</p>',btn('Send demo request','send-demo')+btn('Cancel','back'));break;
  case 'playback':html=dialog(current.title,'A recording from the installed APK. Playback here stays inside the demo.',`<video class="playback-demo" controls autoplay muted playsinline src="${current.video}"></video>`,btn('Back to Vue TV','back'));break;
  case 'app-actions':html=dialog(current.app.title,'Arrange your apps.',`<div class="settings-list" data-row="app-actions">${btn('Move app','move-app')}${btn('Open app','open-demo-app')}</div>`,btn('Cancel','back'));break;
  case 'notice':html=dialog(current.title,current.copy,'',btn('Back','back'));break;
 }
 overlay.innerHTML=html;
 if(['settings','cache'].includes(current.type))overlay.querySelector('.tv-dialog')?.classList.add('connections-dialog');
 overlay.querySelectorAll('#avatar-preview').forEach(img=>img.src=avatarImage());
 if(current.type==='editor')updateAvatar();
 const target=overlay.querySelector(current.type==='editor'?'[data-action=save-profile]':current.type==='search'?'.search-input':'button, input');
 target?.focus({preventScroll:true});
 if(current.type==='search')target?.select();
}
function detailMarkup(media){
 if(media.tmdb===data.featuredDetail.tmdb&&media.type==='movie')media={...media,...data.featuredDetail};
 const available=media.available===true, scores=media.scores||{};
 const score=(label,value,color='')=>`<div class="score"><strong${color?` style="color:${color}"`:''}>${esc(value)}</strong><small>${label}</small></div>`;
 let numbers=media.rating?score('TMDB audience',Number(media.rating).toFixed(1)+' / 10'):'';
 if(scores.rt?.criticsScore!=null)numbers+=score('RT critics',Math.round(scores.rt.criticsScore)+'%','#a8dab5');
 if(scores.rt?.audienceScore!=null)numbers+=score('RT audience',Math.round(scores.rt.audienceScore)+'%');
 if(scores.imdb?.criticsScore!=null)numbers+=score('IMDb',Number(scores.imdb.criticsScore).toFixed(1)+' / 10','#f4d582');
 let actions=available?btn('Watch in Emby','watch-emby'):'';
 if(media.type==='tv'&&media.status!==5&&media.status!==6)actions+=btn('Choose seasons','seasons');
 else if(media.type==='movie'&&!available&&![2,3,6].includes(media.status))actions+=btn('Request movie','confirm');
 actions+=btn('Watch trailer','trailer')+btn('Close','back');
 return `<section class="title-detail" role="dialog" aria-modal="true" aria-label="${esc(media.title)}" style="background-image:url('${esc(media.image)}')"><div class="detail-shade"></div>${media.poster?`<img class="detail-poster" src="${esc(media.poster)}" alt="${esc(media.title)} poster">`:''}<div class="detail-copy"><span class="status-label">${esc(status(media))}</span><h1 style="font-size:${media.title.length>35?32:40}px">${esc(media.title)}</h1><p class="detail-meta">${esc([media.year,media.minutes?`${Math.floor(media.minutes/60)?Math.floor(media.minutes/60)+'h ':''}${media.minutes%60}m`:media.type==='tv'?'TV show':'Movie',...(media.genres||[])].filter(Boolean).join('  ·  '))}</p><div class="score-row">${numbers}</div><p class="ratings-note">${scores.rt?'Rotten Tomatoes (RT) · scores shown when available':'TMDB audience score from the captured library.'}</p>${media.tagline?`<p class="detail-tagline">${esc(media.tagline)}</p>`:''}<p class="detail-overview">${esc(media.overview||'A synopsis is not available for this title yet.')}</p>${media.cast?.length?`<p class="detail-cast">Starring &nbsp; ${esc(media.cast.join(', '))}</p>`:''}</div><div class="detail-actions" data-row="detail-actions">${actions}</div></section>`;
}
function activate(button){
 const current=state.dialogs.at(-1);
 if(button.dataset.tab){switchTab(button.dataset.tab);return;}
 if(button.dataset.media){showMedia(item(button.dataset.media));return;}
 if(button.dataset.app){const app=apps.find(a=>a.key===button.dataset.app);open('notice',{title:app.title,copy:'On the TV, this opens the installed '+app.title+' app. This website keeps you in the demo.'});return;}
 if(button.dataset.setting){const n=Number(button.dataset.setting);if(n===6)open('cache');else if(n===7){state.preview=!state.preview;renderOverlay();toast(`Live previews ${state.preview?'enabled (muted)':'disabled'} in this demo.`);}else if(n===2){toast('Demo snapshot refreshed.');}else open('notice',{title:button.textContent,copy:'Connections and device settings stay on the TV. This demo uses the APK’s exported display data.'});return;}
 if(button.dataset.avatarChoice){const label=button.dataset.avatarChoice;open('avatar-choice',{label,options:['From username',...VueAvatar.options[VueAvatar.fields[label]]]});return;}
 if(button.dataset.season){const n=Number(button.dataset.season);current.selected.has(n)?current.selected.delete(n):current.selected.add(n);button.setAttribute('aria-pressed',String(current.selected.has(n)));button.innerHTML=`${current.selected.has(n)?'☑':'☐'} &nbsp; Season ${n}`;return;}
 switch(button.dataset.action){
  case 'hero':showMedia(heroPool()[state.banner%heroPool().length]);break;
  case 'profiles':open('profiles');break;
  case 'choose-profile':selectProfile(Number(button.dataset.profile));state.dialogs=[];overlay.replaceChildren();renderPage();toast(`${state.profile} selected`);break;
  case 'create-profile':open('create-profile');break;
  case 'create-save':{const name=overlay.querySelector('input').value.trim();if(!name){toast('Enter a profile name.');return;}if(state.profiles.length>=12){toast('You can add up to 12 users.');return;}if(state.profiles.some(p=>p.name.toLowerCase()===name.toLowerCase())){toast('Choose a different profile name.');return;}saveActiveProfile();state.profiles.push({name,avatar:VueAvatar.empty()});selectProfile(state.profiles.length-1);state.dialogs=[];overlay.replaceChildren();renderPage();toast('Demo profile created in this session.');break;}
  case 'edit-profile':open('editor',{name:state.profile,previousStyle:{...state.avatarStyle}});break;
  case 'save-profile':{state.profile=overlay.querySelector('.profile-name').value.trim()||'Vue';saveActiveProfile();state.dialogs=[];overlay.replaceChildren();renderPage();toast('Demo profile saved for this session.');break;}
  case 'shuffle':{const variant=(state.avatarStyle.variant||0)+1;state.avatarStyle={...VueAvatar.empty(),variant};updateAvatar();break;}
  case 'reset-avatar':state.avatarStyle=VueAvatar.empty();updateAvatar();break;
  case 'set-avatar-choice':{const field=VueAvatar.fields[current.label],value=button.dataset.value;state.avatarStyle[field]=VueAvatar.options[field].indexOf(value);back();updateAvatar();break;}
  case 'delete-profile':if(state.profiles.length<=1)toast('Keep at least one profile. Create another first.');else open('delete-confirm');break;
  case 'delete-confirm':state.profiles.splice(state.profileIndex,1);state.profileIndex=0;state.profile=state.profiles[0].name;state.avatarStyle={...state.profiles[0].avatar};state.dialogs=[];overlay.replaceChildren();renderPage();toast('Demo profile deleted.');break;
  case 'settings':open('settings');break;
  case 'demo-cache':toast(button.dataset.value==='Clear artwork cache'?'Demo artwork is already local.':`Cache preference: ${button.dataset.value} (demo only)`);back();break;
  case 'search':open('search');break;
  case 'discover':state.query='';state.mine=false;renderPage();page.querySelector('[data-action=discover]').focus();break;
  case 'mine':state.query='';state.mine=true;renderPage();page.querySelector('[data-action=mine]').focus();break;
  case 'disconnect':open('notice',{title:'Sign out of Requests?',copy:'This is a local demo snapshot. No service account is connected to this website.'});break;
  case 'store':open('notice',{title:'Search for apps and games',copy:'On the TV, this opens the app store. Try holding an app icon to rearrange it in this demo.'});break;
  case 'watch-emby':open('notice',{title:current.media.title,copy:'On the TV, Watch in Emby opens this exact title in the installed Emby app. No app is launched from this website.'});break;
  case 'trailer':open('notice',{title:'Watch trailer',copy:'On the TV, this opens the title’s trailer in YouTube. The website keeps you inside the demo.'});break;
  case 'seasons':open('seasons',{media:current.media});break;
  case 'confirm':open('confirm',{media:current.media});break;
  case 'season-confirm':if(!current.selected.size){toast('Choose at least one season.');return;}open('confirm',{media:current.media,scope:'Seasons '+[...current.selected].sort((a,b)=>a-b).join(', ')});break;
  case 'send-demo':toast('Demo request confirmed. Nothing was sent.');state.dialogs=state.dialogs.filter(x=>x.type==='detail');renderOverlay();break;
  case 'move-app':state.move=current.app.key;state.dialogs=[];overlay.replaceChildren();renderPage(false);page.querySelector(`[data-app="${state.move}"].app-icon`)?.focus();toast('Left / Right to move. Enter to save. Escape to finish.');break;
  case 'open-demo-app':open('notice',{title:current.app.title,copy:'On your TV this launches the installed streaming app.'});break;
  case 'back':back();break;
 }
}
document.addEventListener('click',event=>{if(state.suppressClick){state.suppressClick=false;event.preventDefault();return;}const button=event.target.closest('button');if(button)activate(button);});
document.addEventListener('input',event=>{if(event.target.matches('.profile-name')){const editor=state.dialogs.find(d=>d.type==='editor');if(editor)editor.name=event.target.value;updateAvatar();}});
document.addEventListener('submit',event=>{if(event.target.id!=='demo-search-form')return;event.preventDefault();state.query=event.target.querySelector('input').value.trim();state.tab='requests';state.mine=false;state.dialogs=[];overlay.replaceChildren();renderPage();const first=page.querySelector('.media-card');first?.focus();});
page.addEventListener('focusin',event=>{
 const el=event.target;
 if(el.closest('.tv-nav')){setMode('normal');page.scrollTop=0;if(['home','live'].includes(state.tab))showHero(heroPool()[state.banner%Math.max(1,heroPool().length)]);return;}
 if(el.matches('.hero')){setMode('hero');page.scrollTop=0;showHero(heroPool()[state.banner%Math.max(1,heroPool().length)]);return;}
 if(el.dataset.media){const media=item(el.dataset.media);if(['home','live'].includes(state.tab)){setMode('card');showHero(media);}el.scrollIntoView({block:'nearest',inline:'nearest',behavior:'smooth'});return;}
 if(el.matches('.app-icon,.app-banner'))el.scrollIntoView({block:'nearest',inline:'nearest',behavior:'smooth'});
});
function move(direction){
 const focus=document.activeElement, current=state.dialogs.at(-1);
 if(state.move&&!current&&['left','right'].includes(direction)){
  const index=apps.findIndex(a=>a.key===state.move),next=Math.max(0,Math.min(apps.length-1,index+(direction==='right'?1:-1)));
  [apps[index],apps[next]]=[apps[next],apps[index]];renderPage(false);page.querySelector(`.app-icon[data-app="${state.move}"]`).focus();return;
 }
 if(focus?.classList.contains('hero')&&['left','right'].includes(direction)){
  const size=Math.min(3,heroPool().length);state.banner=(state.banner+(direction==='right'?1:size-1))%Math.max(1,size);showHero(heroPool()[state.banner]);page.querySelectorAll('.hero-dots i').forEach((dot,i)=>dot.classList.toggle('active',i===state.banner));return;
 }
 const root=current?overlay:page;
 const groups=[...root.querySelectorAll('[data-row]')].map(row=>(row.matches('button,input')?[row]:[...row.querySelectorAll('button:not(:disabled),input:not(:disabled)')]).filter(el=>el.getClientRects().length));
 if(current&&groups.every(group=>!group.length))groups.push([...root.querySelectorAll('button,input')]);
 if(focus?.matches('[data-row]')&&['button','input'].includes(focus.tagName.toLowerCase())){
  const idx=[...root.querySelectorAll('[data-row]')].indexOf(focus);if(idx>=0)groups[idx]=[focus];
 }
 let row=groups.findIndex(group=>group.includes(focus));
 if(row<0){(root.querySelector('button,input'))?.focus();return;}
 const column=groups[row].indexOf(focus);
 let target;
 if(['left','right'].includes(direction))target=groups[row][Math.max(0,Math.min(groups[row].length-1,column+(direction==='right'?1:-1)))];
 else{
  let next=row+(direction==='down'?1:-1);while(next>=0&&next<groups.length&&!groups[next].length)next+=direction==='down'?1:-1;
  if(next<0||next>=groups.length)return;
  const center=focus.getBoundingClientRect().x+focus.getBoundingClientRect().width/2;
  target=groups[next].reduce((best,el)=>Math.abs(el.getBoundingClientRect().x+el.getBoundingClientRect().width/2-center)<Math.abs(best.getBoundingClientRect().x+best.getBoundingClientRect().width/2-center)?el:best);
 }
 target?.focus({preventScroll:true});if(current)target?.scrollIntoView({block:'nearest',inline:'nearest'});
}
function remote(action){if(['left','right','up','down'].includes(action))move(action);else if(action==='ok'){if(state.move){state.move=null;toast('App order saved for this demo.');}else document.activeElement?.click();}else if(action==='back')back();else if(action==='home')switchTab('home');else if(action==='reset'){state.profile=data.profile?.name||'Vue';state.avatarStyle={...VueAvatar.empty(),...data.profile?.avatar};state.avatar=0;state.profileIndex=0;state.profiles=[{name:state.profile,avatar:{...state.avatarStyle}}];state.move=null;apps=appNames.map(([key,title])=>({key,title}));switchTab('home');}else if(['home','apps','live','requests'].includes(action))switchTab(action);}
document.addEventListener('keydown',event=>{
 if(event.key==='Tab'&&state.dialogs.length){const controls=[...overlay.querySelectorAll('button,input')],index=controls.indexOf(document.activeElement),next=(index+(event.shiftKey?-1:1)+controls.length)%controls.length;event.preventDefault();controls[next]?.focus();return;}
 if(event.target.matches('input')&&!['Escape','ArrowUp','ArrowDown'].includes(event.key))return;
 const actions={ArrowLeft:'left',ArrowRight:'right',ArrowUp:'up',ArrowDown:'down',Escape:'back',Backspace:'back',Home:'home'};
 if(actions[event.key]){event.preventDefault();remote(actions[event.key]);}
 if(event.key==='Enter'&&state.move){event.preventDefault();remote('ok');}
 if(event.key==='ContextMenu'||(event.shiftKey&&event.key==='F10')){const app=apps.find(a=>a.key===document.activeElement.dataset.app);if(app){event.preventDefault();open('app-actions',{app});}}
});
let holdTimer;
page.addEventListener('pointerdown',event=>{const el=event.target.closest('.app-icon');if(el)holdTimer=setTimeout(()=>{holdTimer=null;state.suppressClick=true;open('app-actions',{app:apps.find(a=>a.key===el.dataset.app)});setTimeout(()=>state.suppressClick=false,1500);},600);});
for(const name of ['pointerup','pointercancel','pointerleave'])page.addEventListener(name,()=>{if(holdTimer){clearTimeout(holdTimer);holdTimer=null;}});
page.addEventListener('contextmenu',event=>{const el=event.target.closest('.app-icon');if(el){event.preventDefault();open('app-actions',{app:apps.find(a=>a.key===el.dataset.app)});}});
window.addEventListener('message',event=>{if(event.source!==parent||event.origin!==location.origin||event.data?.type!=='vue-demo-control')return;remote(event.data.action);});
fetch('demo-data.json').then(response=>{if(!response.ok)throw Error('Demo data unavailable');return response.json();}).then(snapshot=>{data=snapshot;state.profile=data.profile?.name||'Vue';state.avatarStyle={...VueAvatar.empty(),...data.profile?.avatar};$('#loading').remove();renderPage();parent.postMessage({type:'vue-demo-ready'},location.origin);}).catch(()=>{$('#loading').textContent='The demo could not load. Please refresh the page.';});
