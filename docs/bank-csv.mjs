// Free, fully offline bank-statement CSV import (NZD only).
// No bank connection, credentials, cookies or third-party API required.
// Use the SAME account nickname each time to recognise previously imported rows.
const MAX_BYTES = 2_000_000;
const MAX_ROWS = 5000;
const MAX_COLUMNS = 40;
const FIELD_LIMIT = 1500;
const HEADER_ALIASES = {
  date:["date","transaction date","posted date","posting date","value date","processed date"],
  description:["description","transaction description","transaction details","details","narrative","particulars","merchant","payee","reference","memo"],
  amount:["amount","amount nzd","nzd amount","transaction amount","value"],
  debit:["debit","withdrawal","withdrawals","money out","paid out"],
  credit:["credit","deposit","deposits","money in","paid in"],
  type:["type","transaction type","direction"],
  category:["category"],
  note:["note","notes"],
  currency:["currency","ccy"],
  status:["status","transaction status"]
};
const csvKey=value=>String(value).replace(/^\uFEFF/,"").trim().toLowerCase().replace(/[_-]+/g," ").replace(/\s+/g," ");
const emptyCell=s=>s.trim()==="";
export function parseCsv(text) {
  if(typeof text!=="string"||new TextEncoder().encode(text).length>MAX_BYTES)throw Error("CSV must be smaller than 2 MB.");
  const rows=[];let cells=[],field="",quoted=false,closed=false;
  const pushCell=()=>{
    if(field.length>FIELD_LIMIT)throw Error("CSV field is too long.");
    if(cells.length>=MAX_COLUMNS)throw Error("CSV has too many columns.");
    cells.push(field);field="";closed=false;
  };
  const pushRow=()=>{
    pushCell();
    if(cells.some(cell=>!emptyCell(cell))){
      if(rows.length>=MAX_ROWS+1)throw Error("CSV has more than 5,000 transactions.");
      rows.push(cells);
    }
    cells=[];
  };
  for(let i=0;i<text.length;i++){
    const c=text[i];
    if(quoted){
      if(c==='"'){
        if(text[i+1]==='"'){field+='"';i++;}
        else {quoted=false;closed=true;}
      }else field+=c;
    }else if(c==='"'){
      if(field!==""||closed)throw Error("Malformed CSV quotation.");
      quoted=true;
    }else if(c===',')pushCell();
    else if(c==='\n'||c==='\r'){
      pushRow();
      if(c==='\r'&&text[i+1]==='\n')i++;
    }else {
      if(closed&&!/\s/.test(c))throw Error("Malformed CSV after a quoted field.");
      if(!closed)field+=c;
    }
    if(field.length>FIELD_LIMIT)throw Error("CSV field is too long.");
  }
  if(quoted)throw Error("Unclosed CSV quotation.");
  if(field!==""||cells.length||closed)pushRow();
  if(rows.length<2)throw Error("CSV needs a header and at least one transaction.");
  return rows;
}
function col(headers,name){
  const matches=[];
  for(let i=0;i<headers.length;i++)if(HEADER_ALIASES[name].includes(csvKey(headers[i])))matches.push(i);
  if(matches.length>1)throw Error("Ambiguous CSV "+name+" column.");
  return matches.length?matches[0]:-1;
}
function dateOf(raw){
  const s=String(raw).trim();
  let year,month,day;
  let m=/^(\d{4})[-/](\d{1,2})[-/](\d{1,2})(?:\s+.*)?$/.exec(s);
  if(m)[,year,month,day]=m;
  else {
    m=/^(\d{1,2})[-/](\d{1,2})[-/](\d{4})(?:\s+.*)?$/.exec(s);
    if(!m)throw Error("Unsupported transaction date: "+s.slice(0,32));
    [,day,month,year]=m;
  }
  const iso=[year,String(month).padStart(2,"0"),String(day).padStart(2,"0")].join("-");
  const dt=new Date(iso+"T00:00:00Z");
  if(!Number.isFinite(dt.getTime())||dt.toISOString().slice(0,10)!==iso)throw Error("Invalid transaction date.");
  return iso;
}
function money(raw){
  let s=String(raw).trim().replace(/^\$/,"").replace(/^NZD\s*/i,"").replace(/,/g,"").trim();
  if(!s)return null;
  let neg=false;
  if(/^\(.*\)$/.test(s)){neg=true;s=s.slice(1,-1);}
  if(s.startsWith("-")){neg=true;s=s.slice(1);}
  else if(s.startsWith("+"))s=s.slice(1);
  if(!/^(?:0|[1-9]\d{0,11})(?:\.\d{1,2})?$/.test(s))throw Error("Invalid NZD amount.");
  const [major,dec=""]=s.split(".");
  const cents=Number(major)*100+Number(dec.padEnd(2,"0"));
  if(!Number.isSafeInteger(cents))throw Error("Amount is too large.");
  return neg?-cents:cents;
}
function categoryOf(text){
  const s=text.toLowerCase();
  if(/\b(countdown|new world|pak.?n.?save|paknsave|woolworths|col(e|u)s|aldi|iga|four square|supermarket|grocery|foodstuffs)\b/i.test(s))return "Groceries";
  if(/\b(petrol|fuel|bp |z energy|caltex|mobil|shell|transport|uber|myki|opal|at hop|bus|train)\b/i.test(s))return "Transport";
  if(/\b(power|electric|energy|water|gas bill|internet|broadband|spark|one nz|telstra|optus|utilities)\b/i.test(s))return "Utilities";
  if(/\b(rent|mortgage|property|real estate|rates)\b/i.test(s))return "Housing";
  if(/\b(pharmacy|chemist|doctor|dentist|hospital|health)\b/i.test(s))return "Health";
  if(/\b(netflix|spotify|cinema|movies|gaming|entertainment)\b/i.test(s))return "Entertainment";
  return "Other";
}
function fingerprint(r){
  // Reviewed categories can change; the bank's source identity does not.
  return [r.date,r.kind,r.cents,/^Bank CSV \(/.test(r.note)?"":r.category,r.note].join("\u0000");
}
export function planBankCsv(text,existing=[],accountLabel="My bank account"){
  const label=String(accountLabel).trim();
  if(!label||label.length>40||/[\u0000-\u001f\u007f]/.test(label))throw Error("Use a bank nickname of 1–40 characters.");
  const rows=parseCsv(text);const header=rows[0];const indexes=Object.fromEntries(Object.keys(HEADER_ALIASES).map(k=>[k,col(header,k)]));
  if(indexes.date<0)throw Error("Missing Date column.");
  if(indexes.amount<0&&(indexes.debit<0||indexes.credit<0))throw Error("Need Amount or both Debit and Credit columns.");
  if(indexes.description<0&&indexes.note<0)throw Error("Missing Description or Note column.");
  if(indexes.amount>=0&&(indexes.debit>=0||indexes.credit>=0))throw Error("Choose an export with either Amount or Debit/Credit columns, not both.");
  if(header.some(h=>/\b(aud|australian dollars?)\b/i.test(h)))throw Error("AUD statement not imported: MoneyMate budgets currently use NZD only.");
  const ownExport=indexes.description<0&&indexes.note>=0&&indexes.category>=0&&indexes.type>=0&&csvKey(header[indexes.amount])==="amount nzd";
  const existingCounts=new Map();
  for(const row of existing){
    if(!row||!Number.isSafeInteger(row.cents))continue;
    const key=fingerprint(row);existingCounts.set(key,(existingCounts.get(key)||0)+1);
  }
  const seen=new Map(),toAdd=[];let skipped=0;
  for(let i=1;i<rows.length;i++){
    const raw=rows[i];
    if(raw.length!==header.length)throw Error("Row "+(i+1)+" has the wrong number of columns.");
    const field=k=>indexes[k]<0?"":raw[indexes[k]].trim();
    if(indexes.currency>=0&&field("currency").toUpperCase()!=="NZD")throw Error("Row "+(i+1)+" is not NZD. AUD cannot be added to NZD totals.");
    if(indexes.status>=0&&field("status")&&!/^(posted|completed|settled|cleared)$/i.test(field("status")))
      throw Error("Row "+(i+1)+" is pending or unrecognised. Export settled transactions only.");
    const description=field("description")||field("note");
    if(!description&&!ownExport)throw Error("Row "+(i+1)+" has no description.");
    let amount,kind;
    if(indexes.amount>=0){
      amount=money(field("amount"));
      if(amount===null||amount===0)throw Error("Row "+(i+1)+" has no amount.");
      const rawType=field("type").toLowerCase();
      if(rawType&&/^(expense|debit|withdrawal|out|payment)$/.test(rawType))kind="Expense";
      else if(rawType&&/^(income|credit|deposit|in)$/.test(rawType))kind="Income";
      else if(rawType)throw Error("Row "+(i+1)+" has an unrecognised transaction type.");
      else kind=amount<0?"Expense":"Income";
      // MoneyMate exports expense rows with an unsigned positive Amount NZD column.
      if(amount<0&&kind==="Income")throw Error("Row "+(i+1)+" income has a negative amount.");
    }else{
      const debit=money(field("debit")),credit=money(field("credit"));
      if((debit!==null&&debit!==0)===(credit!==null&&credit!==0))throw Error("Row "+(i+1)+" needs exactly one Debit or Credit.");
      kind=debit!==null&&debit!==0?"Expense":"Income";
      amount=kind==="Expense"?-Math.abs(debit):Math.abs(credit);
    }
    const note=ownExport?field("note"):"Bank CSV ("+label+"): "+description.slice(0,1400);
    const record={date:dateOf(field("date")),kind,category:field("category")||categoryOf(description),note,cents:Math.abs(amount)};
    const key=fingerprint(record),ordinal=(seen.get(key)||0)+1;
    seen.set(key,ordinal);
    if(ordinal<=(existingCounts.get(key)||0))skipped++;
    else toAdd.push(record);
  }
  if(!rows.length||seen.size===0)throw Error("No transactions in CSV.");
  return {toAdd,skipped,total:rows.length-1,accountLabel:label};
}
