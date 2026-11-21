const tour = [
  { key: 'home', label: 'Home', kicker: 'FAMILIAR, FROM THE FIRST CLICK', title: 'Everything you actually watch. Together.', description: 'Your Emby library, live follows and favourite apps share one familiar home. Click the menu in this real capture to explore another view.', views: [{file:'home',label:'Home',alt:'Vue TV Home screen with Home, Apps, Live and Requests navigation'}] },
  { key: 'picks', label: 'Top picks', kicker: 'THE BIG-SCREEN TREATMENT', title: 'Give your next watch the spotlight.', description: 'Full artwork, a proper synopsis, ratings and the details that matter. Move between these real captures and the whole screen follows.', views: [{file:'picks',label:'The Dark Knight',alt:'Vue TV Top picks showing The Dark Knight with its full-screen backdrop and ratings'},{file:'goodfellas',label:'GoodFellas',alt:'The selected GoodFellas recommendation in Vue TV'},{file:'western',label:'The Good, the Bad and the Ugly',alt:'The selected western recommendation in Vue TV'}] },
  { key: 'apps', label: 'Your apps', kicker: 'MAKE ROOM FOR YOUR FAVOURITES', title: 'Your apps. In your order.', description: 'Open the apps you already use. Hold an app to move it, arrange your favourites, and keep that order saved for your profile.', views: [{file:'apps',label:'Apps',alt:'The actual Vue TV Apps screen showing installed streaming apps'},{file:'app-glow',label:'Focus glow',alt:'A focused app icon in Vue TV with its circular glow'}] },
  { key: 'live', label: 'Live follows', kicker: 'YOUR PEOPLE, WHEN THEY’RE LIVE', title: 'Catch them while they’re on.', description: 'Live from your follows brings in the Twitch channels you follow, with real thumbnails and streamer name tags. The list refreshes every minute. Select a channel to watch it directly in S0undTV. Twitch sign-in now renews automatically.', views: [{file:'live',label:'Live',alt:'Vue TV Live tab showing a currently live followed Twitch channel'},{file:'follows',label:'Name tags',alt:'The Live from your follows row with live badges and streamer names'}] },
  { key: 'requests', label: 'Requests', kicker: 'YOUR NEXT GREAT WATCH', title: 'Explore first. Request when you’re ready.', description: 'Browse and search through Jellyseerr. Open a title for its story, cast, available scores and trailer. Requests and season choices are confirmed before sending.', views: [{file:'requests',label:'Discover',alt:'Vue TV Requests with Trending now and Popular movies'},{file:'request-details',label:'Title details',alt:'Vue TV movie request details showing artwork, Rotten Tomatoes scores, synopsis, cast and a Watch trailer button'}] },
  { key: 'profiles', label: 'Profiles', kicker: 'A LITTLE MORE YOU', title: 'A home for everyone on the sofa.', description: 'Switch users, connect your own services and style your username-generated avatar. Hold a user or choose Edit profile to make it yours.', views: [{file:'profiles',label:'User picker',alt:'Vue TV user picker with Create user, Edit profile and VueSettings'},{file:'profile-editor',label:'Edit profile',alt:'Vue TV avatar editor with a live preview and style choices'},{file:'profile-custom',label:'Another look',alt:'A different avatar preview created inside the real Vue TV profile editor'}] }
];
let selectedKey = 'picks';
let selectedView = 0;
let imageTicket = 0;
let galleryIndex = 0;
let dialogReturnFocus = null;
const $ = selector => document.querySelector(selector);
const $$ = selector => [...document.querySelectorAll(selector)];
const panel = $('#tour-panel');
const stage = $('#tour-screen');
const image = $('#tour-image');
const modal = $('#media-dialog');
const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
const source = file => `assets/${file}.webp`;
const current = () => tour.find(section => section.key === selectedKey);
const allViews = tour.flatMap(section => section.views.map((view, index) => ({...view, key:section.key, index, section:section.label})));

function renderHotspots(section, view) {
  const host = $('#screen-hotspots');
  host.replaceChildren();
  const add = (label, x, y, w, h, key, index = 0) => {
    const button = document.createElement('button');
    button.className = 'hotspot';
    button.type = 'button';
    button.setAttribute('aria-label', label);
    Object.assign(button.style, {left:`${x}%`, top:`${y}%`, width:`${w}%`, height:`${h}%`});
    const tip = document.createElement('span'); tip.className = 'hotspot-tooltip'; tip.textContent = label; button.append(tip);
    button.addEventListener('click', () => { selectView(key, index); panel.focus({preventScroll:true}); });
    host.append(button);
  };
  if (['home','live'].includes(section.key) && view === 0) {
    add('Explore profiles',6,6.2,4.4,7.5,'profiles');
    add('Explore Home',17,6.5,7,7,'home');
    add('Explore Apps',24,6.5,6.5,7,'apps');
    add('Explore Live',30.5,6.5,6.2,7,'live');
    add('Explore Requests',36.7,6.5,10.5,7,'requests');
  }
  if (['apps','requests'].includes(section.key) && view === 0) {
    add('Explore profiles',6,6.2,4.4,7.5,'profiles');
    add('Explore Home',17,6.5,7,7,'home');
    if (section.key === 'requests') add('Explore Apps',24,6.5,6.5,7,'apps');
  }
  if (section.key === 'picks') {
    add('Select The Dark Knight',24,66,16,17,'picks',0);
    add('Select GoodFellas',42,66,16,17,'picks',1);
    add('Select The Good, the Bad and the Ugly',60,66,16,17,'picks',2);
  }
  if (section.key === 'requests' && view === 0) add('See title details',6,44,16,17,'requests',1);
  if (section.key === 'profiles' && view === 0) add('Open Edit profile',25,68,12,9,'profiles',1);
  if (section.key === 'profiles' && view === 1) add('See another avatar preview',12.8,53.5,12,8,'profiles',2);
}

function selectView(key, index = 0, immediate = false) {
  const section = tour.find(item => item.key === key);
  if (!section) return;
  selectedKey = key;
  selectedView = Math.max(0, Math.min(index, section.views.length - 1));
  const view = section.views[selectedView];
  $$('.tour-menu [role=tab]').forEach(tab => {
    const selected = tab.dataset.tour === key;
    tab.setAttribute('aria-selected', String(selected));
    tab.tabIndex = selected ? 0 : -1;
  });
  panel.setAttribute('aria-labelledby', `tab-${key}`);
  $('#tour-kicker').textContent = section.kicker;
  $('#tour-title').textContent = section.title;
  $('#tour-description').textContent = section.description;
  const variants = $('#screen-variants'); variants.replaceChildren();
  if (section.views.length > 1) section.views.forEach((entry, i) => {
    const button = document.createElement('button');
    button.textContent = String(i + 1); button.title = entry.label;
    button.setAttribute('aria-label', `Show ${entry.label}`);
    button.setAttribute('aria-pressed', String(i === selectedView));
    button.addEventListener('click', () => selectView(key, i)); variants.append(button);
  });
  const run = ++imageTicket;
  const next = new Image();
  const replace = () => {
    if (run !== imageTicket) return;
    image.src = source(view.file); image.alt = view.alt;
    renderHotspots(section, selectedView);
    requestAnimationFrame(() => stage.classList.remove('changing'));
  };
  if (!immediate && !reducedMotion.matches) stage.classList.add('changing');
  next.onload = () => setTimeout(replace, immediate || reducedMotion.matches ? 0 : 130);
  next.onerror = () => {stage.classList.remove('changing');};
  next.src = source(view.file);
  if (immediate && next.complete) replace();
}

function moveView(delta) {
  const index = allViews.findIndex(view => view.key === selectedKey && view.index === selectedView);
  const next = allViews[(index + delta + allViews.length) % allViews.length];
  selectView(next.key, next.index);
}

$$('[data-tour]').forEach(button => button.addEventListener('click', () => selectView(button.dataset.tour)));
$('.tour-menu').addEventListener('keydown', event => {
  if (!['ArrowDown','ArrowUp','Home','End'].includes(event.key)) return;
  event.preventDefault();
  const tabs = $$('.tour-menu [role=tab]');
  const index = tabs.indexOf(document.activeElement);
  const nextIndex = event.key === 'Home' ? 0 : event.key === 'End' ? tabs.length - 1 : (index + (event.key === 'ArrowDown' ? 1 : -1) + tabs.length) % tabs.length;
  selectView(tabs[nextIndex].dataset.tour); tabs[nextIndex].focus();
});
panel.addEventListener('keydown', event => {
  if (event.target !== panel) return;
  if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {event.preventDefault();moveView(event.key === 'ArrowRight' ? 1 : -1);}
  if (event.key === 'Enter') {event.preventDefault();openCapture(selectedKey, selectedView);}
});
$('#remote-prev').addEventListener('click', () => moveView(-1));
$('#remote-next').addEventListener('click', () => moveView(1));
$('#remote-home').addEventListener('click', () => selectView('home'));
$('#remote-open').addEventListener('click', () => openCapture(selectedKey, selectedView));
$('#expand-screen').addEventListener('click', () => openCapture(selectedKey, selectedView));

function openDialog(title) {
  dialogReturnFocus = document.activeElement;
  $('#dialog-title').textContent = title;
  if (!modal.open) modal.showModal();
  document.body.style.overflow = 'hidden';
}

function galleryImage(index) {
  galleryIndex = (index + allViews.length) % allViews.length;
  const entry = allViews[galleryIndex];
  $('#dialog-title').textContent = `${entry.section} · ${entry.label}`;
  const picture = document.createElement('img'); picture.src = source(entry.file); picture.alt = entry.alt; picture.className = 'dialog-image'; picture.width = 1920; picture.height = 1080;
  $('#dialog-content').replaceChildren(picture);
  const footer = $('#dialog-footer'); footer.replaceChildren();
  tour.forEach(section => {
    const button = document.createElement('button');button.textContent = section.label;button.setAttribute('aria-pressed', String(section.key === entry.key));button.addEventListener('click', () => galleryImage(allViews.findIndex(view => view.key === section.key)));footer.append(button);
  });
  const previous = document.createElement('button');previous.textContent = 'Previous';previous.addEventListener('click', () => galleryImage(galleryIndex - 1));
  const next = document.createElement('button');next.textContent = 'Next';next.addEventListener('click', () => galleryImage(galleryIndex + 1));footer.prepend(previous);footer.append(next);
}

function openCapture(key, index = 0) {
  const actualKey = key === 'details' ? 'requests' : key;
  const actualIndex = key === 'details' ? 1 : index;
  const entry = allViews.findIndex(view => view.key === actualKey && view.index === actualIndex);
  if (entry < 0) return;
  openDialog('Vue TV screenshots'); galleryImage(entry);
}

$$('[data-open-capture]').forEach(button => button.addEventListener('click', () => openCapture(button.dataset.openCapture)));
$('#open-gallery').addEventListener('click', () => openCapture('home'));
$$('[data-video]').forEach(button => button.addEventListener('click', () => {
  const cinematic = button.dataset.video === 'cinematic';
  openDialog(cinematic ? 'Cinematic picks ? 1080p' : 'Vue TV navigation ? 1080p');
  const video = document.createElement('video'); video.className = 'dialog-video'; video.controls = true; video.autoplay = true; video.playsInline = true; video.muted = true; video.loop = true; video.poster = source('picks');
  const clip = document.createElement('source'); clip.src = cinematic ? 'assets/cinematic-hq.mp4' : 'assets/navigation-hq.mp4';clip.type = 'video/mp4';video.append(clip);
  $('#dialog-content').replaceChildren(video);
  $('#dialog-footer').textContent = 'Full HD ? Recorded directly on a Google TV Streamer.';
  video.play().catch(() => {});
}));
$('#close-dialog').addEventListener('click', () => modal.close());
modal.addEventListener('click', event => {if (event.target === modal) {const box=modal.getBoundingClientRect();if(event.clientX<box.left||event.clientX>box.right||event.clientY<box.top||event.clientY>box.bottom)modal.close();}});
modal.addEventListener('close', () => {const video = modal.querySelector('video');if(video)video.pause();document.body.style.overflow = '';dialogReturnFocus?.focus({preventScroll:true});});
modal.addEventListener('keydown', event => {if(modal.querySelector('video'))return;if(event.key==='ArrowLeft'||event.key==='ArrowRight'){event.preventDefault();galleryImage(galleryIndex+(event.key==='ArrowRight'?1:-1));}});

$('#avatar-compare').addEventListener('input', event => {
  const position = Number(event.target.value);
  $('#compare-after').style.clipPath = `inset(0 0 0 ${position}%)`;
  $('#compare-line').style.left = `${position}%`;
});
const compareStage = $('#compare-stage');
let comparePointer = null;
compareStage.querySelectorAll('img').forEach(picture => {picture.draggable = false;});
compareStage.addEventListener('dragstart', event => event.preventDefault());
compareStage.addEventListener('pointerdown', event => {
  if (!event.isPrimary || event.button !== 0) return;
  event.preventDefault();
  comparePointer = event.pointerId;
  compareStage.setPointerCapture(event.pointerId);
  updateCompare(event);
});
compareStage.addEventListener('pointermove', event => {
  if (event.pointerId === comparePointer) updateCompare(event);
});
compareStage.addEventListener('pointerup', event => {
  if (event.pointerId !== comparePointer) return;
  updateCompare(event);
  comparePointer = null;
  if (compareStage.hasPointerCapture(event.pointerId)) compareStage.releasePointerCapture(event.pointerId);
});
compareStage.addEventListener('pointercancel', () => {comparePointer = null;});
compareStage.addEventListener('lostpointercapture', () => {comparePointer = null;});
function updateCompare(event){const rect=compareStage.getBoundingClientRect();if(!rect.width)return;const value=Math.round(Math.max(0,Math.min(100,(event.clientX-rect.left)/rect.width*100)));$('#avatar-compare').value=String(value);$('#avatar-compare').dispatchEvent(new Event('input'));}
$$('[data-fact]').forEach(button => button.addEventListener('click', () => button.setAttribute('aria-expanded',String(button.getAttribute('aria-expanded') !== 'true'))));

document.documentElement.classList.add('js');
if ('IntersectionObserver' in window) {
  const observer = new IntersectionObserver(entries => entries.forEach(entry => {if(entry.isIntersecting){entry.target.classList.add('visible');observer.unobserve(entry.target);}}), {threshold:.08});
  $$('.reveal').forEach(element => observer.observe(element));
} else $$('.reveal').forEach(element => element.classList.add('visible'));
const display = $('#hero-display');
const device = $('.hero-device');
let pendingFrame = false;
function scrollScene(){if(pendingFrame||reducedMotion.matches)return;pendingFrame=true;requestAnimationFrame(()=>{if(window.scrollY<display.offsetTop+display.offsetHeight+100){const rotation=Math.max(0,5-window.scrollY/140);device.style.transform=`rotateX(${rotation}deg)`;}pendingFrame=false;});}
window.addEventListener('scroll',scrollScene,{passive:true});
device.addEventListener('pointermove',event=>{if(reducedMotion.matches||event.pointerType==='touch')return;const box=device.getBoundingClientRect();device.style.setProperty('--mx',`${(event.clientX-box.left)/box.width*100}%`);device.style.setProperty('--my',`${(event.clientY-box.top)/box.height*100}%`);});
selectView('picks',0,true);
// Idle preloading keeps the screenshot tour responsive without delaying the hero.
const warm = () => allViews.forEach(view=>{const preload=new Image();preload.src=source(view.file);});
if('requestIdleCallback' in window)window.requestIdleCallback(warm,{timeout:3000});else setTimeout(warm,1500);
