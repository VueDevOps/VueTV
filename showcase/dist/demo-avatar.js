// Browser port of AvatarStyle.java and AvatarView.java from the installed app.
window.VueAvatar=(()=>{
 const options={background:['Ocean','Orchid','Mint','Sunset','Gold','Rose'],skin:['Porcelain','Peach','Warm','Golden','Brown','Deep'],hair:['Crop','Swoop','Curls','Bun','Bald'],hairColor:['Midnight','Chocolate','Chestnut','Blond','Silver','Plum'],eyes:['Friendly','Happy','Calm','Wink'],accessory:['None','Glasses','Headphones','Sparkle']};
 const fields={'Background':'background','Skin tone':'skin','Hair':'hair','Hair color':'hairColor','Eyes':'eyes','Accessory':'accessory'};
 const palettes=[['#174869','#65bed2'],['#45386a','#b19fe8'],['#215c58','#86d5ad'],['#804e3d','#e5ad83'],['#6e5c26','#e8cc71'],['#6b3b59','#dfa4c7']];
 const skins=['#f6ddcd','#edbea2','#dba57e','#c38b60','#996347','#654435'],hairs=['#25313c','#483329','#94563e','#d9b86f','#c2c9ce','#704c79'];
 const salts={background:11,skin:29,hair:47,hairColor:71,eyes:97,accessory:137};
 const empty=()=>({variant:0,background:-1,skin:-1,hair:-1,hairColor:-1,eyes:-1,accessory:-1});
 function mix(v){v^=v>>>16;v=Math.imul(v,0x7feb352d);v^=v>>>15;v=Math.imul(v,0x846ca68b);return (v^(v>>>16))|0;}
 function resolve(name,style=empty()){
  let hash=0;for(const c of name.trim().toLowerCase())hash=(Math.imul(hash,31)+c.charCodeAt(0))|0;
  const seed=mix(hash^Math.imul(style.variant||0,0x9e3779b9));const result={};
  for(const key of Object.keys(options)){const count=options[key].length,value=style[key]??-1;result[key]=value>=0&&value<count?value:((mix(seed^salts[key])%count)+count)%count;}
  return result;
 }
 function render(name,style){
  const chosen=resolve(name,style), canvas=document.createElement('canvas');canvas.width=320;canvas.height=320;const c=canvas.getContext('2d');c.scale(3.2,3.2);c.beginPath();c.arc(50,50,50,0,Math.PI*2);c.clip();
  const palette=palettes[chosen.background],skin=skins[chosen.skin],hair=hairs[chosen.hairColor],cut=chosen.hair,accessory=chosen.accessory;
  const gradient=c.createLinearGradient(0,0,100,100);gradient.addColorStop(0,palette[1]);gradient.addColorStop(1,palette[0]);c.fillStyle=gradient;c.fillRect(0,0,100,100);
  function oval(color,l,t,r,b){c.fillStyle=color;c.beginPath();c.ellipse((l+r)/2,(t+b)/2,(r-l)/2,(b-t)/2,0,0,Math.PI*2);c.fill();}
  function rect(color,l,t,r,b,radius){c.fillStyle=color;c.beginPath();c.roundRect(l,t,r-l,b-t,Math.min(radius,(r-l)/2,(b-t)/2));c.fill();}
  function stroke(color,width){c.strokeStyle=color;c.lineWidth=width;c.lineCap='round';}
  function line(x,y,x2,y2){c.beginPath();c.moveTo(x,y);c.lineTo(x2,y2);c.stroke();}
  function arc(l,t,r,b,start,sweep){c.beginPath();c.ellipse((l+r)/2,(t+b)/2,(r-l)/2,(b-t)/2,0,start*Math.PI/180,(start+sweep)*Math.PI/180);c.stroke();}
  oval('#ffffff1c',-16,-20,54,46);oval('#ffffff12',69,62,119,112);
  if(cut===3){oval(hair,24,16,76,70);oval(hair,39,4,61,26);}else if(cut!==4)oval(hair,25,15,75,61);
  oval(skin,23,43,35,59);oval(skin,65,43,77,59);rect(skin,42,64,58,86,7);rect(palette[1],15,78,85,120,28);oval(skin,39,72,61,86);
  stroke('#ffffff40',2);line(26,91,26,104);line(74,91,74,104);oval(skin,28,20,72,76);
  if(cut===0){rect(hair,28,18,72,33,12);rect(hair,27,27,33,44,3);rect(hair,67,27,73,44,3);}
  else if(cut===1){c.fillStyle=hair;c.beginPath();c.moveTo(26,41);c.bezierCurveTo(22,8,59,7,75,25);c.bezierCurveTo(68,43,48,36,39,29);c.bezierCurveTo(34,38,29,41,26,41);c.fill();}
  else if(cut===2){for(let i=0;i<6;i++){const x=30+i*8;oval(hair,x-7,13+(i%2)*3,x+7,31+(i%2)*3);}rect(hair,26,27,32,43,3);rect(hair,68,27,74,43,3);}
  else if(cut===3){stroke(hair,7);arc(28,17,72,49,180,180);}
  stroke(hair,1.8);line(34,40,42,39);line(58,39,66,40);stroke('#23303a',2.3);
  if(chosen.eyes===1){arc(34,45,43,53,185,170);arc(57,45,66,53,185,170);}
  else if(chosen.eyes===2){line(35,49,42,49);line(58,49,65,49);}
  else{oval('#23303a',36,45,41,52);oval('#ffffffbf',37,46,38.5,48);if(chosen.eyes===3){stroke('#23303a',2.3);arc(57,45,66,53,185,170);}else{oval('#23303a',59,45,64,52);oval('#ffffffbf',60,46,61.5,48);}}
  oval('#cf617225',30,54,40,61);oval('#cf617225',60,54,70,61);stroke('#7d483c55',1.4);c.beginPath();c.moveTo(50,49);c.lineTo(48,56);c.quadraticCurveTo(50,58,53,56);c.stroke();stroke('#874e4b',2);arc(41,56,59,68,12,156);
  if(accessory===1){stroke('#263646',2);for(const l of [30,54]){c.beginPath();c.roundRect(l,42,16,13,4);c.stroke();}line(46,47,54,47);line(26,46,30,46);line(70,46,74,46);}
  else if(accessory===2){stroke('#dde8f0',4);arc(22,19,78,77,180,180);rect('#33495e',20,43,29,63,4);rect('#33495e',71,43,80,63,4);rect('#d3e3ef',21,46,25,60,2);rect('#d3e3ef',75,46,79,60,2);}
  else if(accessory===3){stroke('#ffdf82',2);line(77,24,77,36);line(71,30,83,30);line(73,26,81,34);line(73,34,81,26);}
  return canvas.toDataURL('image/png');
 }
 return {options,fields,empty,resolve,render};
})();
