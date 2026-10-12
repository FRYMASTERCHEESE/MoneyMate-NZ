/**
 * ANZ NZ's "Statement of Accounts" layout:
 * Statement period: 10 Sep 2024 - 09 Dec 2024
 * Date | Transaction type and details | Withdrawals | Deposits | Balance
 *
 * Dates often have NO YEAR and debit/credit amount columns may lose their
 * positions in text/OCR extraction. NEVER guess direction from an unsigned
 * amount: verify the right-most running balance against the previous balance.
 * Abort entire import if any row or page reconciliation is uncertain.
 */
const MONTHS=["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"];
const MONEY=/[-+]?(?:NZD\s*|\$)?(?:0|[1-9][0-9,]*)(?:\.[0-9]{2})/i;
const PAIR=new RegExp("\\s("+MONEY.source+")\\s+("+MONEY.source+")\\s*$","i");
const DATE_RE=/^(\d{1,2})\s+([A-Za-z]{3})\s+(?:(\d{4})\s+)?/;
const ANZ_HEADER=/\bdate\b.*\b(transaction|details)\b(?=.*\bwithdrawals?\b)(?=.*\bdeposits?\b).*\bbalance\b/i;
const PERIOD=/(\d{1,2})\s+([A-Za-z]{3})\s+(\d{4})\s*(?:-|–|—|to)\s*(\d{1,2})\s+([A-Za-z]{3})\s+(\d{4})/i;
const sanitize=s=>String(s).trim().replace(/\s+/g," ");
const quote=s=>'"'+String(s).replace(/"/g,'""')+'"';

function amountToCents(raw) {
 let value=sanitize(raw).replace(/^NZD\s*/i,"").replace(/^\$/,"").replace(/,/g,"");
 if(!/^[+-]?\d{1,13}\.\d{2}$/.test(value))throw Error("PDF contains an unsafe amount or balance.");
 const sign=value.startsWith("-")?-1:1;
 value=value.replace(/^[+-]/,"");
 const [whole,dec]=value.split(".");
 const cents=Number(whole)*100+Number(dec);
 if(!Number.isSafeInteger(cents))throw Error("Statement amount exceeds the safe range.");
 return sign*cents;
}
function dateFrom(day,abbr,year){
 const mi=MONTHS.findIndex(x=>x.toLowerCase()===abbr.toLowerCase());
 if(mi<0||!Number.isInteger(+day)||+day<1||+day>31||!/^\d{4}$/.test(String(year)))throw Error("Unsupported ANZ date.");
 const result=String(year).padStart(4,"0")+"-"+String(mi+1).padStart(2,"0")+"-"+String(day).padStart(2,"0");
 const js=new Date(result+"T00:00:00Z");
 if(!Number.isFinite(js.getTime())||js.toISOString().slice(0,10)!==result)throw Error("ANZ PDF has an invalid calendar date.");
 return result;
}
export function looksLikeAnzNzStatement(lines){
 return Array.isArray(lines)&&lines.some(x=>typeof x==="string"&&ANZ_HEADER.test(x));
}
export function anzNzdStatementToCsv(lines){
 if(!Array.isArray(lines)||!lines.length||lines.length>6000)throw Error("ANZ statement is empty or too long.");
 const values=lines.map(s=>typeof s==="string"?sanitize(s):"");
 if(values.some(s=>/\bAUD\b|\bAustralian dollars?\b/i.test(s)))
   throw Error("AUD detected. NZD budgets cannot import an AUD statement.");
 if(!looksLikeAnzNzStatement(values))throw Error("Not an ANZ Withdrawals/Deposits/Balance statement table.");
 const periodText=values.join(" ");
 const per=PERIOD.exec(periodText);
 if(!per)throw Error("ANZ statement period and year could not be found. Import stopped to avoid incorrect dates.");
 const start=dateFrom(per[1],per[2],per[3]),end=dateFrom(per[4],per[5],per[6]);
 if(start>end||Date.parse(end)-Date.parse(start)>370*86400000)
   throw Error("ANZ statement period is invalid or too long.");
 const years=[...new Set([+per[3],+per[6]])];
 let prev=null,started=false,rows=[],opened=false;
 for(const raw of values){
  if(ANZ_HEADER.test(raw)){started=true;continue;}
  if(!started)continue;
  const bf=/\b(balance brought forward|opening balance|balance carried forward)\b/i.test(raw);
  if(bf){
   const m=new RegExp("("+MONEY.source+")\\s*$","i").exec(raw);
   if(!m)throw Error("Opening or carried balance could not be read.");
   const value=amountToCents(m[1]);
   if(opened&&!/brought forward|carried forward/i.test(raw))throw Error("This PDF may contain multiple accounts. Import each separately.");
   if(prev!==null&&prev!==value)throw Error("Opening or carried balance disagrees with previous transactions.");
   prev=value;opened=true;
   continue;
  }
  const m=DATE_RE.exec(raw);
  if(!m)continue;
  if(/\b(totals?|closing balance|balance at)\b/i.test(raw))continue;
  const dates=years.map(y=>dateFrom(m[1],m[2],m[3]||String(y))).filter(x=>x>=start&&x<=end);
  const distinct=[...new Set(dates)];
  if(distinct.length!==1)throw Error("ANZ transaction year is ambiguous; no records were imported.");
  const rest=raw.slice(m[0].length).trim();
  const pair=PAIR.exec(" "+rest);
  if(!pair||!pair.index)throw Error("ANZ PDF row has unclear transaction amount or balance.");
  const details=rest.slice(0,pair.index).trim();
  if(!details||details.length>450)throw Error("ANZ PDF transaction description is missing or too long.");
  if(prev===null)throw Error("ANZ statement opening or carried balance is missing; cannot infer money in/out.");
  const amount=amountToCents(pair[1]),balance=amountToCents(pair[2]);
  const delta=balance-prev;
  if(amount<=0||delta===0||Math.abs(delta)!==amount)
   throw Error("ANZ transaction did not reconcile with the running balance. Nothing imported.");
  const kind=delta<0?"Expense":"Income";
  rows.push([distinct[0],kind,details,(amount/100).toFixed(2)]);
  prev=balance;
  if(rows.length>200)throw Error("For safety import 200 or fewer ANZ transactions per PDF.");
 }
 if(!rows.length)throw Error("No validated ANZ transactions found in this PDF.");
 return ["Date,Type,Description,Amount NZD",...rows.map(r=>r.map(quote).join(","))].join("\r\n");
}
