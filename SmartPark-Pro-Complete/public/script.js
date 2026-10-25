const $ = (id) => document.getElementById(id);
async function loadVehicleTypes() {
  try {
    const response = await fetch("/config.json", { cache: "no-store" });
    if (!response.ok) throw new Error("Could not load vehicle configuration");
    const config = await response.json();
    const select = $("typeInput");
    if (!select || !Array.isArray(config.vehicleTypes)) return;
    select.replaceChildren(new Option("Select vehicle type", ""));
    config.vehicleTypes.forEach(type => select.add(new Option(type, type)));
  } catch (error) {
    console.warn("Using vehicle types already present in the HTML.", error);
  }
}

let currentState = null;
let activeView = "overview";
let noticeTimer = null;

const money = n => new Intl.NumberFormat("en-IN",{style:"currency",currency:"INR",maximumFractionDigits:0}).format(Number(n)||0);
const escapeHtml = value => String(value ?? "").replace(/[&<>"']/g, ch => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[ch]));
function showNotice(message, kind="success") {
  const box=$("notice"); box.textContent=message; box.className="notice"+(kind==="error"?" error":kind==="warning"?" warning":"");
  clearTimeout(noticeTimer); noticeTimer=setTimeout(()=>box.classList.add("hidden"),6000);
}
async function request(url, options={}) {
  const response=await fetch(url,{cache:"no-store",...options});
  const type=response.headers.get("content-type")||"";
  if(type.includes("application/json")) {
    const data=await response.json();
    if(!response.ok || data.success===false) throw new Error(data.message||"Request failed.");
    return data;
  }
  if(!response.ok) throw new Error("Request failed. Check that the Java server is running.");
  return response;
}
function postForm(url, form) {
  return request(url,{method:"POST",headers:{"Content-Type":"application/x-www-form-urlencoded;charset=UTF-8"},body:new URLSearchParams(new FormData(form)).toString()});
}
function go(view) {
  activeView=view;
  document.querySelectorAll(".view").forEach(el=>el.classList.add("hidden"));
  $("view-"+view).classList.remove("hidden");
  document.querySelectorAll(".nav-item").forEach(el=>el.classList.toggle("active",el.dataset.view===view));
  const names={overview:["Overview","Parking overview"],register:["Register vehicle","Register a vehicle"],vehicles:["Parked vehicles","Parked vehicles"],exit:["Vehicle exit","Vehicle exit"],search:["Search vehicle","Search vehicle"],reports:["Reports & history","Reports & history"]};
  $("crumbTitle").textContent=names[view][0]; $("pageTitle").textContent=names[view][1];
  $("notice").classList.add("hidden");
  if(view==="search") $("searchInput").focus();
  if(view==="register") $("plateInput").focus();
}
document.querySelectorAll("[data-view]").forEach(btn=>btn.addEventListener("click",()=>go(btn.dataset.view)));
document.querySelectorAll("[data-go]").forEach(btn=>btn.addEventListener("click",()=>go(btn.dataset.go)));
$("refreshBtn").addEventListener("click",()=>refreshState(true));
$("tableRefresh").addEventListener("click",()=>refreshState(true));
$("historyRefresh").addEventListener("click",()=>refreshState(true));
$("tableFilter").addEventListener("input",renderVehicles);

async function refreshState(showMessage=false) {
  try {
    currentState=await request("/api/state");
    renderAll();
    if(showMessage) showNotice("Dashboard refreshed with the latest server data.");
  } catch(e) {
    showNotice(e.message+" Make sure you ran `java Main` in VS Code.", "error");
  }
}
function renderAll() {
  if(!currentState)return;
  const s=currentState;
  $("totalMetric").textContent=s.totalSlots;
  $("availableMetric").textContent=s.available;
  $("occupiedMetric").textContent=s.occupied;
  $("revenueMetric").textContent=money(s.revenue);
  $("registerAvailable").textContent=s.available;
  $("registerWaiting").textContent=s.waiting.length;
  $("recordMetric").textContent=s.history.length;
  $("exitMetric").textContent=s.history.filter(r=>r.action==="EXIT").length;
  $("waitingMetric").textContent=s.waiting.length;
  $("historyCount").textContent=`${s.history.length} records`;
  $("slotSummary").textContent=`${s.available} of ${s.totalSlots} spaces available`;
  renderSlots(); renderActivity(); renderVehicles(); renderHistory();
  if(activeView==="exit") previewExit(false);
}
function renderSlots() {
  const occupied=new Map(currentState.vehicles.map(v=>[v.slot,v]));
  $("slotGrid").innerHTML=currentState.slots.map(n=>{
    const slot=`P-${String(n).padStart(2,"0")}`,v=occupied.get(slot);
    return `<div class="slot ${v?"occupied":""}" title="${v?escapeHtml(v.plate+" · "+v.type):"Available"}"><strong>${slot}</strong><small>${v?escapeHtml(v.plate):"AVAILABLE"}</small></div>`;
  }).join("");
}
function renderActivity() {
  const rows=currentState.history.slice(0,5);
  $("recentActivity").innerHTML=rows.length?rows.map(r=>{
    const isExit=r.action==="EXIT",isWait=r.action==="WAITLIST";
    return `<div class="activity-row"><div class="activity-icon ${isExit?"blue":isWait?"amber":"green"}">${isExit?"↗":isWait?"◷":"＋"}</div><div class="activity-copy"><b>${escapeHtml(isExit?"Vehicle exit processed":isWait?"Vehicle added to waiting queue":"Vehicle registered")} · ${escapeHtml(r.plate)}</b><small>${escapeHtml(r.type)}${r.slot&&r.slot!=="Waiting queue"?" · "+escapeHtml(r.slot):""}${isExit?" · "+money(r.fee):""}</small></div><span class="activity-time">${escapeHtml(r.time)}</span></div>`;
  }).join(""):`<div class="empty-state">No activity recorded yet. Register a vehicle to get started.</div>`;
}
function renderVehicles() {
  if(!currentState)return;
  const filter=($("tableFilter")?.value||"").trim().toLowerCase();
  const rows=currentState.vehicles.filter(v=>[v.plate,v.type,v.owner,v.slot].some(x=>String(x||"").toLowerCase().includes(filter)));
  $("vehicleCount").textContent=`${currentState.vehicles.length} vehicle${currentState.vehicles.length===1?"":"s"}`;
  $("vehicleRows").innerHTML=rows.length?rows.map(v=>`<tr><td><div class="vehicle-primary">${escapeHtml(v.plate)}</div><div class="vehicle-secondary">${escapeHtml(v.owner||"Owner not provided")}</div></td><td><span class="type-pill">${escapeHtml(v.type)}</span></td><td><b>${escapeHtml(v.slot)}</b></td><td>${escapeHtml(v.entryTime)}</td><td>${v.minutes<60?`${v.minutes} min`:`${Math.floor(v.minutes/60)}h ${v.minutes%60}m`}</td><td><b>${money(v.estimatedFee)}</b></td><td><button class="row-action" data-exit="${escapeHtml(v.plate)}">Check out →</button></td></tr>`).join(""):`<tr><td colspan="7" class="empty-cell">${filter?"No vehicles match your filter.":"No vehicles currently parked."}</td></tr>`;
  document.querySelectorAll("[data-exit]").forEach(btn=>btn.addEventListener("click",()=>{$("exitPlate").value=btn.dataset.exit;go("exit");previewExit(true);}));
}
function renderHistory() {
  $("historyRows").innerHTML=currentState.history.length?currentState.history.map(r=>`<tr><td>${escapeHtml(r.time)}</td><td><span class="action-pill ${r.action.toLowerCase()}">${escapeHtml(r.action==="ENTRY"?"Vehicle entry":r.action==="EXIT"?"Vehicle exit":"Waiting queue")}</span></td><td><b>${escapeHtml(r.plate)}</b></td><td>${escapeHtml(r.type)}</td><td>${escapeHtml(r.slot||"—")}</td><td>${r.action==="EXIT"?money(r.fee):"—"}</td></tr>`).join(""):`<tr><td colspan="6" class="empty-cell">No activity recorded yet. Your records will appear here.</td></tr>`;
}
$("registerForm").addEventListener("submit",async e=>{
  e.preventDefault();
  try {
    const data=await postForm("/api/register",e.currentTarget);
    e.currentTarget.reset(); await refreshState(false); showNotice(data.message,data.message.includes("waiting queue")?"warning":"success"); go("overview");
  } catch(err){showNotice(err.message,"error");}
});
$("exitForm").addEventListener("submit",async e=>{
  e.preventDefault();
  try {
    const data=await postForm("/api/exit",e.currentTarget);
    e.currentTarget.reset(); $("exitPreview").classList.add("hidden"); await refreshState(false); showNotice(data.message); go("overview");
  } catch(err){showNotice(err.message,"error");}
});
async function previewExit(showError=true) {
  const plate=$("exitPlate").value.trim();
  if(!plate){$("exitPreview").classList.add("hidden");return;}
  try {
    const data=await request("/api/search?q="+encodeURIComponent(plate));
    const v=data.results.find(item=>item.plate.toUpperCase()===plate.toUpperCase());
    if(!v) { $("exitPreview").classList.add("hidden"); if(showError)showNotice("No active parking session found for that registration number.","error"); return; }
    $("exitPreview").classList.remove("hidden");
    $("exitPreview").innerHTML=`<b>${escapeHtml(v.plate)} · ${escapeHtml(v.type)}</b><p>Parking bay: <strong>${escapeHtml(v.slot)}</strong><br>Entry time: ${escapeHtml(v.entryTime)}<br>Current duration: ${v.minutes} minute(s)<br>Estimated fee: <strong>${money(v.estimatedFee)}</strong></p>`;
  } catch(err){if(showError)showNotice(err.message,"error");}
}
$("previewExit").addEventListener("click",()=>previewExit(true));
$("exitPlate").addEventListener("change",()=>previewExit(true));
$("searchForm").addEventListener("submit",async e=>{
  e.preventDefault(); const q=$("searchInput").value.trim();
  if(!q){showNotice("Enter a registration number or owner name to search.","warning");return;}
  try {
    const data=await request("/api/search?q="+encodeURIComponent(q));
    $("searchResults").innerHTML=data.results.length?data.results.map(v=>`<div class="result-card"><div class="result-mark">P</div><div class="result-info"><b>${escapeHtml(v.plate)}</b><p>${escapeHtml(v.type)} · ${escapeHtml(v.owner||"Owner not provided")} · Bay ${escapeHtml(v.slot)}</p></div><div class="result-meta"><b>${money(v.estimatedFee)}</b><small>estimated fee · ${v.minutes} min</small></div><button class="btn btn-light" data-search-exit="${escapeHtml(v.plate)}">Exit</button></div>`).join(""):`<div class="empty-state">No currently parked vehicles matched “${escapeHtml(q)}”.</div>`;
    document.querySelectorAll("[data-search-exit]").forEach(btn=>btn.addEventListener("click",()=>{$("exitPlate").value=btn.dataset.searchExit;go("exit");previewExit(true);}));
  } catch(err){showNotice(err.message,"error");}
});
$("clock").textContent=new Intl.DateTimeFormat("en-IN",{dateStyle:"medium",timeStyle:"short"}).format(new Date());
setInterval(()=>$("clock").textContent=new Intl.DateTimeFormat("en-IN",{dateStyle:"medium",timeStyle:"short"}).format(new Date()),30000);
loadVehicleTypes();
refreshState(false);
setInterval(()=>refreshState(false),15000);
