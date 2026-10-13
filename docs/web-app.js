import {planBankCsv} from "./bank-csv.mjs";
import {pdfStatementToCsv,pdfFileToLines} from "./pdf-statement.mjs";
import {emptyData,strictData,cents,nzd,iso,month,nextId,recordDue,totals,csv,CATEGORIES} from "./web-core.mjs";
import {normalizeLoginSession,sessionInvalidated,loadLoginSession,saveLoginSession,clearLoginSession} from "./auth-session.mjs";
import {decideSync} from "./sync-policy.mjs";
import {writeProfile} from "./profile-store.mjs";

const $=s=>document.querySelector(s);
const escape=s=>String(s??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const today=()=>{const d=new Date();return [d.getFullYear(),String(d.getMonth()+1).padStart(2,"0"),String(d.getDate()).padStart(2,"0")].join("-");};
const clockMonth=()=>today().slice(0,7);
const prettyDate=s=>new Date(s+"T12:00:00").toLocaleDateString("en-NZ",{day:"numeric",month:"short",year:"numeric"});
const amounts=["#06785b","#e4ac54","#5a9ed8","#ac89e5","#eb8769","#73c7be","#8aa56c"];
const pages=["home","transactions","budgets","goals","bills","export","account","banking","admin"];
const label=p=>({home:"Home",transactions:"Transactions",budgets:"Budgets",goals:"Savings goals",bills:"Bills",export:"Export & backup",account:"Account",banking:"Bank connections",admin:"Admin dashboard"}[p]||"Home");
let profile="guest",data=emptyData(),page="home",selectedMonth=clockMonth(),auth=null,config=null,busy=false,searchTerm="";
let toastTimer,modalKind="",modalTarget=0;
let adminOverview=null;
let refreshInFlight=null;
let cloudMeta=null,cloudBusy=false,cloudPending=false,cloudStatus="Cloud sync is off";
let profileEpoch=0;
let financialSaving=false;
const editor=$("#editor"),view=$("#view");
const toast=(message)=>{const el=$("#toast");el.textContent=message;el.classList.add("show");clearTimeout(toastTimer);toastTimer=setTimeout(()=>el.classList.remove("show"),4500);};
const errorMessage=e=>toast(e&&e.message?e.message:"Something went wrong.");
function dbOpen(){
 return new Promise((resolve,reject)=>{
  const req=indexedDB.open("MoneyMateNZWeb",2);
  req.onupgradeneeded=()=>{if(!req.result.objectStoreNames.contains("profiles"))req.result.createObjectStore("profiles");if(!req.result.objectStoreNames.contains("sync"))req.result.createObjectStore("sync");};
  req.onerror=()=>reject(req.error);
  req.onsuccess=()=>resolve(req.result);
 });
}
async function dbRead(id){
 const db=await dbOpen();
 try {const stored=await new Promise((resolve,reject)=>{const tx=db.transaction("profiles","readonly");const q=tx.objectStore("profiles").get(id);q.onsuccess=()=>resolve(q.result);q.onerror=()=>reject(q.error);});
 return stored?strictData(stored):emptyData();}finally{db.close();}
}
async function dbWrite(id,next,options){
 const db=await dbOpen();
 try{return await writeProfile(db,id,next,options);}
 finally{db.close();}
}
async function dbErase(id){
 const db=await dbOpen();try{await new Promise((resolve,reject)=>{const tx=db.transaction("profiles","readwrite");tx.objectStore("profiles").delete(id);tx.oncomplete=resolve;tx.onerror=()=>reject(tx.error);tx.onabort=()=>reject(tx.error);});}finally{db.close();}
}
async function save(next){
 if(financialSaving)throw Error("Please wait for the previous save to finish.");
 const safe=strictData(next);
 financialSaving=true;
 const owner=profile,epoch=profileEpoch,previous=data;data=safe;
 try{await dbWrite(owner,safe,{expected:previous,current:()=>profile===owner&&profileEpoch===epoch});}
 catch(error){
  const persisted=await dbRead(owner).catch(()=>previous);
  if(profileEpoch===epoch&&data===safe)data=persisted;
  render();throw error;
 }
 finally{financialSaving=false;}
 if(profileEpoch!==epoch)return;
 render();
 if(cloudMeta?.enabled&&auth?.user?.id===owner){cloudStatus="Saved locally · cloud check pending";void syncCloud(false);}
}
async function switchOwner(id){
 const epoch=++profileEpoch,owner=id||"guest";
 editor.close();
 profile=id||"guest";cloudMeta=null;cloudStatus="Cloud sync is off";cloudPending=false;
 const stored=await dbRead(owner);
 if(profileEpoch!==epoch)return;
 const result=recordDue(stored,today());
 if(result.created)await dbWrite(owner,result.data,{expected:stored,current:()=>profileEpoch===epoch});
 if(profileEpoch!==epoch)return;
 data=result.data;searchTerm="";selectedMonth=clockMonth();page="home";render();
}
function empty(icon,heading,detail){
 return '<div class="empty"><div class="empty-art" aria-hidden="true">'+icon+'</div><strong>'+escape(heading)+'</strong><span>'+escape(detail)+'</span></div>';
}
function heading(title,desc){return '<p class="eyebrow">MONEYMATE NZ · PERSONAL FINANCE</p><h1 class="page-heading">'+escape(title)+'</h1><p class="lede">'+escape(desc)+'</p>';}
function btn(text,action,variant="primary",id=""){
 return '<button type="button" class="'+variant+(variant==="ghost"||variant==="danger"?" mini-action":"")+'" data-action="'+escape(action)+'"'+(id!==""?' data-id="'+escape(id)+'"':"")+'>'+escape(text)+'</button>';
}
function progress(amount,target){
 const percent=target?Math.min(100,Math.round(amount/target*100)):(amount?100:0);
 return '<div class="progress" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow="'+percent+'"><i class="'+(amount>target?"over":"")+'" style="width:'+percent+'%"></i></div>';
}
function monthChange(delta){
 const [y,m]=selectedMonth.split("-").map(Number);
 const date=new Date(Date.UTC(y,m-1+delta,1));
 selectedMonth=date.toISOString().slice(0,7);render();
}
function home(){
 const t=totals(data,selectedMonth);
 const upcoming=data.bills.filter(x=>x.due>=today()).sort((a,b)=>a.due.localeCompare(b.due)).slice(0,5);
 const overdue=data.bills.filter(x=>x.due<today()).sort((a,b)=>a.due.localeCompare(b.due)).slice(0,5);
 const mLabel=new Date(selectedMonth+"-01T12:00:00").toLocaleDateString("en-NZ",{month:"long",year:"numeric"});
 let markup=heading("Your money, made clear","A little clarity. A little more confidence. All amounts in New Zealand dollars.");
 markup+='<div class="section-row"><span class="tag">'+escape(mLabel)+'</span><div class="btns" style="margin:0">'
   +btn("← Previous","month-prev","secondary")+btn("Current month","month-now","secondary")+btn("Next →","month-next","secondary")+'</div></div>';
 markup+='<section class="hero"><p class="eyebrow">AVAILABLE THIS MONTH · NZD</p><div class="balance">'+nzd(t.remaining)+'</div><div class="hero-bottom"><div><span>Income</span><strong>'+nzd(t.income)+'</strong></div><div><span>Expenses</span><strong>'+nzd(t.expense)+'</strong></div><div><span>Entries</span><strong>'+data.transactions.filter(x=>month(x.date)===selectedMonth).length+'</strong></div></div></section>';
 markup+='<div class="btns">'+btn("+ Add income","income")+btn("+ Add expense","expense","secondary")+'</div>';
 markup+='<div class="grid-2"><section class="panel"><h2>Spending categories</h2>';
 if(!t.categories.length)markup+=empty("◉","Make spending visible","Add an expense to see your spending chart.");
 else{
   let percentage=0;const pieces=t.categories.map(([name,value],i)=>{const start=percentage;percentage+=value/t.expense*100;return amounts[i%amounts.length]+" "+start+"% "+percentage+"%";});
   markup+='<div class="chart-wrap"><div class="donut" style="background:conic-gradient('+pieces.join(",")+')"><div class="donut-label">Spent<strong>'+nzd(t.expense)+'</strong></div></div><div class="legend">'
    +t.categories.map(([name,value],i)=>'<div class="legend-row"><span><i class="legend-dot" style="background:'+amounts[i%amounts.length]+'"></i>'+escape(name)+'</span><strong>'+nzd(value)+'</strong></div>').join("")+'</div></div>';
 }
 markup+='</section><section class="panel"><h2>Upcoming bills</h2>';
 if(!upcoming.length)markup+=empty("▦","A clear view of what's next","Add bills and due dates to plan ahead.");
 else markup+='<div class="rows">'+upcoming.map(b=>'<div class="entry"><div class="entry-text"><div class="entry-title">'+escape(b.name)+'</div><div class="entry-sub">'+escape(prettyDate(b.due))+'</div></div><div class="entry-end amount">'+nzd(b.cents)+'</div></div>').join("")+'</div>';
 markup+='<div class="btns">'+btn("Manage bills","go-bills","secondary")+'</div></section></div>';
 if(overdue.length)markup+='<section class="panel" style="margin-top:20px"><h2>⚠ Overdue bills</h2><div class="rows">'+overdue.map(b=>'<div class="entry"><div><strong>'+escape(b.name)+'</strong><div class="entry-sub">Due '+escape(prettyDate(b.due))+'</div></div><span class="tag danger-tag">'+nzd(b.cents)+'</span></div>').join("")+'</div></section>';
 return markup;
}
function renderRecords(){
 const term=searchTerm.trim().toLowerCase();
 const rows=data.transactions.filter(x=>!term||[x.category,x.note,x.kind,x.date].some(v=>v.toLowerCase().includes(term)))
   .slice().sort((a,b)=>b.date.localeCompare(a.date)||b.id-a.id).slice(0,300);
 $("#transaction-records").innerHTML=rows.length?'<div class="rows">'+rows.map(x=>'<div class="entry"><div class="entry-text"><div class="entry-title">'+escape(x.category)+'</div><div class="entry-sub">'+escape(x.kind)+' · '+escape(prettyDate(x.date))+(x.note?" · "+escape(x.note):"")+'</div></div><div class="entry-end"><div class="amount '+(x.kind==="Income"?"positive":"negative")+'">'+(x.kind==="Income"?"+":"−")+nzd(x.cents)+'</div><div class="row-actions">'+btn("Delete","delete-transaction","danger",x.id)+'</div></div></div>').join("")+'</div>'
   :empty("⇄",term?"No matches":"Your story starts here",term?"Try another search.":"Record your first income or expense.");
}
function transactions(){
 let markup=heading("Transactions","Track every dollar coming in and going out.");
 markup+='<div class="btns">'+btn("+ Income","income")+btn("+ Expense","expense","secondary")+btn("Add repeating entry","recurring","secondary")+btn("Import bank CSV (free)","bank-csv","secondary")+btn("Import bank PDF (free)","bank-pdf","secondary")+'</div>';
 markup+='<section class="panel"><div class="section-row" style="margin-top:0"><h2 style="margin:0">Transaction history</h2><span class="tag">Last 300 entries</span></div>'
 +'<label class="field"><span>Search date, category, type or note</span><input class="search" id="history-search" type="search" placeholder="Search transactions…" value="'+escape(searchTerm)+'"></label><div id="transaction-records"></div></section>';
 markup+='<section class="panel" style="margin-top:19px"><h2>Recurring schedules</h2>'
 +(data.recurring.length?'<div class="rows">'+data.recurring.slice().sort((a,b)=>a.next_date.localeCompare(b.next_date)).map(x=>'<div class="entry"><div><div class="entry-title">'+escape(x.category)+' · '+nzd(x.cents)+'</div><div class="entry-sub">'+escape(x.kind)+" · "+escape(x.frequency)+" · next "+escape(prettyDate(x.next_date))+'</div></div>'+btn("Stop","delete-recurring","danger",x.id)+'</div>').join("")+'</div>'
 :empty("↻","Build a steady routine","Set repeating income or expenses for weekly, fortnightly or monthly schedules."))+'</section>';
 return markup;
}
function budgets(){
 return heading("Budgets","Set monthly limits and watch your progress without the guesswork.")
 +'<div class="btns">'+btn("+ Set monthly budget","budget")+'</div>'
 +(data.budgets.length?'<div class="grid-2">'+data.budgets.map(x=>{
   const spent=data.transactions.filter(t=>t.kind==="Expense"&&t.category===x.category&&month(t.date)===selectedMonth).reduce((a,v)=>a+v.cents,0);
   return '<section class="panel"><div class="goal-head"><h3>'+escape(x.category)+'</h3><span class="tag '+(spent>x.cents?"danger-tag":"")+'">'+(spent>x.cents?"Over budget":"On track")+'</span></div><strong>'+nzd(spent)+'</strong> spent of '+nzd(x.cents)+progress(spent,x.cents)+'<div class="muted">'+(spent>x.cents?nzd(spent-x.cents)+" over":nzd(x.cents-spent)+" left")+'</div><div class="btns">'+btn("Adjust","edit-budget","secondary",x.category)+btn("Remove","delete-budget","danger",x.category)+'</div></section>';
 }).join("")+'</div>':empty("▤","Give every category a plan","Create a budget for groceries, rent, transport or anything else."));
}
function goals(){
 return heading("Savings goals","Make room for something good and see the progress you make.")
 +'<div class="btns">'+btn("+ New goal","goal")+'</div>'
 +(data.goals.length?'<div class="grid-2">'+data.goals.map(x=>'<section class="panel"><div class="goal-head"><h3>'+escape(x.name)+'</h3><span class="tag">'+Math.min(100,Math.round(x.saved/x.target*100))+'%</span></div><div class="muted">'+nzd(x.saved)+' saved of '+nzd(x.target)+'</div>'+progress(x.saved,x.target)+'<div class="btns">'+btn("+ Add savings","contribute","secondary",x.id)+btn("Remove","delete-goal","danger",x.id)+'</div></section>').join("")+'</div>'
 :empty("◎","Make room for something good","Create a savings goal and start tracking contributions."));
}
function bills(){
 const ordered=data.bills.slice().sort((a,b)=>a.due.localeCompare(b.due));
 return heading("Bills & due dates","Stay ahead of upcoming payments and record them when you've paid.")
 +'<div class="notice">MoneyMate NZ records bill payments in your budget. It does <strong>not</strong> transfer money or connect to your bank.</div>'
 +'<div class="btns">'+btn("+ Add bill","bill")+'</div>'
 +(ordered.length?'<div class="grid-2">'+ordered.map(x=>{
 const days=Math.round((Date.parse(x.due+"T00:00:00Z")-Date.parse(today()+"T00:00:00Z"))/86400000);
 const time=days<0?"Overdue by "+(-days)+" days":days===0?"Due today":days<=7?"Due in "+days+" days":"Due "+prettyDate(x.due);
 return '<section class="panel"><div class="goal-head"><h3>'+escape(x.name)+'</h3><span class="tag '+(days<0?"danger-tag":days<=7?"warn":"")+'">'+escape(time)+'</span></div><div class="amount">'+nzd(x.cents)+'</div><div class="muted">Due '+escape(prettyDate(x.due))+'</div><div class="btns">'+btn("Record as paid","pay-bill","primary",x.id)+btn("Remove","delete-bill","danger",x.id)+'</div></section>';
 }).join("")+'</div>':empty("▦","Stay ahead of due dates","Add your first bill to keep it in view."));
}
function exportPage(){
 return heading("Export & backup","Take control of your records and move them between your devices.")
 +'<div class="notice warning"><strong>Important:</strong> Signed-in users can enable secure syncing in Account to share transactions, budgets, goals and bills between Android and web. Guest records stay local. Keep a full JSON backup.</div>'
 +'<div class="grid-2"><section class="panel"><h2>Export your data</h2><p class="muted">Download a spreadsheet-compatible CSV or a complete MoneyMate NZ JSON backup of the current local profile.</p>'
 +'<div class="btns">'+btn("Export transactions CSV","csv")+btn("Save full JSON backup","backup","secondary")+'</div></section>'
 +'<section class="panel"><h2>Restore a full backup</h2><p class="muted">Import a JSON backup saved on this laptop or exported from Android. It replaces all entries in this local profile, after confirmation.</p>'
 +'<div class="btns">'+btn("Choose JSON file","import")+'</div></section></div>'
 +'<section class="panel" style="margin-top:20px"><h2>Your privacy choices</h2><p class="muted">Financial records remain in this browser. Browser storage can be cleared by your device or browser settings, so keep a secure backup. Account authentication uses Supabase separately.</p>'
 +'<div class="btns"><a class="secondary" href="./privacy.html">Read privacy policy ↗</a><a class="secondary" href="./delete-account.html">Account deletion ↗</a></div></section>';
}
function accountPage(){
 let html=heading("Your MoneyMate account","Sign in or use a local guest profile. Your personal financial data stays in this browser.");
 html+='<div class="notice warning">Your records stay local until you enable secure cloud sync. Once enabled on both devices, changes sync automatically when online. Conflicting edits on two offline devices require a manual choice; keep a JSON backup.</div>';
 if(!config)html+='<div class="notice">Connecting to secure account service… If unavailable, local budgeting still works.</div>';
 if(auth){
   html+='<div class="panel"><h2>Signed in</h2><p class="muted">'+escape(auth.user.email)+'</p><span class="tag">This browser profile</span><p class="muted" style="margin-top:14px">Financial records belong to your signed-in profile. Cloud syncing is optional.</p>'
    +'<div class="notice">'+escape(cloudStatus)+'</div><div class="btns">'+(cloudMeta?.enabled?btn("Sync now","cloud-sync","secondary"):btn("Enable secure cloud sync","cloud-enable","primary"))+(cloudStatus.toLowerCase().includes("conflict")?btn("Use cloud copy after backup","cloud-download","ghost"):"")+(localEmpty(data)?btn("Copy guest records","import-guest","secondary"):"")+btn("Bank connections","go-banking","secondary")+(adminOverview?btn("✦ Open Admin dashboard","go-admin","primary"):"")+btn("Sign out","signout","secondary")+btn("Delete account","delete-account","danger")+'</div></div>';
 }else{
   html+='<div class="account-grid"><section class="panel"><h2>Sign in</h2><form id="signin-form">'
    +formField("email","Email address","email","email",true,'autocomplete="username"')
    +formField("password","Password","password","password",true,'autocomplete="current-password"')
    +'<button class="primary" type="submit" '+(!config?"disabled":"")+'>Sign in</button></form>'
    +'<div class="btns">'+btn("Forgot password","recover","ghost")+(config?.googleEnabled?btn("Continue with Google","google-login","secondary"):"")+'</div></section>'
    +'<section class="panel"><h2>Create your account</h2><form id="signup-form">'
    +formField("new-email","Email address","email","email",true,'autocomplete="username"')
    +formField("new-password","Password (8–128 characters)","password","password",true,'autocomplete="new-password" minlength="8" maxlength="128"')
    +'<label class="field"><span><input type="checkbox" id="terms" required> I have read the <a href="./privacy.html">privacy policy</a>.</span></label>'
    +'<button class="primary" type="submit" '+(!config?"disabled":"")+'>Create account</button></form>'
    +'<div class="btns">'+(config?.googleEnabled?btn("Sign up with Google","google-login","secondary"):"")+btn("Confirm email code","confirm","secondary")+btn("Resend code","resend","ghost")+'</div></section></div>';
 }
 html+='<section class="panel" style="margin-top:20px"><h2>Local data & account safety</h2>'
  +'<p class="muted">When you sign out, account records stay in this browser but are hidden until you sign in again. The guest profile remains separately accessible. If you share a computer, use a private device account and a screen lock.</p>'
  +'<div class="btns">'+btn("Open Export & backup","go-export","secondary")+'</div></section>';
 return html;
}



function bankingPage(){
 return heading("Bank connections","Import NZD bank statements free, or check future NZ and Australian automatic bank connections.")
  +'<section class="panel"><h2>Free NZD statement imports · CSV or PDF</h2><p>Choose a bank CSV or text-based PDF statement downloaded from your bank. Neither needs a bank connection or paid provider. PDF extraction happens in your browser; PDF.js code downloads when you use it, and the file is never uploaded to a PDF service.</p><p class="muted">NZD only: AUD files are blocked until separate Australian-dollar budgets are supported. We suggest categories automatically, skip recognisable duplicate rows and ask before adding anything. Use the same account nickname when re-importing. Never upload bank passwords.</p><div class="btns">'+btn("Choose bank CSV file","bank-csv","primary")+btn("Choose bank PDF file","bank-pdf","secondary")+btn("Save a JSON backup","backup","secondary")+'</div></section>'
 +'<section class="panel"><h2>New Zealand banks · PDF format checks</h2><p><strong>ANZ NZ:</strong> specialised PDF statement checking for dates, withdrawals, deposits and running balances. <strong>ASB, BNZ, Westpac, Kiwibank, TSB, SBS, The Co-operative Bank, Heartland, Rabobank and other NZ banks:</strong> you may try a PDF or CSV with a clearly readable compatible transaction table. Bank-specific PDFs are not guaranteed until their formats are verified.</p><p class="muted">Unclear or scanned PDFs may be rejected rather than importing incorrect amounts. The Android app also attempts on-device OCR. Refer to the <a href="https://www.rbnz.govt.nz/regulation-and-supervision/cross-sector-oversight/registers-of-entities-we-regulate/registered-banks-in-new-zealand" target="_blank" rel="noopener noreferrer">RBNZ bank register ↗</a> for all registered banks.</p></section>'
 +'<div class="notice warning"><strong>Not available yet.</strong> Actual bank linking must await provider onboarding, security checks and user consent. Never enter banking passwords into MoneyMate NZ. Automatic bank transaction downloads are not available.</div>'
 +'<div class="grid-2"><section class="panel"><h2>🇳🇿 New Zealand</h2><p>Planned: Akahu official open-banking connection, depending on bank availability.</p><span class="tag">Provider approval required</span><p><a href="https://www.akahu.nz/" target="_blank" rel="noopener noreferrer">About Akahu ↗</a></p></section>'
 +'<section class="panel"><h2>🇦🇺 Australia</h2><p>Planned: Basiq Consumer Data Right account connection with bank-approved consent.</p><span class="tag">Provider approval required</span><p><a href="https://www.basiq.io/" target="_blank" rel="noopener noreferrer">About Basiq ↗</a></p></section></div>'
 +'<section class="panel"><h2>How connections will work</h2><p class="muted">You will choose a participating bank, approve read-only access with the bank or accredited provider, and allow imported transactions into your profile. You will be able to disconnect. Import deduplication and separate NZD/AUD currency handling must pass testing first. Do not enter banking credentials on this website.</p></section>';
}

function adminPage(){
 if(!auth||!adminOverview)return heading("Administrator access","Sign in with your approved administrator account to view this section.")
    +'<div class="notice warning">This dashboard requires a verified server-side admin role.</div>';
 const fmt=value=>Number.isSafeInteger(value)&&value>=0?value.toLocaleString("en-NZ"):"—";
 const cards=[
  ["Registered accounts",adminOverview.registered_users,"Total MoneyMate identities"],
  ["Confirmed accounts",adminOverview.verified_users,"Verified email identities"],
  ["Google accounts",adminOverview.google_users,"Accounts with Google as a provider"],
  ["Last 7 days",adminOverview.registrations_last_7_days,"New registrations"],
  ["Cloud-backed profiles",adminOverview.cloud_sync_accounts,"Accounts with a saved cloud snapshot"]
 ];
 const last=adminOverview.latest_registration_at?new Date(adminOverview.latest_registration_at):null;
 const lastText=last&&Number.isFinite(last.getTime())?last.toLocaleString("en-NZ"):"No registrations yet";
 return heading("Admin dashboard","MoneyMate NZ operational overview · authorised access only")
 +'<div class="notice"><strong>Administrator</strong> · '+escape(auth.user.email)+'<p style="margin:8px 0 0">Read-only account totals. No passwords, individual transaction histories, or customers’ financial values are exposed.</p></div>'
 +'<div class="admin-stats">'+cards.map(([name,value,desc])=>'<section class="panel admin-stat"><span class="admin-stat-title">'+escape(name)+'</span><strong>'+escape(fmt(value))+'</strong><p class="muted">'+escape(desc)+'</p></section>').join("")+'</div>'
 +'<div class="grid-2" style="margin-top:22px"><section class="panel"><h2>Account activity</h2><p class="muted">Latest registration</p><p><strong>'+escape(lastText)+'</strong></p><p class="muted">Metrics update when you refresh the panel.</p>'
 +'<div class="btns">'+btn("↻ Refresh statistics","admin-refresh","secondary")+'</div></section>'
 +'<section class="panel"><h2>Management links</h2><p class="muted">Changes to members and authentication happen in secured service consoles, not from this public website.</p>'
 +'<div class="admin-links"><a href="https://supabase.com/dashboard/project/efdegdlsrkxlnjgbxtep/auth/users" target="_blank" rel="noopener noreferrer">View accounts ↗</a>'
 +'<a href="https://supabase.com/dashboard/project/efdegdlsrkxlnjgbxtep" target="_blank" rel="noopener noreferrer">Database & authentication ↗</a>'
 +'<a href="https://github.com/FRYMASTERCHEESE/MoneyMate-NZ/actions" target="_blank" rel="noopener noreferrer">Website and Android builds ↗</a>'
 +'<a href="https://play.google.com/console/" target="_blank" rel="noopener noreferrer">Google Play Console ↗</a></div></section></div>'
 +'<p class="muted" style="margin-top:20px">This dashboard is protected by a server-verified admin role. Signed-out users and other account holders cannot query these statistics.</p>';
}

function formField(id,caption,type,name,required=false,extra=""){
 return '<label class="field"><span>'+escape(caption)+'</span><input id="'+escape(id)+'" name="'+escape(name)+'" type="'+escape(type)+'" '+(required?"required":"")+' '+extra+'></label>';
}
function render(){
 if(page==="admin"&&!(auth&&adminOverview))page="account";
 for(const navButton of document.querySelectorAll('[data-page="admin"]'))navButton.hidden=!(auth&&adminOverview);
 $("#profile-pill").textContent=auth?auth.user.email:"Local profile";
 $("#date-heading").textContent="Your money. Your plan.";
 for(const button of document.querySelectorAll("[data-page]"))button.setAttribute("aria-current",button.dataset.page===page?"page":"false");
 const body={home,transactions,budgets,goals,bills,export:exportPage,account:accountPage,banking:bankingPage,admin:adminPage}[page]||home;
 view.innerHTML=body();
 if(page==="transactions"){
   renderRecords();
   $("#history-search").addEventListener("input",e=>{searchTerm=e.target.value;renderRecords();});
 }
 if(page==="account")attachAuthForms();
}
function navigate(next){
 if(!pages.includes(next))next="home";
 if(next==="admin"&&!(auth&&adminOverview))next="account";
 page=next;location.hash=next;render();window.scrollTo(0,0);
}
function showModal(kind,id=0){
 modalKind=kind;modalTarget=id;
 const fields=$("#editor-fields");
 const choices=(key,options,defaultValue)=>{
   return '<label class="field"><span>'+escape(key)+'</span><select name="'+escape(key)+'">'+options.map(o=>'<option '+(o===defaultValue?"selected":"")+'>'+escape(o)+'</option>').join("")+'</select></label>';
 };
 const field=(key,title,type="text",value="",extra="required")=>'<label class="field"><span>'+escape(title)+'</span><input type="'+type+'" name="'+escape(key)+'" value="'+escape(value)+'" '+extra+'></label>';
 let title="",inner="",hint="";
 if(kind==="income"||kind==="expense"){
   title="Add "+(kind==="income"?"income":"expense");
   inner=field("category","Category", "text",kind==="income"?"Income":"Groceries")
    +field("note","Note (optional)","text","","")
    +field("amount","Amount in NZD","text","","inputmode=\"decimal\" required")
    +field("date","Date","date",today());
 }else if(kind==="recurring"){
   title="New repeating transaction";
   inner=choices("kind",["Expense","Income"],"Expense")
    +field("category","Category","text","Groceries")
    +field("note","Note (optional)","text","","")
    +field("amount","Amount in NZD","text","","inputmode=\"decimal\" required")
    +field("date","First date","date",today())
    +choices("frequency",["Weekly","Fortnightly","Monthly"],"Monthly");
   hint="Due entries will be added whenever you open the app or website. It does not make bank payments.";
 }else if(kind==="budget"){
   title="Set monthly budget";
   inner=field("category","Category","text","Groceries")
    +field("amount","Monthly limit in NZD","text","","inputmode=\"decimal\" required");
 }else if(kind==="edit-budget"){
   title="Adjust monthly budget";
   const item=data.budgets.find(x=>x.category===id);
   inner=field("category","Category","text",item.category,'readonly')
    +field("amount","Monthly limit in NZD","text",(item.cents/100).toFixed(2),'inputmode="decimal" required');
 }else if(kind==="goal"){
   title="New savings goal";
   inner=field("name","Goal name","text","Holiday")
    +field("amount","Target in NZD","text","","inputmode=\"decimal\" required");
 }else if(kind==="contribute"){
   title="Add savings";
   inner=field("amount","Savings contribution in NZD","text","","inputmode=\"decimal\" required");
 }else if(kind==="bill"){
   title="Add bill";
   inner=field("name","Bill name","text","Power")
    +field("amount","Amount in NZD","text","","inputmode=\"decimal\" required")
    +field("date","Due date","date",today());
 }
 $("#editor-title").textContent=title;
 $("#submit-modal").textContent="Save";
 $("#editor-fields").innerHTML=inner;
 $("#editor-hint").textContent=hint;
 editor.showModal();
 const first=fields.querySelector("input,select");if(first)first.focus();
}
async function submitModal(event){
 event.preventDefault();
 if(busy)return;
 const f=new FormData($("#editor-form")),v=key=>String(f.get(key)||"").trim();
 busy=true;
 try {
  const next=structuredClone(data);
  const amount=["income","expense","recurring","budget","edit-budget","goal","contribute","bill"].includes(modalKind)?cents(v("amount")):0;
  if(["income","expense","recurring","goal","contribute","bill"].includes(modalKind)&&amount===0)throw Error("Amount must be greater than zero.");
  if(modalKind==="income"||modalKind==="expense"){
   if(!v("category"))throw Error("Enter a category.");
   next.transactions.push({id:nextId(next.transactions),kind:modalKind==="income"?"Income":"Expense",
     category:v("category"),note:v("note"),cents:amount,date:iso(v("date"))});
  }else if(modalKind==="recurring"){
   if(!v("category"))throw Error("Enter a category.");
   next.recurring.push({id:nextId(next.recurring),kind:v("kind"),category:v("category"),note:v("note"),
     cents:amount,next_date:iso(v("date")),frequency:v("frequency")});
   Object.assign(next,recordDue(next,today()).data);
  }else if(modalKind==="budget"||modalKind==="edit-budget"){
   if(!v("category"))throw Error("Enter a category.");
   next.budgets=next.budgets.filter(x=>x.category!==v("category"));
   next.budgets.push({category:v("category"),cents:amount});
  }else if(modalKind==="goal"){
   if(!v("name"))throw Error("Enter a goal name.");
   next.goals.push({id:nextId(next.goals),name:v("name"),target:amount,saved:0});
  }else if(modalKind==="contribute"){
   const goal=next.goals.find(x=>x.id===Number(modalTarget));
   if(!goal)throw Error("Goal no longer exists.");
   if(!Number.isSafeInteger(goal.saved+amount))throw Error("Contribution is too large.");
   goal.saved+=amount;
  }else if(modalKind==="bill"){
   if(!v("name"))throw Error("Enter a bill name.");
   next.bills.push({id:nextId(next.bills),name:v("name"),cents:amount,due:iso(v("date"))});
  }
  await save(next);editor.close();toast("Saved to this device.");
 }catch(error){errorMessage(error);}
 finally{busy=false;}
}
async function mutate(action,id){
 const next=structuredClone(data);
 const find=(items)=>items.find(x=>String(x.id)===String(id));
 const remove=(table)=>next[table]=next[table].filter(x=>String(x.id)!==String(id));
 let confirmText="",notice="Updated.";
 switch(action){
  case "delete-transaction":confirmText="Delete this transaction permanently?";break;
  case "delete-recurring":confirmText="Stop this repeating transaction? Existing entries stay in your history.";break;
  case "delete-goal":confirmText="Remove this savings goal?";break;
  case "delete-budget":confirmText="Remove this category budget?";break;
  case "delete-bill":confirmText="Remove this bill without adding an expense?";break;
  case "pay-bill": {
    const bill=find(next.bills);
    if(!bill)throw Error("Bill not found.");
    confirmText="Record "+bill.name+" as paid for "+nzd(bill.cents)+"? This only adds a local expense; it does not transfer money.";
    break;
  }
 }
 if(!window.confirm(confirmText))return;
 if(action==="delete-transaction")remove("transactions");
 else if(action==="delete-recurring")remove("recurring");
 else if(action==="delete-goal")remove("goals");
 else if(action==="delete-budget")next.budgets=next.budgets.filter(x=>x.category!==id);
 else if(action==="delete-bill")remove("bills");
 else if(action==="pay-bill"){
   const bill=find(next.bills);
   if(!bill)throw Error("Bill already removed.");
   next.transactions.push({id:nextId(next.transactions),kind:"Expense",category:"Bills",note:bill.name,cents:bill.cents,date:today()});
   remove("bills");notice="Bill recorded as a local expense.";
 }
 await save(next);toast(notice);
}
function download(filename,content,type){
 const file=new Blob([content],{type}),url=URL.createObjectURL(file);
 const link=document.createElement("a");
 link.href=url;link.download=filename;link.click();
 setTimeout(()=>URL.revokeObjectURL(url),60000);
}
async function approvePdfRows(plan){
 return await new Promise(resolve=>{
  const dialog=document.createElement("dialog");
  dialog.style.maxWidth="min(95vw,650px)";
  dialog.style.width="95vw";
  dialog.style.padding="20px";
  dialog.style.borderRadius="16px";
  const title=document.createElement("h2");title.textContent="Review ALL PDF transactions";
  const explanation=document.createElement("p");
  explanation.textContent=plan.total+" PDF rows; "+plan.skipped+" previously imported; "+plan.toAdd.length+" NEW entries. This preview is mandatory because PDF extraction can mix up text or amounts. Compare every row against your bank statement. Only NZD is supported.";
  const viewport=document.createElement("div");viewport.style.maxHeight="49vh";viewport.style.overflow="auto";
  viewport.style.border="1px solid #bbb";viewport.style.padding="8px";
  for(let i=0;i<plan.toAdd.length;i++){
   const entry=plan.toAdd[i],item=document.createElement("p");
   item.textContent=(i+1)+". "+entry.date+" · "+entry.kind+" · "+nzd(entry.cents)+" · "+entry.note.replace(/^Bank CSV \([^)]+\):\s*/,"");
   viewport.append(item);
  }
  const approveLabel=document.createElement("label");
  const check=document.createElement("input");check.type="checkbox";check.style.marginRight="10px";
  approveLabel.append(check,document.createTextNode("I checked the dates, directions and amounts against the original PDF."));
  const buttons=document.createElement("div");buttons.style.display="flex";buttons.style.gap="10px";buttons.style.marginTop="18px";
  const cancel=document.createElement("button");cancel.type="button";cancel.textContent="Cancel";
  const yes=document.createElement("button");yes.type="button";yes.textContent="Import these NZD transactions";yes.disabled=true;
  check.addEventListener("change",()=>{yes.disabled=!check.checked;});
  const finish=ok=>{dialog.close();dialog.remove();resolve(ok);};
  cancel.addEventListener("click",()=>finish(false));
  yes.addEventListener("click",()=>finish(true));
  dialog.addEventListener("cancel",event=>{event.preventDefault();finish(false);});
  buttons.append(cancel,yes);dialog.append(title,explanation,viewport,approveLabel,buttons);
  document.body.append(dialog);dialog.showModal();
 });
}
async function importBankPdfFile(file){
 if(!file)return;
 const owner=profile,baseline=JSON.stringify(data);
 try{
  if(file.size>4000000)throw Error("Bank PDF is larger than 4 MB.");
  const nickname=window.prompt("Bank nickname, such as Everyday. Reuse the same nickname for later statement imports. Never enter your account number.","Everyday");
  if(nickname===null)return;
  toast("Reading bank PDF locally. This can take a moment.");
  const lines=await pdfFileToLines(file);
  const converted=pdfStatementToCsv(lines);
  const planned=planBankCsv(converted,data.transactions,nickname);
  if(owner!==profile||baseline!==JSON.stringify(data))throw Error("Your financial records changed. Try again.");
  if(!planned.toAdd.length){toast("No new PDF transactions found. Skipped "+planned.skipped+" duplicates.");return;}
  if(planned.toAdd.length>200)throw Error("For safety, split the PDF into statements of 200 transactions or fewer.");
  if(data.transactions.length+planned.toAdd.length>100000)throw Error("Too many transactions.");
  if(!await approvePdfRows(planned))return;
  if(owner!==profile||baseline!==JSON.stringify(data))throw Error("Your data changed while reviewing the PDF. No transactions were added.");
  const next=structuredClone(data);
  for(const row of planned.toAdd)next.transactions.push({id:nextId(next.transactions),...row});
  strictData(next);await save(next);
  toast("Added "+planned.toAdd.length+" reviewed PDF transactions.");
 }catch(error){errorMessage(error);}
 finally{$("#bank-pdf-file").value="";}
}
async function importBankCsvFile(file){
 if(!file)return;
 const owner=profile,baseline=JSON.stringify(data);
 try{
  if(file.size>2000000)throw Error("Bank CSV is larger than 2 MB.");
  const label=window.prompt("Account nickname (for example: Everyday). Reuse the same nickname every time you import this account's statements. Do not enter your account number.","Everyday");
  if(label===null)return;
  const parsed=planBankCsv(await file.text(),data.transactions,label);
  if(owner!==profile||baseline!==JSON.stringify(data))throw Error("Your records changed while reading this file. Please try again.");
  if(!parsed.toAdd.length){toast("No new records: "+parsed.skipped+" already imported.");return;}
  if(data.transactions.length+parsed.toAdd.length>100000)throw Error("Too many transactions. Export a JSON backup before continuing.");
  const sample=parsed.toAdd.slice(0,3).map(r=>r.date+" · "+r.kind+" · "+nzd(r.cents)+" · "+r.category).join("\n");
  const message="Preview: "+parsed.total+" statement rows; "+parsed.skipped+" existing duplicates; "+parsed.toAdd.length+" new NZD transactions.\n\n"+sample+"\n\nConfirm the entire file is NZD, not AUD. Import into the CURRENT profile? Records will be ADDED, never replaced. If two bank entries are completely identical, duplicates may not be distinguishable. Keep a full JSON backup.";
  if(!window.confirm(message))return;
  if(owner!==profile||baseline!==JSON.stringify(data))throw Error("Your financial records changed during preview. Please retry.");
  const next=structuredClone(data);
  for(const row of parsed.toAdd)next.transactions.push({id:nextId(next.transactions),...row});
  strictData(next);
  await save(next);
  toast("Imported "+parsed.toAdd.length+" NZD transactions. Skipped "+parsed.skipped+" duplicates.");
 }catch(error){errorMessage(error);}
 finally{$("#bank-csv-file").value="";}
}
async function importFile(file){
 if(!file)return;
 const owner=profile,epoch=profileEpoch,baseline=JSON.stringify(data);
 try{
  if(file.size>20000000)throw Error("Backup exceeds 20 MB.");
  const backup=strictData(JSON.parse(await file.text()));
  const question="Replace ALL local transactions, budgets, goals, bills and repeating schedules for this profile? This cannot be undone. Save your existing JSON backup first.";
  if(!window.confirm(question))return;
  if(profile!==owner||profileEpoch!==epoch||JSON.stringify(data)!==baseline)throw Error("Account or records changed while reading this backup. Nothing was restored.");
  await save(backup);toast("Full backup restored successfully.");
 }catch(error){errorMessage(error);}
 finally{$("#import-file").value="";}
}
async function settingsAction(action){
 switch(action){
  case "month-prev":monthChange(-1);break;case "month-next":monthChange(1);break;
  case "month-now":selectedMonth=clockMonth();render();break;
  case "go-bills":navigate("bills");break;case "go-export":navigate("export");break;
  case "csv":download("MoneyMate-NZ-"+today()+".csv",csv(data),"text/csv;charset=utf-8");break;
  case "backup":download("MoneyMate-NZ-backup-"+today()+".json",JSON.stringify(data,null,2),"application/json");break;
  case "import":$("#import-file").click();break;
  case "bank-csv":$("#bank-csv-file").click();break;
  case "bank-pdf":$("#bank-pdf-file").click();break;
  case "delete-account":await deleteAccount();break;
  case "signout":await signout();break;
  case "go-admin":if(adminOverview)navigate("admin");break;
  case "admin-refresh":await refreshAdminOverview(false);break;
  case "google-login":await signInWithGoogle();break;
  case "go-account":navigate("account");break;
  case "go-banking":navigate("banking");break;
  case "cloud-enable":await enableCloud();break;
  case "cloud-sync":await syncCloud(true);break;
  case "cloud-download":await chooseCloudCopy();break;
  case "import-guest":await copyGuest();break;
  case "confirm":showAuthModal("confirm");break;
  case "recover":showAuthModal("recover");break;
  case "resend":showAuthModal("resend");break;
  default:
   if(["income","expense","recurring","budget","edit-budget","goal","contribute","bill"].includes(action))
     showModal(action,arguments[1]);
 }
}
document.addEventListener("click",event=>{
 const tab=event.target.closest("[data-page]");
 if(tab){navigate(tab.dataset.page);return;}
 const btn=event.target.closest("[data-action]");if(!btn||busy)return;
 const action=btn.dataset.action,id=btn.dataset.id;
 if(["delete-transaction","delete-recurring","delete-goal","delete-budget","delete-bill","pay-bill"].includes(action))
   mutate(action,id).catch(errorMessage);
 else if(["income","expense","recurring","budget","edit-budget","goal","contribute","bill"].includes(action))
   showModal(action,id);
 else settingsAction(action,id).catch(errorMessage);
});
$("#editor-form").addEventListener("submit",submitModal);
$("#cancel-modal").addEventListener("click",()=>editor.close());
$("#dismiss-modal").addEventListener("click",()=>editor.close());
$("#import-file").addEventListener("change",e=>importFile(e.target.files[0]));
$("#bank-csv-file").addEventListener("change",e=>importBankCsvFile(e.target.files[0]));
$("#bank-pdf-file").addEventListener("change",e=>importBankPdfFile(e.target.files[0]));
window.addEventListener("hashchange",()=>{const p=location.hash.replace(/^#/,"");if(p!==page&&pages.includes(p)){page=p;render();}});
function requireConfig(){if(!config)throw Error("Account service is not configured. Local budgeting still works.");}
async function authApi(path,method="POST",body={},token=null){
 requireConfig();
 const response=await fetch(config.url+path,{method,headers:{"Content-Type":"application/json",apikey:config.publishableKey,...(token?{Authorization:"Bearer "+token}:{})},
   body:body===null?undefined:JSON.stringify(body),cache:"no-store",credentials:"omit",redirect:"error",signal:AbortSignal.timeout(20000)});
 if(!response.ok){
   if(response.status===429)throw Error("Too many attempts. Wait a little and try again.");
   if(response.status===401||response.status===400){const err=Error("Check your email, password or verification code.");err.httpStatus=response.status;throw err;}
   const err=Error("Account request could not be completed ("+response.status+").");err.httpStatus=response.status;throw err;
 }
 const raw=await response.text();return raw?JSON.parse(raw):{};
}
async function withBusy(operation){
 if(busy)return;busy=true;
 try{await operation();}catch(error){errorMessage(error);}finally{busy=false;}
}
async function attachSession(result,{persist=true,checkAdmin=true}={}){
 const next=normalizeLoginSession(result);
 if(persist)await saveLoginSession(next);
 auth=next;adminOverview=null;
 await switchOwner(next.user.id);
 await cloudOnSignin();
 if(checkAdmin)await refreshAdminOverview(true);
 toast("Signed in. Your private account records are available.");
}
async function accessToken(){
 if(!auth)throw Error("Sign in first.");
 if(auth.expires_at<=Math.floor(Date.now()/1000)+60){
  if(!refreshInFlight)refreshInFlight=(async()=>{
    const prior=auth,owner=prior.user.id;
    const stored=await loadLoginSession().catch(()=>null);
    if(stored?.user.id===owner&&stored.refresh_token!==prior.refresh_token&&stored.expires_at>Math.floor(Date.now()/1000)+60){
      if(auth?.user.id===owner)auth=stored;
      return;
    }
    const updated=normalizeLoginSession(
      await authApi("/auth/v1/token?grant_type=refresh_token","POST",{refresh_token:prior.refresh_token}),owner);
    if(auth?.user.id!==owner)throw Error("Account changed while refreshing");
    await saveLoginSession(updated);
    auth=updated;
  })();
  try{await refreshInFlight;}finally{refreshInFlight=null;}
 }
 if(!auth)throw Error("Sign in first.");
 return auth.access_token;
}
async function restoreLogin(){
 let saved;
 try{saved=await loadLoginSession();}catch{
  await clearLoginSession().catch(()=>{});
  return;
 }
 if(!saved||auth)return;
 try {
  const now=Math.floor(Date.now()/1000);
  if(saved.expires_at<=now+60){
   saved=normalizeLoginSession(
     await authApi("/auth/v1/token?grant_type=refresh_token","POST",{refresh_token:saved.refresh_token}),saved.user.id);
  }else if(config){
   const current=await authApi("/auth/v1/user","GET",null,saved.access_token);
   if(current?.id!==saved.user.id)throw Object.assign(Error("Session identity changed"),{httpStatus:401});
  }
  if(!auth){
   const destination=page;
   await attachSession(saved);
   if(destination!=="home")navigate(destination);
  }
 }catch(error){
  if(sessionInvalidated(error.httpStatus)){
   await clearLoginSession().catch(()=>{});
   if(!auth){await switchOwner("guest");navigate("account");}
   toast("Your previous login expired or was revoked. Please sign in again.");
  }else if(!auth){
   const destination=page;
   await attachSession(saved,{persist:false,checkAdmin:false}).catch(()=>{});
   if(auth&&destination!=="home")navigate(destination);
   toast("Offline account opened. Reconnect to refresh your login.");
  }
 }
}

async function refreshAdminOverview(silent=true){
 const owner=auth?.user?.id;
 if(!owner||!config){adminOverview=null;render();return;}
 try{
  const token=await accessToken();
  const response=await fetch(config.url+"/rest/v1/rpc/moneymate_admin_overview",{
   method:"POST",headers:{"Content-Type":"application/json",apikey:config.publishableKey,Authorization:"Bearer "+token},
   body:"{}",credentials:"omit",cache:"no-store",redirect:"error",signal:AbortSignal.timeout(20000)
  });
  if(auth?.user?.id!==owner)return;
  if(response.status===401||response.status===403||response.status===404){adminOverview=null;return;}
  if(!response.ok)throw Error("Admin service unavailable.");
  const stats=await response.json();
  if(stats?.role!=="admin"||!Number.isSafeInteger(stats.registered_users)||stats.registered_users<0)
   throw Error("Invalid admin server response.");
  adminOverview=stats;
 }catch{
  if(auth?.user?.id===owner)adminOverview=null;
  if(!silent)toast("Unable to load administrator statistics. Please try again.");
 }finally{
  if(auth?.user?.id===owner)render();
 }
}

function attachAuthForms(){
 $("#signin-form")?.addEventListener("submit",e=>{e.preventDefault();withBusy(async()=>{
  const email=$("#email").value.trim(),password=$("#password").value;
  $("#password").value="";
  await attachSession(await authApi("/auth/v1/token?grant_type=password","POST",{email,password}));
 });});
 $("#signup-form")?.addEventListener("submit",e=>{e.preventDefault();withBusy(async()=>{
  const email=$("#new-email").value.trim(),password=$("#new-password").value;
  if(password.length<8||password.length>128)throw Error("Use a password of 8–128 characters.");
  if(!$("#terms").checked)throw Error("Please accept the privacy policy.");
  $("#new-password").value="";
  await authApi("/auth/v1/signup","POST",{email,password});
  toast("If registration is available, a confirmation code will arrive. Select Confirm email code.");
 });});
}
async function signout(){
 if(!auth)return;
 await withBusy(async()=>{
  if(!confirm("Sign out? Your account records will stay on this browser and remain hidden until you sign in again."))return;
  try{await authApi("/auth/v1/logout?scope=global","POST",{},await accessToken());}
  catch{toast("Signed out locally. Remote sessions might still be active.");}
  finally{
   await clearLoginSession().catch(()=>toast("Could not clear saved login. Clear site data on shared devices."));
   adminOverview=null;auth=null;await switchOwner("guest");navigate("account");
  }
 });
}
async function deleteAccount(){
 if(!auth)return;
 const entered=prompt("Type DELETE to confirm that your Supabase account will be permanently deleted:");
 if(entered!=="DELETE")return;
 const password=prompt("Enter your current account password to verify deletion:");
 if(!password)return;
 await withBusy(async()=>{
  const id=auth.user.id;
  await authApi("/functions/v1/delete-account","POST",{password},await accessToken());
  await clearLoginSession().catch(()=>{});
   await dbErase(id);await dbSyncMeta(id,null);adminOverview=null;auth=null;cloudMeta=null;
  await switchOwner("guest");navigate("account");
  toast("Account deleted. Browser records for this account have also been removed.");
 });
}
function showAuthModal(kind){
 modalKind="auth-"+kind;
 const fields=$("#editor-fields");
 const label=kind==="confirm"?"Confirm email":kind==="resend"?"Resend confirmation":"Reset password";
 $("#editor-title").textContent=label;
 $("#submit-modal").textContent=kind==="confirm"?"Confirm":kind==="resend"?"Send code":kind==="recover"?"Send reset code":"Save";
 fields.innerHTML=formField("modal-email","Email address","email","email",true)
  +(kind==="confirm"?formField("modal-code","Email verification code","text","code",true,'inputmode="numeric" pattern="[0-9]{6,10}"'):"");
 $("#editor-hint").textContent="Email delivery depends on the project's configured SMTP provider.";
 editor.showModal();
}
const standardSubmit=submitModal;
$("#editor-form").removeEventListener("submit",standardSubmit);
$("#editor-form").addEventListener("submit",event=>{
 if(!modalKind.startsWith("auth-")){submitModal(event);return;}
 event.preventDefault();
 const kind=modalKind.slice(5),email=$("#modal-email").value.trim();
 editor.close();withBusy(async()=>{
  if(kind==="confirm") {
   const code=$("#modal-code").value.trim();
   if(!/^\d{6,10}$/.test(code))throw Error("Enter your email code.");
   const verified=await authApi("/auth/v1/verify","POST",{type:"email",email,token:code});
   if(verified.access_token)await authApi("/auth/v1/logout?scope=local","POST",{},verified.access_token).catch(()=>{});
   toast("Email verified. You can now sign in.");
  }else if(kind==="resend"){
   await authApi("/auth/v1/resend","POST",{type:"signup",email});
   toast("If eligible, a confirmation code will arrive.");
  }else{
   await authApi("/auth/v1/recover","POST",{email});
   toast("If eligible, a reset code will arrive. Continue with Reset password.");
   setTimeout(()=>showResetModal(email),250);
  }
 });
});
function showResetModal(email){
 modalKind="auth-reset";
 $("#editor-title").textContent="Reset password";
 $("#editor-fields").innerHTML=formField("modal-email","Email","email","email",true)
   +formField("modal-code","Reset code","text","code",true,'inputmode="numeric" pattern="[0-9]{6,10}"')
   +formField("modal-new-password","New password (8–128 chars)","password","password",true,'minlength="8" maxlength="128"');
 $("#editor-hint").textContent="Enter the code sent to your email. No data is deleted.";
 $("#editor-fields").querySelector("#modal-email").value=email;
 $("#submit-modal").textContent="Reset password";
 editor.showModal();
}
$("#editor-form").addEventListener("submit",event=>{if(modalKind!=="auth-reset")return;
 event.preventDefault();event.stopImmediatePropagation();
 const email=$("#modal-email").value.trim(),code=$("#modal-code").value.trim(),password=$("#modal-new-password").value;
 editor.close();withBusy(async()=>{
  if(!/^\d{6,10}$/.test(code)||password.length<8)throw Error("Invalid code or password.");
  const verified=await authApi("/auth/v1/verify","POST",{email,token:code,type:"recovery"});
  try{await authApi("/auth/v1/user","PUT",{password},verified.access_token);toast("Password updated. Please sign in.");}
  finally{await authApi("/auth/v1/logout?scope=global","POST",{},verified.access_token).catch(()=>{});}
 });
},true);
const GOOGLE_PKCE_KEY="MoneyMateNZ-google-login-verifier";
function toBase64Url(buffer) {
 return btoa(String.fromCharCode(...new Uint8Array(buffer)))
   .replace(/\+/g,"-").replace(/\//g,"_").replace(/=+$/,"");
}
async function signInWithGoogle(){
 if(!config?.googleEnabled)throw Error("Google sign-in is not configured yet.");
 const bytes=new Uint8Array(48);crypto.getRandomValues(bytes);
 const verifier=toBase64Url(bytes);
 const digest=await crypto.subtle.digest("SHA-256",new TextEncoder().encode(verifier));
 const redirect=location.origin+location.pathname;
 sessionStorage.setItem(GOOGLE_PKCE_KEY,verifier);
 const address=new URL(config.url+"/auth/v1/authorize");
 address.searchParams.set("provider","google");
 address.searchParams.set("redirect_to",redirect);
 address.searchParams.set("code_challenge",toBase64Url(digest));
 address.searchParams.set("code_challenge_method","s256");
 location.assign(address.toString());
}
async function finishGoogleSignin(){
 const params=new URLSearchParams(location.search),code=params.get("code");
 if(!code)return;
 const verifier=sessionStorage.getItem(GOOGLE_PKCE_KEY);
 sessionStorage.removeItem(GOOGLE_PKCE_KEY);
 history.replaceState(null,"",location.pathname+(location.hash||""));
 if(!verifier){toast("Google sign-in session expired. Start again from Account.");return;}
 try {
  const session=await authApi("/auth/v1/token?grant_type=pkce","POST",{auth_code:code,code_verifier:verifier});
  await attachSession(session);navigate("account");
 }catch(error){errorMessage(error);}
}



async function dbSyncMeta(id,write) {
 const db=await dbOpen();
 try {
  return await new Promise((resolve,reject)=>{
   const tx=db.transaction("sync",write===undefined?"readonly":"readwrite"),store=tx.objectStore("sync");
   const req=write===undefined?store.get(id):write===null?store.delete(id):store.put(write,id);
   tx.oncomplete=()=>resolve(write===undefined?req.result:write);
   tx.onerror=()=>reject(tx.error);
   tx.onabort=()=>reject(tx.error||Error("Sync metadata save interrupted."));
  });
 }finally{db.close();}
}
const localEmpty=backup=>["transactions","budgets","goals","bills","recurring"].every(t=>backup[t].length===0);
async function signature(backup){
 const bytes=new TextEncoder().encode(JSON.stringify(strictData(backup)));
 const digest=new Uint8Array(await crypto.subtle.digest("SHA-256",bytes));
 return Array.from(digest,b=>b.toString(16).padStart(2,"0")).join("");
}
async function cloudRead() {
 const owner=auth?.user?.id,token=await accessToken();
 if(!owner||auth?.user?.id!==owner)throw Error("Account changed during cloud check.");
 const result=await authApi("/rest/v1/rpc/read_account_snapshot","POST",{},token);
 if(!Number.isSafeInteger(result.revision)||result.revision<0)throw Error("Cloud replied with an invalid revision.");
 if(result.revision>0)strictData(result.data);
 return result;
}
async function cloudCommit(revision,backup,owner=auth?.user?.id) {
 const token=await accessToken();
 if(!owner||auth?.user?.id!==owner)throw Error("Account changed during cloud save.");
 const result=await authApi("/rest/v1/rpc/commit_account_snapshot","POST",{
  p_expected_revision:revision,p_data:strictData(backup)
 },token);
 if(typeof result.success!=="boolean"||!Number.isSafeInteger(result.revision))throw Error("Cloud save returned an invalid reply.");
 return result;
}
async function cloudOnSignin() {
 const owner=auth?.user?.id,epoch=profileEpoch;
 if(!owner)return;
 const metadata=await dbSyncMeta(owner)||null;
 if(auth?.user?.id!==owner||profileEpoch!==epoch)return;
 cloudMeta=metadata;
 if(cloudMeta?.enabled){await syncCloud(false);return;}
 try {
  const remote=await cloudRead();
  if(auth?.user?.id!==owner||profile!==owner)return;
  cloudStatus=remote.revision>0?
    "Cloud copy available. Export a JSON backup, then tap Enable secure cloud sync to use it on this device.":
    "Cloud sync is off. Enable it to share your finances across devices.";
 }catch{
  cloudStatus="Cloud status unavailable. Your local financial records remain safe.";
 }
 render();
}
async function enableCloud() {
 if(!auth)return;
 if(!window.confirm("Enable MoneyMate NZ cloud sync? Your budgets, transactions, goals and bills will be stored on Supabase servers in Australia under your private account. Keep a full JSON backup first. Continue?"))return;
 if(cloudBusy)return;
 cloudBusy=true;
 try{
 const owner=auth.user.id,epoch=profileEpoch,baseline=strictData(data),remote=await cloudRead();
  if(auth?.user?.id!==owner||profileEpoch!==epoch)return;
  const localHash=await signature(baseline);
  if(profileEpoch!==epoch||await signature(data)!==localHash)throw Error("Records changed while enabling sync. Please retry.");
  if(remote.revision>0){
   const remoteData=strictData(remote.data);
   if(localEmpty(data)||JSON.stringify(remoteData)===JSON.stringify(strictData(data))){
    const metadata={enabled:true,revision:remote.revision,digest:await signature(remoteData)};
    await dbWrite(owner,remoteData,{expected:baseline,recovery:true,metadata,current:()=>profileEpoch===epoch&&JSON.stringify(data)===JSON.stringify(baseline)});
    if(profileEpoch!==epoch)return;
    data=remoteData;cloudMeta=metadata;
    cloudStatus="Cloud records restored. Automatic sync is active.";
   }else{
    cloudStatus="Sync conflict: this browser and cloud both have different records. Export local JSON first; nothing was overwritten.";
    toast(cloudStatus);render();return;
   }
  }else{
   const result=await cloudCommit(0,baseline,owner);
   if(auth?.user?.id!==owner||profileEpoch!==epoch)return;
   if(!result.success){cloudStatus="Another device created a cloud copy. Nothing was overwritten.";render();return;}
   cloudMeta={enabled:true,revision:result.revision,digest:localHash};
   cloudStatus="Cloud sync enabled. Your records are now backed up online.";
   if(await signature(data)!==localHash)cloudPending=true;
  }
  await dbSyncMeta(owner,cloudMeta);render();toast(cloudStatus);
 }catch(error){cloudStatus="Could not enable cloud sync. Your existing local records remain safe.";errorMessage(error);render();}
 finally{cloudBusy=false;if(cloudPending){cloudPending=false;void syncCloud(false);}}
}
async function syncCloud(showStatus=false) {
 if(!auth||!cloudMeta?.enabled)return;
 if(cloudBusy||financialSaving){cloudPending=true;return;}
 cloudBusy=true;
 const owner=auth.user.id,epoch=profileEpoch;
 try{
  const remote=await cloudRead();
  if(auth?.user?.id!==owner||profileEpoch!==epoch)return;
  const baseline=strictData(data);
  const localHash=await signature(data);
  if(profileEpoch!==epoch||!cloudMeta?.enabled)return;
  const choice=decideSync({
   enabled:cloudMeta.enabled,baseRevision:cloudMeta.revision,baseDigest:cloudMeta.digest,
   localDigest:localHash,remoteRevision:remote.revision,localEmpty:localEmpty(data)
  });
  if(choice==="conflict"){
   cloudStatus="Sync conflict: both devices changed records. Your local changes are safe; cloud was not overwritten. Export JSON before resolving.";
  }else if(choice==="download"){
   if(!remote.data){cloudStatus="Cloud copy not found. Local records are unchanged.";return;}
   const restored=strictData(remote.data),hash=await signature(restored);
   // Never overwrite local edits made while the request was in flight.
   if(auth?.user?.id!==owner||profileEpoch!==epoch||await signature(data)!==localHash){cloudPending=true;return;}
   const metadata={enabled:true,revision:remote.revision,digest:hash};
   await dbWrite(owner,restored,{expected:baseline,recovery:true,metadata,current:()=>profileEpoch===epoch&&JSON.stringify(data)===JSON.stringify(baseline)});
   if(profileEpoch!==epoch)return;
   data=restored;cloudMeta=metadata;
   cloudStatus="Cloud changes recovered and saved to this device.";render();
  }else if(choice==="upload"){
   const captured=strictData(data),hash=await signature(captured);
   if(profileEpoch!==epoch)return;
   const result=await cloudCommit(cloudMeta.revision,captured,owner);
   if(auth?.user?.id!==owner||profileEpoch!==epoch)return;
   if(result.success){
    cloudMeta={enabled:true,revision:result.revision,digest:hash};
    await dbSyncMeta(owner,cloudMeta);
    cloudStatus="Synced securely to your account.";
    if(await signature(data)!==hash)cloudPending=true;
   }else cloudStatus="Sync conflict: the cloud changed before this upload. Your local records were kept.";
  }else cloudStatus="Up to date · securely saved to cloud.";
  if(showStatus)toast(cloudStatus);
  if(page==="account")render();
 }catch(error){
  cloudStatus="Offline or sync unavailable. Local records are safe; retry when connected.";
  if(showStatus)errorMessage(error);
  if(page==="account")render();
 }finally{
  cloudBusy=false;
  if(cloudPending){cloudPending=false;setTimeout(()=>{if(auth?.user?.id===owner)void syncCloud(false);},0);}
 }
}
async function chooseCloudCopy() {
 if(!auth)return;
 if(cloudBusy){toast("A cloud check is in progress. Please retry shortly.");return;}
 const owner=auth.user.id,epoch=profileEpoch,baseline=strictData(data);
 if(!window.confirm("First export a full JSON backup of your current local records. Continue only if you have saved it. This replaces the current browser profile with the cloud copy."))return;
 if(!window.confirm("Final confirmation: REPLACE this device's local financial records with the saved cloud version?"))return;
 cloudBusy=true;
 try{
  const remote=await cloudRead();
  if(auth?.user?.id!==owner||profileEpoch!==epoch||!remote.data)throw Error("Cloud records unavailable or account changed.");
  const safe=strictData(remote.data);
  const metadata={enabled:true,revision:remote.revision,digest:await signature(safe)};
  // Preserve the outgoing local records and metadata in the same transaction.
  await dbWrite(owner,safe,{expected:baseline,recovery:true,metadata,current:()=>profileEpoch===epoch&&JSON.stringify(data)===JSON.stringify(baseline)});
  if(profileEpoch!==epoch)return;
  data=safe;cloudMeta=metadata;
  cloudStatus="Cloud copy restored. Previous local records remain in a safety copy in this browser.";
  render();toast("Cloud copy restored.");
 }catch(error){errorMessage(error);}
 finally{cloudBusy=false;if(cloudPending){cloudPending=false;void syncCloud(false);}}
}
document.addEventListener("visibilitychange",()=>{
 if(document.visibilityState==="visible"&&auth&&cloudMeta?.enabled)void syncCloud(false);
});




window.addEventListener("online",()=>{if(auth&&cloudMeta?.enabled)void syncCloud(false);});
setInterval(()=>{if(document.visibilityState==="visible"&&auth&&cloudMeta?.enabled)void syncCloud(false);},45000);
async function copyGuest(){
 if(!auth)return;
 if(!localEmpty(data))throw Error("Copy guest records only into an empty account profile. Export a full JSON backup first.");
 const original=await dbRead("guest");
 if(localEmpty(original)){toast("No guest records to copy.");return;}
 if(!confirm("Copy guest income, expenses, budgets, goals, bills and repeating entries into this empty account? The guest records are preserved, and cloud sync will only upload after your consent."))return;
 await save(original);
 toast("Guest records copied safely. The original guest profile was not changed.");
}


async function bootstrap(){
 try{
  await switchOwner("guest");
  page=pages.includes(location.hash.slice(1))?location.hash.slice(1):"home";
  render();
  try{
   const result=await fetch("./account-config.json",{cache:"no-store"});
   if(!result.ok)throw Error();
   const obj=await result.json();
   if(!/^https:\/\/[a-z0-9-]+\.supabase\.co$/.test(obj.url)||!/^sb_publishable_[A-Za-z0-9_-]+$/.test(obj.publishableKey))throw Error();
   config=obj;if(page==="account")render();
  }catch{if(page==="account")render();}
  if(new URLSearchParams(location.search).has("code"))await finishGoogleSignin();
  else await restoreLogin();
  if("serviceWorker" in navigator)navigator.serviceWorker.register("./sw.js").catch(()=>{});
 }catch(error){
  view.innerHTML='<div class="panel"><h2>Unable to open browser storage</h2><p>MoneyMate NZ needs browser storage enabled for safe offline financial records. Check private mode or storage restrictions and reload this page.</p></div>';
  errorMessage(error);
 }
}
window.addEventListener("online",()=>{if(auth)void accessToken().then(()=>refreshAdminOverview(true)).catch(()=>{});else void restoreLogin();});
bootstrap();
