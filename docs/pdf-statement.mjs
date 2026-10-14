import {looksLikeAnzNzStatement,anzNzdStatementToCsv} from "./anz-statement.mjs";
// Conservative free PDF statement importer. Financial PDFs are parsed ON THE DEVICE.
// Only unambiguous NZD rows with a signed amount or explicit DR/CR are accepted.
// Scanned PDFs may have OCR errors: always review a complete preview before committing.
const DATE = '(?:\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}|\\d{1,2}[-/]\\d{1,2}[-/]\\d{4})';
const MONEY = '[+\\-]?(?:\\$|NZD\\s*)?\\(?[0-9][0-9,]*\\.[0-9]{2}\\)?';
const ROW = new RegExp('^('+DATE+')\\s+(.+?)\\s+('+MONEY+')(?:\\s+(DR|CR))?(?:\\s+('+MONEY+'))?\\s*$','i');
const STARTS_DATE = new RegExp('^'+DATE+'\\s');
const MAX_LINES=6000;
const quote = s=>'"'+String(s).replace(/"/g,'""')+'"';
export function pdfStatementToCsv(lines){
 if(!Array.isArray(lines)||!lines.length||lines.length>MAX_LINES)throw Error("PDF has no readable statement rows or is too long.");
 // ANZ format has unsigned debit/credit columns. Reconcile against running balances;
 // never fall back to guessing if its rows fail the ANZ safety checks.
 if(looksLikeAnzNzStatement(lines))return anzNzdStatementToCsv(lines);
 const cleaned=lines.map(line=>typeof line==="string"?line.trim().replace(/\s+/g," "):"");
 if(cleaned.some(line=>/\bAUD\b|\bAustralian dollars?\b/i.test(line)))
   throw Error("This statement mentions AUD. MoneyMate NZ currently accepts NZD only.");
 const headers=cleaned.filter(line=>/date/i.test(line)&&/description|details|particulars|transaction|narrative/i.test(line)
   &&/amount|debit|credit|withdrawal|deposit/i.test(line));
 if(!headers.length)throw Error("Could not identify a supported statement table. No records were added.");
 const hasSeparateDirection=headers.some(line=>/debit|credit|withdrawal|deposit/i.test(line));
 const hasBalance=headers.some(line=>/\bbalance\b/i.test(line));
 const rows=[];let started=false;
 for(const raw of cleaned){
  if(headers.includes(raw)){started=true;continue;}
  if(!started||!STARTS_DATE.test(raw))continue;
  if(/\b(opening balance|closing balance|balance brought forward|balance carried forward)\b/i.test(raw))continue;
  if(/\b(pending|authorisation hold|uncleared)\b/i.test(raw))
    throw Error("PDF contains pending transaction rows. Export settled transactions only.");
  const match=ROW.exec(raw);
  if(!match)throw Error("A PDF transaction row was unclear: "+raw.slice(0,65)+". Nothing was imported.");
  const [,date,description,amount,direction,balance]=match;
  if(!description.trim()||description.length>450)
    throw Error("PDF description could not be read safely. Nothing was imported.");
  if(balance&&!hasBalance)
    throw Error("PDF has multiple monetary columns without a Balance heading. Nothing was imported.");
  if(hasSeparateDirection&&!direction)
    throw Error("PDF debit/credit columns need explicit DR or CR to import safely. Nothing was imported.");
  const normalized=amount.replace(/^NZD\s*/i,"").replace(/^\$/,"").replace(/,/g,"").trim();
  const minus=normalized.startsWith("-")||/^\(.*\)$/.test(normalized);
  const plus=normalized.startsWith("+");
  if(direction&&((minus&&direction.toUpperCase()==="CR")||(plus&&direction.toUpperCase()==="DR")))
    throw Error("PDF amount direction contradicts its DR/CR marker. Nothing was imported.");
  if(!direction&&!minus&&!plus)
    throw Error("PDF amount has no +, - or DR/CR direction. Nothing was imported.");
  const kind=direction?direction.toUpperCase()==="DR"?"Expense":"Income":minus?"Expense":"Income";
  const number=normalized.replace(/[()+-]/g,"").replace(/^\$/,"");
  if(!/^\d{1,12}\.\d{2}$/.test(number)||Number(number)===0)
    throw Error("PDF amount could not be interpreted. Nothing was imported.");
  rows.push([date,kind,description,number]);
  if(rows.length>5000)throw Error("PDF has more than 5,000 rows.");
 }
 if(!rows.length)throw Error("No unambiguous transaction rows found in this PDF. No records were added.");
 return ["Date,Type,Description,Amount NZD",...rows.map(r=>r.map(quote).join(","))].join("\r\n");
}

/** Fetches only open-source PDF.js executable code; PDF bytes stay in memory.
 * A static, pinned browser module is loaded on demand. No PDF file is uploaded.
 * Supports text PDFs; scanned pictures have no text layer.
 */
export async function pdfFileToLines(file){
 if(!file||file.size>4_000_000)throw Error("PDF must be 4 MB or smaller.");
 if(!/\.pdf$/i.test(file.name||"")&&file.type!=="application/pdf")throw Error("Please select a PDF statement.");
 const pdfjs=await import("https://cdnjs.cloudflare.com/ajax/libs/pdf.js/4.10.38/pdf.min.mjs");
 pdfjs.GlobalWorkerOptions.workerSrc="https://cdnjs.cloudflare.com/ajax/libs/pdf.js/4.10.38/pdf.worker.min.mjs";
 const bytes=new Uint8Array(await file.arrayBuffer());
 if(bytes[0]!==37||bytes[1]!==80||bytes[2]!==68||bytes[3]!==70)
   throw Error("Not a valid PDF file.");
 let document;
 try{
  document=await pdfjs.getDocument({data:bytes,isEvalSupported:false}).promise;
  if(document.numPages>12)throw Error("For safety, import PDF statements of 12 pages or fewer.");
  const output=[];
  for(let p=1;p<=document.numPages;p++){
   const page=await document.getPage(p);
   const text=await page.getTextContent();
   if(text.items.length>15000)throw Error("PDF has too much text to import safely.");
   const grouped=[];
   for(const item of text.items){
    if(typeof item.str!=="string"||!item.str.trim()||!item.transform)continue;
    const y=item.transform[5],x=item.transform[4];
    let group=grouped.find(g=>Math.abs(g.y-y)<2.25);
    if(!group){group={y,segments:[]};grouped.push(group);}
    group.segments.push({x,str:item.str});
   }
   grouped.sort((a,b)=>b.y-a.y);
   for(const group of grouped){
    group.segments.sort((a,b)=>a.x-b.x);
    output.push(group.segments.map(s=>s.str).join(" ").trim());
   }
  }
  if(!output.some(line=>line.trim().length>4))
    throw Error("This PDF is scanned or image-only. It cannot be read as text on the website.");
  return output;
 }catch(err){
  if(err?.name==="PasswordException")throw Error("Password-protected PDFs must be unlocked by your bank first.");
  throw err;
 }finally{if(document)await document.destroy();}
}
