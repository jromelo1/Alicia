/* Aviso de cookies (solo informativo: el sitio usa únicamente almacenamiento estrictamente
   necesario, así que no hace falta pedir permiso). Se muestra una vez por navegador. */
(function(){
  var KEY='cookieNotice';
  try{if(localStorage.getItem(KEY))return}catch(e){}
  var TXT={
    es:{t:'Usamos solo lo necesario para que la página funcione (tu idioma, tu sesión y una verificación anti-abuso de Google). Sin publicidad ni rastreo.',l:'Más información',b:'Entendido'},
    en:{t:'We only use what the site needs to work (your language, your session and a Google anti-abuse check). No ads, no tracking.',l:'Learn more',b:'Got it'}
  };
  var css='.ck{position:fixed;left:50%;bottom:16px;transform:translateX(-50%);z-index:80;width:min(640px,calc(100% - 32px));background:#1d1d1f;color:#f5f5f7;border-radius:16px;padding:14px 16px;display:flex;gap:12px;align-items:center;box-shadow:0 12px 40px rgba(0,0,0,.25);font:14px/1.45 "Geist","Helvetica Neue",Helvetica,system-ui,sans-serif}'+
    '.ck p{margin:0;flex:1}.ck a{color:#f0c9b6;text-decoration:underline;text-underline-offset:2px;white-space:nowrap}'+
    '.ck button{flex:0 0 auto;min-height:40px;padding:0 18px;border:0;border-radius:999px;background:#fff;color:#1d1d1f;font:inherit;font-weight:600;cursor:pointer}'+
    '@media (max-width:520px){.ck{flex-direction:column;align-items:stretch}.ck button{width:100%}}';
  var st=document.createElement('style');st.textContent=css;document.head.appendChild(st);
  var box=document.createElement('div');box.className='ck';box.setAttribute('role','region');
  var p=document.createElement('p'),a=document.createElement('a'),btn=document.createElement('button');
  a.href='cookies.html';btn.type='button';
  box.appendChild(p);box.appendChild(btn);
  function render(){var t=TXT[document.documentElement.lang==='en'?'en':'es'];box.setAttribute('aria-label','Cookies');
    p.textContent=t.t+' ';a.textContent=t.l;p.appendChild(a);btn.textContent=t.b}
  render();
  new MutationObserver(render).observe(document.documentElement,{attributes:true,attributeFilter:['lang']});
  btn.addEventListener('click',function(){try{localStorage.setItem(KEY,'1')}catch(e){}box.remove()});
  document.body.appendChild(box);
})();
