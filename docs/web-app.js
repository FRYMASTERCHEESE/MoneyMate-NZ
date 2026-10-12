import {emptyData,strictData,cents,nzd,iso,month,nextId,recordDue,totals,csv,CATEGORIES} from "./web-core.mjs";

const $=s=>document.querySelector(s);
const escape=s=>String(s??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const today=()=>{const d=new Date();return [d.getFullYear(),String(d.getMonth()+1).padStart(2,"0"),String(d.getDate()).padStart(2,"0")].join("-");};
const clockMonth=()=>today().slice(0,7);
const prettyDate=s=>new Date(s+"T12:00:00").toLocaleDateString("en-NZ",{day:"numeric",month:"short",year:"numeric"});
const amounts=["#06785b","#e4ac54","#5a9ed8","#ac89e5","#eb8769","#73c7be","#8aa56c"];
const pages=["home","transactions","budgets","goals","bills","export","account"];
const label=p=>({home:"Home",transactions:"Transactions",budgets:"Budgets",goals:"Savings goals",bills:"Bills",export:"Export & backup",account:"Account"}[p]||"Home");
let profile="guest",data=emptyData(),page="home",selectedMonth=clockMonth(),auth=null,config=null,busy=false,searchTerm="";
let toastTimer,modalKind="",modalTarget=0;
const editor=$("#editor"),view=$("#view");
const toast=(message)=>{const el=$("#toast");el.textContent=message;el.classList.add("show");clearTimeout(toastTimer);toastTimer=setTimeout(()=>el.classList.remove("show"),4500);};
const errorMessage=e=>toast(e&&e.message?e.message:"Something went wrong.");
function dbOpen(){
 return new Promise((resolve,reject)=>{
  const req=indexedDB.open("MoneyMateNZWeb",1);
  req.onupgradeneeded=()=>req.result.createObjectStore("profiles");
  req.onerror=()=>reject(req.error);
  req.onsuccess=()=>resolve(req.result);
 });
}
async function dbRead(id){
 const db=await dbOpen();
 try {const stored=await new Promise((resolve,reject)=>{const tx=db.transaction("profiles","readonly");const q=tx.objectStore("profiles").get(id);q.onsuccess=()=>resolve(q.result);q.onerror=()=>reject(q.error);});
 return stored?strictData(stored):emptyData();}finally{db.close();}
}
async function dbWrite(id,next){
 const safe=strictData(next),db=await dbOpen();
 try{await new Promise((resolve,reject)=>{const tx=db.transaction("profiles","readwrite");tx.objectStore("profiles").put(safe,id);tx.oncomplete=resolve;tx.onerror=()=>reject(tx.error);tx.onabort=()=>reject(tx.error);});}
 finally{db.close();}
}
async function dbErase(id){
 const db=await dbOpen();try{await new Promise((resolve,reject)=>{const tx=db.transaction("profiles","readwrite");tx.objectStore("profiles").delete(id);tx.oncomplete=resolve;tx.onerror=()=>reject(tx.error);tx.onabort=()=>reject(tx.error);});}finally{db.close();}
}
async function save(next){
 data=strictData(next);await dbWrite(profile,data);render();
}
async function switchOwner(id){
 profile=id||"guest";
 data=await dbRead(profile);
 const result=recordDue(data,today());
 if(result.created)await dbWrite(profile,result.data);
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
 markup+='<div class="btns">'+btn("+ Income","income")+btn("+ Expense","expense","secondary")+btn("Add repeating entry","recurring","secondary")+'</div>';
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
 +'<div class="notice warning"><strong>Important:</strong> Your Android app and this laptop store financial data separately. Signing into the same account does not automatically sync transactions. Use a JSON backup to move records between devices. Store backups securely.</div>'
 +'<div class="grid-2"><section class="panel"><h2>Export your data</h2><p class="muted">Download a spreadsheet-compatible CSV or a complete MoneyMate NZ JSON backup of the current local profile.</p>'
 +'<div class="btns">'+btn("Export transactions CSV","csv")+btn("Save full JSON backup","backup","secondary")+'</div></section>'
 +'<section class="panel"><h2>Restore a full backup</h2><p class="muted">Import a JSON backup saved on this laptop or exported from Android. It replaces all entries in this local profile, after confirmation.</p>'
 +'<div class="btns">'+btn("Choose JSON file","import")+'</div></section></div>'
 +'<section class="panel" style="margin-top:20px"><h2>Your privacy choices</h2><p class="muted">Financial records remain in this browser. Browser storage can be cleared by your device or browser settings, so keep a secure backup. Account authentication uses Supabase separately.</p>'
 +'<div class="btns"><a class="secondary" href="./privacy.html">Read privacy policy ↗</a><a class="secondary" href="./delete-account.html">Account deletion ↗</a></div></section>';
}
function accountPage(){
 let html=heading("Your MoneyMate account","Sign in or use a local guest profile. Your personal financial data stays in this browser.");
 html+='<div class="notice warning">Signing into the same email account on Android and web does <strong>not</strong> automatically sync finances. Each device retains its own private local records; use Export & backup for transfers.</div>';
 if(!config)html+='<div class="notice">Connecting to secure account service… If unavailable, local budgeting still works.</div>';
 if(auth){
   html+='<div class="panel"><h2>Signed in</h2><p class="muted">'+escape(auth.user.email)+'</p><span class="tag">This browser profile</span><p class="muted" style="margin-top:14px">Financial entries are local to this browser and isolated under your Supabase user ID.</p>'
    +'<div class="btns">'+btn("Sign out","signout","secondary")+btn("Delete account","delete-account","danger")+'</div></div>';
 }else{
   html+='<div class="account-grid"><section class="panel"><h2>Sign in</h2><form id="signin-form">'
    +formField("email","Email address","email","email",true,'autocomplete="username"')
    +formField("password","Password","password","password",true,'autocomplete="current-password"')
    +'<button class="primary" type="submit" '+(!config?"disabled":"")+'>Sign in</button></form>'
    +'<div class="btns">'+btn("Forgot password","recover","ghost")+'</div></section>'
    +'<section class="panel"><h2>Create your account</h2><form id="signup-form">'
    +formField("new-email","Email address","email","email",true,'autocomplete="username"')
    +formField("new-password","Password (12–128 characters)","password","password",true,'autocomplete="new-password" minlength="12" maxlength="128"')
    +'<label class="field"><span><input type="checkbox" id="terms" required> I have read the <a href="./privacy.html">privacy policy</a>.</span></label>'
    +'<button class="primary" type="submit" '+(!config?"disabled":"")+'>Create account</button></form>'
    +'<div class="btns">'+btn("Confirm email code","confirm","secondary")+btn("Resend code","resend","ghost")+'</div></section></div>';
 }
 html+='<section class="panel" style="margin-top:20px"><h2>Local data & account safety</h2>'
  +'<p class="muted">When you sign out, account records stay in this browser but are hidden until you sign in again. The guest profile remains separately accessible. If you share a computer, use a private device account and a screen lock.</p>'
  +'<div class="btns">'+btn("Open Export & backup","go-export","secondary")+'</div></section>';
 return html;
}
function formField(id,caption,type,name,required=false,extra=""){
 return '<label class="field"><span>'+escape(caption)+'</span><input id="'+escape(id)+'" name="'+escape(name)+'" type="'+escape(type)+'" '+(required?"required":"")+' '+extra+'></label>';
}
function render(){
 $("#profile-pill").textContent=auth?auth.user.email:"Local profile";
 $("#date-heading").textContent="Your money. Your plan.";
 for(const button of document.querySelectorAll("[data-page]"))button.setAttribute("aria-current",button.dataset.page===page?"page":"false");
 const body={home,transactions,budgets,goals,bills,export:exportPage,account:accountPage}[page]||home;
 view.innerHTML=body();
 if(page==="transactions"){
   renderRecords();
   $("#history-search").addEventListener("input",e=>{searchTerm=e.target.value;renderRecords();});
 }
 if(page==="account")attachAuthForms();
}
function navigate(next){
 if(!pages.includes(next))next="home";
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
async function importFile(file){
 if(!file)return;
 try{
  if(file.size>20000000)throw Error("Backup exceeds 20 MB.");
  const backup=strictData(JSON.parse(await file.text()));
  const question="Replace ALL local transactions, budgets, goals, bills and repeating schedules for this profile? This cannot be undone. Save your existing JSON backup first.";
  if(!window.confirm(question))return;
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
  case "delete-account":await deleteAccount();break;
  case "signout":await signout();break;
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
window.addEventListener("hashchange",()=>{const p=location.hash.replace(/^#/,"");if(p!==page&&pages.includes(p)){page=p;render();}});
function requireConfig(){if(!config)throw Error("Account service is not configured. Local budgeting still works.");}
async function authApi(path,method="POST",body={},token=null){
 requireConfig();
 const response=await fetch(config.url+path,{method,headers:{"Content-Type":"application/json",apikey:config.publishableKey,...(token?{Authorization:"Bearer "+token}:{})},
   body:body===null?undefined:JSON.stringify(body),cache:"no-store",credentials:"omit",redirect:"error",signal:AbortSignal.timeout(20000)});
 if(!response.ok){
   if(response.status===429)throw Error("Too many attempts. Wait a little and try again.");
   if(response.status===401||response.status===400)throw Error("Check your email, password or verification code.");
   throw Error("Account request could not be completed ("+response.status+").");
 }
 const raw=await response.text();return raw?JSON.parse(raw):{};
}
async function withBusy(operation){
 if(busy)return;busy=true;
 try{await operation();}catch(error){errorMessage(error);}finally{busy=false;}
}
async function attachSession(result){
 if(!result||!result.access_token||!result.refresh_token||!result.user||!/^[0-9a-f-]{36}$/i.test(result.user.id))
   throw Error("Account service returned an invalid session.");
 auth={...result,expires_at:Math.floor(Date.now()/1000)+(result.expires_in||900)};
 await switchOwner(result.user.id);
 toast("Signed in. Local account records are now open.");
}
async function accessToken(){
 if(!auth)throw Error("Sign in first.");
 if(auth.expires_at<Date.now()/1000+60){
   const previous=auth.user.id;
   const updated=await authApi("/auth/v1/token?grant_type=refresh_token","POST",{refresh_token:auth.refresh_token});
   if(updated.user?.id!==previous)throw Error("Account identity changed. Please sign in again.");
   auth={...updated,expires_at:Math.floor(Date.now()/1000)+(updated.expires_in||900)};
 }
 return auth.access_token;
}
function attachAuthForms(){
 $("#signin-form")?.addEventListener("submit",e=>{e.preventDefault();withBusy(async()=>{
  const email=$("#email").value.trim(),password=$("#password").value;
  $("#password").value="";
  await attachSession(await authApi("/auth/v1/token?grant_type=password","POST",{email,password}));
 });});
 $("#signup-form")?.addEventListener("submit",e=>{e.preventDefault();withBusy(async()=>{
  const email=$("#new-email").value.trim(),password=$("#new-password").value;
  if(password.length<12||password.length>128)throw Error("Use a password of 12–128 characters.");
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
  finally{auth=null;await switchOwner("guest");navigate("account");}
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
  await dbErase(id);auth=null;
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
   +formField("modal-new-password","New password (12–128 chars)","password","password",true,'minlength="12" maxlength="128"');
 $("#editor-hint").textContent="Enter the code sent to your email. No data is deleted.";
 $("#editor-fields").querySelector("#modal-email").value=email;
 $("#submit-modal").textContent="Reset password";
 editor.showModal();
}
$("#editor-form").addEventListener("submit",event=>{if(modalKind!=="auth-reset")return;
 event.preventDefault();event.stopImmediatePropagation();
 const email=$("#modal-email").value.trim(),code=$("#modal-code").value.trim(),password=$("#modal-new-password").value;
 editor.close();withBusy(async()=>{
  if(!/^\d{6,10}$/.test(code)||password.length<12)throw Error("Invalid code or password.");
  const verified=await authApi("/auth/v1/verify","POST",{email,token:code,type:"recovery"});
  try{await authApi("/auth/v1/user","PUT",{password},verified.access_token);toast("Password updated. Please sign in.");}
  finally{await authApi("/auth/v1/logout?scope=global","POST",{},verified.access_token).catch(()=>{});}
 });
},true);
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
  if("serviceWorker" in navigator)navigator.serviceWorker.register("./sw.js").catch(()=>{});
 }catch(error){
  view.innerHTML='<div class="panel"><h2>Unable to open browser storage</h2><p>MoneyMate NZ needs browser storage enabled for safe offline financial records. Check private mode or storage restrictions and reload this page.</p></div>';
  errorMessage(error);
 }
}
bootstrap();
