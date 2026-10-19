/* First-party Javity/ORBIT adapter. Authentication remains in the parent launcher. */
(() => {
  'use strict';
  const embedded = window.parent !== window;
  const config = globalThis.JAVITY_ORBIT_CONFIG;
  const sdk = embedded ? Orbit.createGameSdk({launcherOrigins: config.launcherOrigins}) : null;
  let closed = false, revision = 0, pendingRestore = null, loaded = false, busy = false;
  const state = {mode: embedded ? 'connecting' : 'standalone', player: null, revision: 0, javaPlayer: null};
  const unavailable = () => Object.assign(new Error('Uruchom aplikację z launchera ORBIT'), {code:'NOT_IN_LAUNCHER'});
  const methods = Object.assign(Object.create(null), {
    'player.get': () => sdk.player.get(),
    'saves.get': p => sdk.saves.get(p.slot),
    'saves.put': p => sdk.saves.put(p.slot,p.data,{expectedRevision:p.expectedRevision}),
    'assets.list': p => sdk.assets.list(p.cursor ? {cursor:p.cursor} : {}),
    'assets.getUrl': p => sdk.assets.getUrl(p.assetId),
    'achievements.unlock': p => sdk.achievements.unlock(p.key),
    'multiplayer.list': () => sdk.multiplayer.list(),
    'multiplayer.create': p => sdk.multiplayer.create(p),
    'multiplayer.join': p => sdk.multiplayer.join(p.roomId,p.inviteCode ? {inviteCode:p.inviteCode} : {}),
    'multiplayer.close': p => sdk.multiplayer.close(p.roomId),
    'leaderboard.list': p => sdk.leaderboard.list(p.mode)
  });
  const ready = sdk ? sdk.ready().then(() => sdk.player.get()).then(player => {
    if(closed)throw Object.assign(Error('Adapter closed'),{code:'CLOSED'});
    state.mode='connected';state.player=player;return player;
  }) : Promise.resolve(null);
  void ready.catch(() => {if(!closed)state.mode='unavailable';});
  const request = async(method,params={}) => {
    if(closed)throw Object.assign(Error('Adapter closed'),{code:'CLOSED'});
    if(!sdk)throw unavailable();
    if(!Object.hasOwn(methods,method) || !params || typeof params!=='object' || Array.isArray(params))throw Object.assign(Error('Unsupported ORBIT request'),{code:'INVALID_REQUEST'});
    await ready;
    if(closed)throw Object.assign(Error('Adapter closed'),{code:'CLOSED'});
    return methods[method](params);
  };
  function dispose(){if(closed)return;closed=true;state.mode='closed';pendingRestore=null;sdk?.dispose();}
  globalThis.JavityOrbit=Object.freeze({enabled:embedded,ready:()=>ready,request,state,
    takeRestore(){const value=pendingRestore;pendingRestore=null;return value;},
    reportJavaPlayer(json){if(!closed)state.javaPlayer=JSON.parse(json);},dispose});
  window.addEventListener('pagehide',dispose,{once:true});
  function attach(){
    if(!embedded || closed)return;
    const panel=document.createElement('aside');panel.id='orbit-panel';
    Object.assign(panel.style,{position:'fixed',right:'18px',top:'18px',zIndex:'1000',background:'#102032ed',color:'#e0f1ff',padding:'14px',borderRadius:'12px',font:'14px system-ui'});
    const label=document.createElement('p');label.textContent='Łączenie z ORBIT…';label.setAttribute('role','status');
    const save=document.createElement('button');save.textContent='Zapisz w chmurze';save.disabled=true;
    const load=document.createElement('button');load.textContent='Wczytaj zapis';load.disabled=true;
    panel.append(label,save,load);document.body.append(panel);
    const buttons=()=>{save.disabled=busy||!loaded||closed;load.disabled=busy||closed;};
    const status=text=>{if(!closed)label.textContent=text;};
    const loadGame=async()=>{
      const value=await request('saves.get',{slot:'progress'});
      if(closed)return;
      if(value){const data=value.data;
        if(data?.kind!=='javity-demo-position-v1'||!Number.isFinite(data.x)||!Number.isFinite(data.z)||Math.abs(data.x)>10||Math.abs(data.z)>10||!Number.isSafeInteger(value.revision)||value.revision<1)throw Error('Zapis ma nieobsługiwany format');
        revision=value.revision;pendingRestore={x:data.x,z:data.z};
      }else revision=0;
      loaded=true;state.revision=revision;status(value?'Wczytano zapis z ORBIT':'Nowa rozgrywka — '+state.player.nickname);
    };
    const operation=async(fn)=>{if(busy||closed)return;busy=true;buttons();try{await fn();}catch(error){status(error.message);}finally{busy=false;buttons();}};
    save.onclick=()=>operation(async()=>{
      const snapshot=globalThis.javityWasm;
      if(!snapshot||snapshot.disposed||!Number.isFinite(snapshot.x)||!Number.isFinite(snapshot.z))throw Error('Scena nie jest gotowa');
      const value=await request('saves.put',{slot:'progress',data:{kind:'javity-demo-position-v1',x:snapshot.x,z:snapshot.z},expectedRevision:revision});
      if(closed)return;revision=value.revision;state.revision=revision;status('Zapisano w ORBIT · rewizja '+revision);
    });
    load.onclick=()=>operation(loadGame);
    ready.then(()=>operation(loadGame)).catch(error=>status(error.message));
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',attach,{once:true});else attach();
})();
