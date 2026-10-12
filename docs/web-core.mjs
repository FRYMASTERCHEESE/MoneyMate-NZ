// Shared, dependency-free financial rules for the browser edition.
export const CATEGORIES = ["Groceries","Housing","Transport","Utilities","Health","Entertainment","Savings","Other"];
export function emptyData() { return {format:"MoneyMateNZ",version:1,transactions:[],budgets:[],goals:[],bills:[],recurring:[]}; }
export function cents(raw) {
  const clean=String(raw??"").trim().replace(/^\$/,"").replace(/,/g,"").trim();
  if(!/^\d+(?:\.\d{1,2})?$/.test(clean)) throw Error("Enter a valid amount with up to two decimal places.");
  const [whole,decimal=""]=clean.split(".");
  const result=Number(whole)*100+Number(decimal.padEnd(2,"0"));
  if(!Number.isSafeInteger(result)||result<0) throw Error("Amount is too large.");
  return result;
}
export function nzd(value) {return new Intl.NumberFormat("en-NZ",{style:"currency",currency:"NZD"}).format(value/100);}
export function iso(date) {
  if(!/^\d{4}-\d{2}-\d{2}$/.test(String(date)))throw Error("Enter a valid date.");
  const d=new Date(date+"T00:00:00Z");
  if(!Number.isFinite(d.getTime())||d.toISOString().slice(0,10)!==date)throw Error("Invalid calendar date.");
  return date;
}
export function month(date){return iso(date).slice(0,7);}
export function nextId(items){return Math.max(0,...items.map(row=>Number(row.id)||0))+1;}
export function advance(date,frequency) {
  iso(date);const d=new Date(date+"T00:00:00Z");
  if(frequency==="Weekly")d.setUTCDate(d.getUTCDate()+7);
  else if(frequency==="Fortnightly")d.setUTCDate(d.getUTCDate()+14);
  else if(frequency==="Monthly") {
    const day=d.getUTCDate();
    d.setUTCDate(1);d.setUTCMonth(d.getUTCMonth()+1);
    const last=new Date(Date.UTC(d.getUTCFullYear(),d.getUTCMonth()+1,0)).getUTCDate();
    d.setUTCDate(Math.min(day,last));
  }else throw Error("Invalid repeat interval.");
  return d.toISOString().slice(0,10);
}
export function strictData(input) {
  if(!input||input.format!=="MoneyMateNZ"||input.version!==1)throw Error("Not a MoneyMate NZ backup.");
  const data=emptyData();
  const schema={
    transactions:{number:["id","cents"],text:["kind","category","note","date"]},
    budgets:{number:["cents"],text:["category"]},
    goals:{number:["id","target","saved"],text:["name"]},
    bills:{number:["id","cents"],text:["name","due"]},
    recurring:{number:["id","cents"],text:["kind","category","note","next_date","frequency"]}
  };
  for(const [table,fields] of Object.entries(schema)){
    const rows=input[table];
    if(!Array.isArray(rows)||rows.length>100000)throw Error("Invalid "+table+" in backup.");
    const seen=new Set();
    data[table]=rows.map(row=>{
      if(!row||typeof row!=="object"||Array.isArray(row))throw Error("Invalid "+table+" entry.");
      const allowed=[...fields.number,...fields.text];
      if(Object.keys(row).some(k=>!allowed.includes(k)))throw Error("Unexpected backup field.");
      const clean={};
      for(const key of fields.number) {
        if(!Number.isSafeInteger(row[key])||row[key]<0)throw Error("Invalid "+table+" amount.");
        clean[key]=row[key];
      }
      for(const key of fields.text){
        if(typeof row[key]!=="string"||row[key].length>2000)throw Error("Invalid "+table+" text.");
        clean[key]=row[key];
      }
      if(table!=="budgets"){
        if(clean.id<1||seen.has(clean.id))throw Error("Duplicate or invalid record ID.");
        seen.add(clean.id);
      }else{
        if(!clean.category.trim()||seen.has(clean.category))throw Error("Duplicate budget category.");
        seen.add(clean.category);
      }
      if(table==="transactions"){
        if(!["Income","Expense"].includes(clean.kind)||!clean.category.trim()||!clean.cents)throw Error("Invalid transaction.");
        iso(clean.date);
      }else if(table==="goals") {
        if(!clean.name.trim()||clean.target<1)throw Error("Invalid savings goal.");
      }else if(table==="bills"){
        if(!clean.name.trim()||clean.cents<1)throw Error("Invalid bill.");
        iso(clean.due);
      }else if(table==="recurring"){
        if(!["Income","Expense"].includes(clean.kind)||!["Weekly","Fortnightly","Monthly"].includes(clean.frequency)||!clean.category.trim()||clean.cents<1)
          throw Error("Invalid recurring entry.");
        iso(clean.next_date);
      }
      return clean;
    });
  }
  return data;
}
export function recordDue(data,today) {
  iso(today);
  const result=structuredClone(data);
  let created=0;
  for(const rule of result.recurring){
    let steps=0;
    while(rule.next_date<=today){
      if(++steps>1200||++created>10000)throw Error("Repeating schedule covers too many dates.");
      result.transactions.push({id:nextId(result.transactions),kind:rule.kind,category:rule.category,
        note:rule.note,cents:rule.cents,date:rule.next_date});
      rule.next_date=advance(rule.next_date,rule.frequency);
    }
  }
  return {data:result,created};
}
export function totals(data,yyyyMm) {
  const rows=data.transactions.filter(x=>x.date.slice(0,7)===yyyyMm);
  const income=rows.filter(x=>x.kind==="Income").reduce((a,x)=>a+x.cents,0);
  const expense=rows.filter(x=>x.kind==="Expense").reduce((a,x)=>a+x.cents,0);
  const categories=Object.entries(rows.filter(x=>x.kind==="Expense").reduce((o,x)=>(o[x.category]=(o[x.category]||0)+x.cents,o),{}))
    .sort((a,b)=>b[1]-a[1]);
  return {income,expense,remaining:income-expense,categories};
}
export function csv(data) {
  const quoted=s=>'"'+String(s).replace(/"/g,'""')+'"';
  return ["Date,Type,Category,Note,Amount NZD",...data.transactions.slice().sort((a,b)=>b.date.localeCompare(a.date)||b.id-a.id)
    .map(row=>[row.date,row.kind,row.category,row.note].map(quoted).join(",")+","+(row.cents/100).toFixed(2))].join("\r\n");
}
