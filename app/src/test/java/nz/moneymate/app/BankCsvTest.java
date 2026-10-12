package nz.moneymate.app;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class BankCsvTest {
    @Test public void signedNzdAndMerchantCategory(){
        List<BankCsv.Entry> rows=BankCsv.parse("Date,Amount,Description,Currency\r\n12/10/2026,-50.25,\"PAK'nSAVE, Matamata\",NZD\r\n13/10/2026,300.10,Salary,NZD\r\n","Everyday");
        assertEquals(2,rows.size());
        assertEquals("2026-10-12",rows.get(0).date);
        assertEquals("Expense",rows.get(0).kind);
        assertEquals("Groceries",rows.get(0).category);
        assertEquals(5025,rows.get(0).cents);
        assertEquals("Bank CSV (Everyday): PAK'nSAVE, Matamata",rows.get(0).note);
        assertEquals("Income",rows.get(1).kind);
        assertEquals(30010,rows.get(1).cents);
    }
    @Test public void debitCreditAndMoneyMateExport(){
        List<BankCsv.Entry> rows=BankCsv.parse("Date,Debit,Credit,Details\n01/02/2026,12.34,,Electric power\n02/02/2026,,99.50,Salary\n","Daily");
        assertEquals("2026-02-01",rows.get(0).date);
        assertEquals(1234,rows.get(0).cents);
        assertEquals("Expense",rows.get(0).kind);
        assertEquals("Income",rows.get(1).kind);
        assertEquals(9950,rows.get(1).cents);
        assertEquals("Expense",BankCsv.parse("Date,Type,Category,Note,Amount NZD\n2026-10-12,Expense,Housing,Rent,560.00\n","Daily").get(0).kind);
    }
    @Test public void duplicateKeyStableOnlyWithinSameBankNickname(){
        BankCsv.Entry one=BankCsv.parse("Date,Amount,Description\n2026-10-12,-5.00,Cafe\n","Daily").get(0);
        BankCsv.Entry same=BankCsv.parse("Date,Amount,Description\n2026-10-12,-5.00,Cafe\n","Daily").get(0);
        BankCsv.Entry other=BankCsv.parse("Date,Amount,Description\n2026-10-12,-5.00,Cafe\n","Savings").get(0);
        assertEquals(one.key(),same.key());
        assertNotEquals(one.key(),other.key());
    }
    @Test public void blockAudAndBadDatesAndPendingTransactions(){
        String[] bad={
           "Date,Amount AUD,Description\n2026-10-12,-5,Cafe\n",
           "Date,Amount,Description,Currency\n2026-10-12,-5,Cafe,AUD\n",
           "Date,Amount,Description\n2026-02-30,-5,Cafe\n",
           "Date,Amount,Description\n2026-10-12,1.999,Cafe\n",
           "Date,Amount,Description,Status\n2026-10-12,-5,Cafe,pending\n",
           "Date,Amount,Description\n2026-10-12,\"-5,Cafe\n"
        };
        for(String csv:bad)try {BankCsv.parse(csv,"Everyday");fail("Should refuse invalid CSV");}
        catch(IllegalArgumentException expected){}
    }
    @Test public void quotedCells(){
        assertEquals("Said \"hello\",\ntoday",BankCsv.cells("Date,Amount,Description\n2026-10-12,-2,\"Said \"\"hello\"\",\ntoday\"\n").get(1).get(2));
    }
}
