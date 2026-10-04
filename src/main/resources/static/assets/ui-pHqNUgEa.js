const b={success:"fa-circle-check",error:"fa-circle-xmark",warning:"fa-triangle-exclamation",info:"fa-circle-info"};let i=null;function k(){return i&&document.body.contains(i)||(i=document.createElement("div"),i.className="toast-host",i.setAttribute("role","status"),i.setAttribute("aria-live","polite"),document.body.appendChild(i)),i}function g(m,l="success"){const d=k(),n=document.createElement("div");n.className=`toast ${l}`,n.innerHTML=`<i class="fas ${b[l]||b.info}"></i><span></span>`,n.querySelector("span").textContent=String(m??""),d.appendChild(n);const f=setTimeout(()=>u(),2800);function u(){clearTimeout(f),n.classList.add("leaving"),setTimeout(()=>n.remove(),240)}n.addEventListener("click",u)}function x({title:m="提示",message:l="",okText:d="确定",cancelText:n="取消",type:f="info"}={}){return new Promise(u=>{const t=document.createElement("div");t.className="modal-overlay",t.setAttribute("role","dialog"),t.setAttribute("aria-modal","true");const v={info:{cls:"fa-circle-info",color:"var(--info)"},warning:{cls:"fa-triangle-exclamation",color:"var(--warning)"},error:{cls:"fa-circle-xmark",color:"var(--danger)"},success:{cls:"fa-circle-check",color:"var(--success)"}},e=v[f]||v.info;t.innerHTML=`
      <div class="modal-box confirm-box">
        <div class="confirm-icon" style="color:${e.color}">
          <i class="fas ${e.cls}"></i>
        </div>
        <h2 class="confirm-title"></h2>
        <p class="confirm-msg"></p>
        <div class="confirm-actions">
          <button type="button" class="btn btn-outline confirm-cancel"></button>
          <button type="button" class="btn btn-primary confirm-ok"></button>
        </div>
      </div>
    `,t.querySelector(".confirm-title").textContent=m,t.querySelector(".confirm-msg").textContent=l,t.querySelector(".confirm-cancel").textContent=n,t.querySelector(".confirm-ok").textContent=d;const c=t.querySelector(".confirm-ok"),y=t.querySelector(".confirm-cancel");function a(r){t.classList.add("leaving"),setTimeout(()=>{t.remove(),document.removeEventListener("keydown",s)},180),u(r)}function s(r){r.key==="Escape"&&a(!1),r.key==="Enter"&&a(!0)}c.addEventListener("click",()=>a(!0)),y.addEventListener("click",()=>a(!1)),t.addEventListener("click",r=>{r.target===t&&a(!1)}),document.addEventListener("keydown",s),document.body.appendChild(t),c.focus()})}function E({title:m="提示",message:l="",placeholder:d="",defaultValue:n="",okText:f="确定",cancelText:u="取消",required:t=!1}={}){return new Promise(v=>{const e=document.createElement("div");e.className="modal-overlay",e.setAttribute("role","dialog"),e.setAttribute("aria-modal","true"),e.innerHTML=`
      <div class="modal-box confirm-box">
        <h2 class="confirm-title"></h2>
        <p class="confirm-msg"></p>
        <textarea class="form-input prompt-input" rows="3"></textarea>
        <div class="confirm-actions">
          <button type="button" class="btn btn-outline confirm-cancel"></button>
          <button type="button" class="btn btn-primary confirm-ok"></button>
        </div>
      </div>
    `,e.querySelector(".confirm-title").textContent=m,e.querySelector(".confirm-msg").textContent=l,e.querySelector(".confirm-cancel").textContent=u,e.querySelector(".confirm-ok").textContent=f;const c=e.querySelector(".prompt-input");c.placeholder=d,c.value=n;const y=e.querySelector(".confirm-ok"),a=e.querySelector(".confirm-cancel");function s(o){e.classList.add("leaving"),setTimeout(()=>{e.remove(),document.removeEventListener("keydown",r)},180),v(o)}function r(o){if(o.key==="Escape"&&s(null),o.key==="Enter"&&(o.ctrlKey||o.metaKey)){const p=c.value.trim();if(t&&!p)return;s(p||null)}}y.addEventListener("click",()=>{const o=c.value.trim();if(t&&!o){g("请填写内容","warning"),c.focus();return}s(o||null)}),a.addEventListener("click",()=>s(null)),e.addEventListener("click",o=>{o.target===e&&s(null)}),document.addEventListener("keydown",r),document.body.appendChild(e),c.focus()})}export{x as c,E as p,g as s};
