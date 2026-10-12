package nz.moneymate.app;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** ANZ New Zealand statement table with unsigned Withdrawals, Deposits and Balance.
 * Never infer income or expense from wording. Verify the money movement matches
 * the difference of two successive statement running balances, cent for cent.
 */
public final class AnzStatement {
    private AnzStatement(){}
    private static final String MONEY="[-+]?(?:NZD\\s*|\\$)?(?:0|[1-9][0-9,]*)(?:\\.[0-9]{2})";
    private static final Pattern HEADER=Pattern.compile("\\bdate\\b.*\\b(transaction|details)\\b.*\\bwithdrawals?\\b.*\\bdeposits?\\b.*\\bbalance\\b",Pattern.CASE_INSENSITIVE);
    private static final Pattern PERIOD=Pattern.compile("(\\d{1,2})\\s+([A-Za-z]{3})\\s+(\\d{4})\\s*(?:-|–|—|to)\\s*(\\d{1,2})\\s+([A-Za-z]{3})\\s+(\\d{4})",Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE=Pattern.compile("^(\\d{1,2})\\s+([A-Za-z]{3})\\s+(?:(\\d{4})\\s+)?");
    private static final Pattern PAIR=Pattern.compile("\\s("+MONEY+")\\s+("+MONEY+")\\s*$",Pattern.CASE_INSENSITIVE);
    private static final Pattern LAST_MONEY=Pattern.compile("("+MONEY+")\\s*$",Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter FULL=DateTimeFormatter.ofPattern("d MMM uuuu",Locale.ENGLISH);
    private static String cleaned(String s){return s==null?"":s.trim().replaceAll("\\s+"," ");}
    private static String quoted(String s){return "\""+s.replace("\"","\"\"")+"\"";}
    private static LocalDate date(String day,String month,String year){
        try{return LocalDate.parse(day+" "+month.substring(0,1).toUpperCase(Locale.ROOT)
            +month.substring(1).toLowerCase(Locale.ROOT)+" "+year,FULL);}
        catch(DateTimeParseException | IndexOutOfBoundsException ex){throw new IllegalArgumentException("Unclear or impossible ANZ date");}
    }
    private static long cents(String text){
        String s=cleaned(text).replaceFirst("(?i)^NZD\\s*","").replaceFirst("^\\$","").replace(",","");
        if(!s.matches("[+-]?\\d{1,13}\\.\\d{2}"))throw new IllegalArgumentException("Invalid amount or balance in ANZ PDF");
        try{return new java.math.BigDecimal(s).movePointRight(2).longValueExact();}
        catch(ArithmeticException ex){throw new IllegalArgumentException("ANZ amount is too large");}
    }
    static boolean matches(List<String> lines){
        for(String line:lines)if(line!=null&&HEADER.matcher(line).find())return true;
        return false;
    }
    public static String toCsv(List<String> lines){
        if(lines==null||lines.isEmpty()||lines.size()>6000)
            throw new IllegalArgumentException("ANZ statement empty or too long");
        List<String> cleaned=new ArrayList<>();
        for(String line:lines){
            String s=cleaned(line);
            if(s.matches("(?i).*\\bAUD\\b.*")||s.matches("(?i).*\\bAustralian dollars?\\b.*"))
                throw new IllegalArgumentException("AUD statement not supported by NZD budgets");
            cleaned.add(s);
        }
        if(!matches(cleaned))throw new IllegalArgumentException("Not an ANZ withdrawals/deposits/balance table");
        Matcher period=PERIOD.matcher(String.join(" ",cleaned));
        if(!period.find())throw new IllegalArgumentException("ANZ period is missing or ambiguous; no entries saved");
        LocalDate begin=date(period.group(1),period.group(2),period.group(3));
        LocalDate end=date(period.group(4),period.group(5),period.group(6));
        if(begin.isAfter(end)||java.time.temporal.ChronoUnit.DAYS.between(begin,end)>370)
            throw new IllegalArgumentException("Invalid ANZ statement period");
        LinkedHashSet<Integer> years=new LinkedHashSet<>(Arrays.asList(begin.getYear(),end.getYear()));
        Long previous=null;
        boolean started=false,opened=false;
        List<String> out=new ArrayList<>();out.add("Date,Type,Description,Amount NZD");
        for(String line:cleaned){
            if(HEADER.matcher(line).find()){started=true;continue;}
            if(!started)continue;
            if(line.matches("(?i).*\\b(opening balance|balance brought forward|balance carried forward)\\b.*")){
                Matcher m=LAST_MONEY.matcher(line);
                if(!m.find())throw new IllegalArgumentException("Missing opening or carried balance");
                long balance=cents(m.group(1));
                if(opened&&!line.toLowerCase(Locale.ROOT).contains("brought forward"))
                    throw new IllegalArgumentException("Multiple accounts or statements: import one account at a time");
                if(previous!=null&&previous.longValue()!=balance)
                    throw new IllegalArgumentException("Balance brought forward did not reconcile");
                previous=balance;opened=true;continue;
            }
            Matcher m=DATE.matcher(line);
            if(!m.find())continue;
            if(line.matches("(?i).*\\b(totals?|closing balance|balance at)\\b.*"))continue;
            LocalDate valid=null;
            for(int year:years){
                LocalDate candidate=date(m.group(1),m.group(2),m.group(3)!=null?m.group(3):Integer.toString(year));
                if(!candidate.isBefore(begin)&&!candidate.isAfter(end)){
                    if(valid!=null&&!candidate.equals(valid))
                        throw new IllegalArgumentException("Transaction year is ambiguous");
                    valid=candidate;
                }
            }
            if(valid==null)throw new IllegalArgumentException("ANZ date outside statement period");
            String rest=line.substring(m.end()).trim();
            Matcher pair=PAIR.matcher(" "+rest);
            if(!pair.find())throw new IllegalArgumentException("Unclear transaction amount and running balance in ANZ PDF");
            String description=rest.substring(0,Math.min(rest.length(),pair.start())).trim();
            if(description.isEmpty()||description.length()>450)
                throw new IllegalArgumentException("Unclear ANZ transaction description");
            if(previous==null)throw new IllegalArgumentException("Opening or carried ANZ balance not found");
            long amount=cents(pair.group(1)),balance=cents(pair.group(2));
            long delta;
            try{delta=Math.subtractExact(balance,previous);}catch(ArithmeticException e){throw new IllegalArgumentException("ANZ balance overflow");}
            if(amount<=0||delta==0||Math.abs(delta)!=amount)
                throw new IllegalArgumentException("ANZ transaction fails running-balance check; no entries imported");
            String kind=delta<0?"Expense":"Income";
            out.add(quoted(valid.toString())+","+quoted(kind)+","+quoted(description)+","+
                quoted(String.format(Locale.ROOT,"%d.%02d",amount/100,amount%100)));
            previous=balance;
            if(out.size()>201)throw new IllegalArgumentException("Import PDFs of 200 ANZ transactions or fewer");
        }
        if(out.size()<2)throw new IllegalArgumentException("No safely reconciled ANZ transactions");
        return String.join("\r\n",out);
    }
}
