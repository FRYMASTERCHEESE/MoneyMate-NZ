package nz.moneymate.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fail-closed parser for text extracted from NZD bank PDF statements.
 * No bank API calls, no passwords, no financial-data uploads.
 * OCR errors must be reviewed against the original statement.
 */
public final class PdfStatement {
    private PdfStatement(){}
    private static final String DATE="(?:\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}|\\d{1,2}[-/]\\d{1,2}[-/]\\d{4})";
    private static final String MONEY="[+\\-]?(?:\\$|NZD\\s*)?\\(?[0-9][0-9,]*\\.[0-9]{2}\\)?";
    private static final Pattern ROW=Pattern.compile("^("+DATE+")\\s+(.+?)\\s+("+MONEY+")(?:\\s+(DR|CR))?(?:\\s+("+MONEY+"))?\\s*$",Pattern.CASE_INSENSITIVE);
    private static final Pattern ROW_START=Pattern.compile("^"+DATE+"\\s");
    private static final Pattern HEADER_DATE=Pattern.compile("date",Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADER_DESCRIPTION=Pattern.compile("description|details|particulars|transaction|narrative",Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADER_AMOUNTS=Pattern.compile("amount|debit|credit|withdrawal|deposit",Pattern.CASE_INSENSITIVE);
    private static final Pattern AMBIGUOUS=Pattern.compile("debit|credit|withdrawal|deposit",Pattern.CASE_INSENSITIVE);
    private static String csv(String s){return "\""+s.replace("\"","\"\"")+"\"";}
    /** Does not permit partial import: an ambiguous transaction-like row rejects the whole statement. */
    public static String toCsv(List<String> lines) {
        if(lines==null||lines.isEmpty()||lines.size()>6000)
            throw new IllegalArgumentException("PDF has no readable statement rows or is too long");
        List<String> cleaned=new ArrayList<>();
        for(String line:lines){
            String s=line==null?"":line.trim().replaceAll("\\s+"," ");
            if(s.matches("(?i).*\\bAUD\\b.*")||s.matches("(?i).*\\bAustralian dollars?\\b.*"))
                throw new IllegalArgumentException("This statement mentions AUD. Only NZD is supported.");
            cleaned.add(s);
        }
        boolean separate=false,balance=false,validHeader=false,started=false;
        for(String line:cleaned)if(HEADER_DATE.matcher(line).find()&&HEADER_DESCRIPTION.matcher(line).find()
                &&HEADER_AMOUNTS.matcher(line).find()){
            validHeader=true;
            if(AMBIGUOUS.matcher(line).find())separate=true;
            if(line.toLowerCase(Locale.ROOT).contains("balance"))balance=true;
        }
        if(!validHeader)throw new IllegalArgumentException("Could not identify a supported PDF transaction table");
        List<String> out=new ArrayList<>();out.add("Date,Type,Description,Amount NZD");
        for(String line:cleaned){
            if(HEADER_DATE.matcher(line).find()&&HEADER_DESCRIPTION.matcher(line).find()
                    &&HEADER_AMOUNTS.matcher(line).find()){started=true;continue;}
            if(!started||!ROW_START.matcher(line).find())continue;
            if(line.toLowerCase(Locale.ROOT).matches(".*\\b(opening balance|closing balance|balance brought forward|balance carried forward)\\b.*"))continue;
            if(line.toLowerCase(Locale.ROOT).matches(".*\\b(pending|authorisation hold|uncleared)\\b.*"))
                throw new IllegalArgumentException("Pending transactions cannot be imported");
            Matcher m=ROW.matcher(line);
            if(!m.matches())throw new IllegalArgumentException("Unclear PDF transaction row: "+line.substring(0,Math.min(65,line.length())));
            String date=m.group(1),description=m.group(2),amount=m.group(3),direction=m.group(4),trailing=m.group(5);
            if(description.isEmpty()||description.length()>450)throw new IllegalArgumentException("Unclear PDF description");
            if(trailing!=null&&!balance)throw new IllegalArgumentException("Multiple amounts but no Balance heading");
            if(separate&&direction==null)throw new IllegalArgumentException("Debit/credit columns require a clear DR or CR marker");
            String normalized=amount.replaceFirst("(?i)^NZD\\s*","").replaceFirst("^\\$","").replace(",","");
            normalized=normalized.replace(",","");
            boolean minus=normalized.startsWith("-")||(normalized.startsWith("(")&&normalized.endsWith(")"));
            boolean plus=normalized.startsWith("+");
            if(direction==null&&!minus&&!plus)throw new IllegalArgumentException("Amount direction is unclear");
            String kind=direction==null?(minus?"Expense":"Income"):(direction.equalsIgnoreCase("DR")?"Expense":"Income");
            String digits=normalized.replaceAll("[()+-]","").replaceFirst("^\\$","");
            if(!digits.matches("\\d{1,12}\\.\\d{2}")||new java.math.BigDecimal(digits).signum()==0)
                throw new IllegalArgumentException("Invalid PDF transaction amount");
            out.add(csv(date)+","+csv(kind)+","+csv(description)+","+csv(digits));
            if(out.size()>201)throw new IllegalArgumentException("Import PDFs of 200 transactions or fewer");
        }
        if(out.size()<2)throw new IllegalArgumentException("No unambiguous PDF transactions were found");
        return String.join("\r\n",out);
    }
}
