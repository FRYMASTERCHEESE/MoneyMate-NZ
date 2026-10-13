package nz.moneymate.app;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Locally reads NZD statement CSVs. Does not contact a bank or access credentials.
 * No database write occurs until the caller previews and confirms.
 */
public final class BankCsv {
    private BankCsv(){}
    public static final class Entry {
        public final String date,kind,category,note;
        public final long cents;
        Entry(String date,String kind,String category,String note,long cents) {
            this.date=date;this.kind=kind;this.category=category;this.note=note;this.cents=cents;
        }
        public String key(){return date+"\u0000"+kind+"\u0000"+(note.startsWith("Bank CSV (")?"":category)+"\u0000"+note+"\u0000"+cents;}
    }
    private static String norm(String s){return s.replace("\ufeff","").trim().toLowerCase(Locale.ROOT).replace('_',' ').replace('-',' ').replaceAll("\\s+"," ");}
    private static final String[][] COLUMNS={
        {"date","transaction date","posted date","posting date","value date","processed date"},
        {"description","transaction description","transaction details","details","narrative","particulars","merchant","payee","reference","memo"},
        {"amount","amount nzd","nzd amount","transaction amount","value"},
        {"debit","withdrawal","withdrawals","money out","paid out"},
        {"credit","deposit","deposits","money in","paid in"},
        {"type","transaction type","direction"},
        {"category"},
        {"note","notes"},
        {"currency","ccy"},
        {"status","transaction status"}
    };
    private static int find(List<String> header,int column) {
        int found=-1;
        for(int i=0;i<header.size();i++)for(String alias:COLUMNS[column])
            if(norm(header.get(i)).equals(alias)) {
                if(found!=-1)throw new IllegalArgumentException("Ambiguous CSV headers");
                found=i;break;
            }
        return found;
    }
    private static String cell(List<String> row,int at){return at<0?"":row.get(at).trim();}
    public static List<List<String>> cells(String raw) {
        if(raw==null||raw.length()>2_000_000)throw new IllegalArgumentException("CSV exceeds the 2 MB safety limit");
        List<List<String>> rows=new ArrayList<>();
        List<String> row=new ArrayList<>();
        StringBuilder field=new StringBuilder();boolean quoted=false,closed=false;
        for(int i=0;i<raw.length();i++) {
            char c=raw.charAt(i);
            if(quoted){
                if(c=='"'){
                    if(i+1<raw.length()&&raw.charAt(i+1)=='"'){field.append('"');i++;}
                    else {quoted=false;closed=true;}
                }else field.append(c);
            }else if(c=='"') {
                if(field.length()>0||closed)throw new IllegalArgumentException("Malformed CSV quoting");
                quoted=true;
            }else if(c==','){
                row.add(field.toString());field.setLength(0);closed=false;
                if(row.size()>40)throw new IllegalArgumentException("Too many CSV columns");
            }else if(c=='\r'||c=='\n'){
                row.add(field.toString());field.setLength(0);closed=false;
                boolean any=false;for(String s:row)if(!s.trim().isEmpty()){any=true;break;}
                if(any){rows.add(row);if(rows.size()>5001)throw new IllegalArgumentException("More than 5,000 CSV transactions");}
                row=new ArrayList<>();
                if(c=='\r'&&i+1<raw.length()&&raw.charAt(i+1)=='\n')i++;
            }else{
                if(closed&&!Character.isWhitespace(c))throw new IllegalArgumentException("Malformed CSV after a quote");
                if(!closed)field.append(c);
            }
            if(field.length()>1500)throw new IllegalArgumentException("CSV field too long");
        }
        if(quoted)throw new IllegalArgumentException("Unclosed CSV field");
        if(field.length()>0||!row.isEmpty()||closed){
            row.add(field.toString());rows.add(row);
        }
        if(rows.size()<2)throw new IllegalArgumentException("CSV needs a header and transaction rows");
        if(rows.size()>5001)throw new IllegalArgumentException("Too many CSV transactions");
        return rows;
    }
    private static String date(String raw){
        String s=raw.trim(),shortDate=s.split("\\s+")[0];
        String[] p=shortDate.split("[-/]");
        if(p.length!=3)throw new IllegalArgumentException("Unsupported date "+s);
        try{
            LocalDate d;
            if(p[0].length()==4)d=LocalDate.of(Integer.parseInt(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2]));
            else if(p[2].length()==4)d=LocalDate.of(Integer.parseInt(p[2]),Integer.parseInt(p[1]),Integer.parseInt(p[0]));
            else throw new IllegalArgumentException("Use four-digit years");
            return d.toString();
        }catch(java.time.DateTimeException | NumberFormatException error){
            throw new IllegalArgumentException("Invalid transaction date "+s);
        }
    }
    private static Long cents(String raw){
        String s=raw.trim().replace(",","").replaceFirst("^\\$","").replaceFirst("(?i)^NZD\\s*","");
        if(s.isEmpty())return null;
        boolean bracket=s.startsWith("(")&&s.endsWith(")");
        if(bracket)s="-"+s.substring(1,s.length()-1);
        if(!s.matches("[-+]?(0|[1-9][0-9]{0,11})(\\.[0-9]{1,2})?"))
            throw new IllegalArgumentException("Invalid NZD amount");
        try{return new BigDecimal(s).movePointRight(2).longValueExact();}
        catch(ArithmeticException e){throw new IllegalArgumentException("NZD amount too large");}
    }
    private static String category(String s){
        String t=s.toLowerCase(Locale.ROOT);
        if(t.matches(".*(countdown|new world|pak.?n.?save|paknsave|woolworths|coles|aldi|supermarket|grocery|four square).*"))return "Groceries";
        if(t.matches(".*(petrol|fuel|bp |z energy|caltex|mobil|shell|transport|uber|myki|opal|bus|train).*"))return "Transport";
        if(t.matches(".*(power|electric|energy|water|gas bill|internet|broadband|spark|telstra|optus|utilities).*"))return "Utilities";
        if(t.matches(".*(rent|mortgage|property|real estate|rates).*"))return "Housing";
        if(t.matches(".*(pharmacy|chemist|doctor|dentist|hospital|health).*"))return "Health";
        if(t.matches(".*(netflix|spotify|cinema|movies|gaming|entertainment).*"))return "Entertainment";
        return "Other";
    }
    public static List<Entry> parse(String raw,String accountLabel) {
        String label=accountLabel==null?"":accountLabel.trim();
        if(label.length()<1||label.length()>40||label.matches("(?s).*[\\p{Cntrl}].*"))
            throw new IllegalArgumentException("Account nickname must be 1–40 characters");
        List<List<String>> rows=cells(raw);
        List<String> header=rows.get(0);
        int[] ix=new int[COLUMNS.length];
        for(int j=0;j<ix.length;j++)ix[j]=find(header,j);
        if(ix[0]<0)throw new IllegalArgumentException("Missing Date column");
        if(ix[2]<0&&(ix[3]<0||ix[4]<0))throw new IllegalArgumentException("Missing Amount or Debit and Credit columns");
        if(ix[1]<0&&ix[7]<0)throw new IllegalArgumentException("Missing Description or Note column");
        if(ix[2]>=0&&(ix[3]>=0||ix[4]>=0))throw new IllegalArgumentException("Ambiguous bank amount columns");
        for(String h:header)if(norm(h).matches(".*\\b(aud|australian dollars?)\\b.*"))
            throw new IllegalArgumentException("AUD CSV not supported by NZD budgets");
        boolean ownExport=ix[1]<0&&ix[7]>=0&&ix[6]>=0&&ix[5]>=0&&ix[2]>=0&&norm(header.get(ix[2])).equals("amount nzd");
        List<Entry> result=new ArrayList<>();
        for(int i=1;i<rows.size();i++) {
            List<String> r=rows.get(i);
            if(r.size()!=header.size())throw new IllegalArgumentException("Row "+(i+1)+" column count differs from heading");
            if(ix[8]>=0&&!cell(r,ix[8]).equalsIgnoreCase("NZD"))throw new IllegalArgumentException("Cannot import AUD to NZD budgets");
            String status=cell(r,ix[9]);
            if(!status.isEmpty()&&!status.matches("(?i)posted|completed|settled|cleared"))
                throw new IllegalArgumentException("Row "+(i+1)+" is not settled");
            String desc=cell(r,ix[1]);if(desc.isEmpty())desc=cell(r,ix[7]);
            if(desc.isEmpty()&&!ownExport)throw new IllegalArgumentException("Missing description in row "+(i+1));
            String kind;long value;
            if(ix[2]>=0){
                Long amount=cents(cell(r,ix[2]));
                if(amount==null||amount==0)throw new IllegalArgumentException("Missing amount in row "+(i+1));
                String type=cell(r,ix[5]).toLowerCase(Locale.ROOT);
                if(type.matches("expense|debit|withdrawal|out|payment"))kind="Expense";
                else if(type.matches("income|credit|deposit|in"))kind="Income";
                else if(!type.isEmpty())throw new IllegalArgumentException("Unrecognised transaction type in row "+(i+1));
                else kind=amount<0?"Expense":"Income";
                if(amount<0&&kind.equals("Income"))throw new IllegalArgumentException("Negative income amount");
                value=Math.abs(amount);
            }else{
                Long debit=cents(cell(r,ix[3])),credit=cents(cell(r,ix[4]));
                if((debit!=null&&debit!=0)==(credit!=null&&credit!=0))throw new IllegalArgumentException("Row "+(i+1)+" needs Debit or Credit");
                kind=debit!=null&&debit!=0?"Expense":"Income";
                value=Math.abs(kind.equals("Expense")?debit:credit);
            }
            String note=ownExport?cell(r,ix[7]):"Bank CSV ("+label+"): "+desc.substring(0,Math.min(1400,desc.length()));
            String category=cell(r,ix[6]);if(category.isEmpty())category=category(desc);
            if(category.length()>2000)throw new IllegalArgumentException("Category is too long");
            result.add(new Entry(date(cell(r,ix[0])),kind,category,note,value));
        }
        return result;
    }
}
