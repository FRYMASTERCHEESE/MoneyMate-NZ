package nz.moneymate.app;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class PdfStatementTest {
    @org.junit.Test public void contradictoryDirectionNeverImports() {
        for(String amount:new String[]{"-42.10 CR","+42.10 DR","(42.10) CR"}){
            try {PdfStatement.toCsv(java.util.Arrays.asList("Date Description Amount","12/10/2026 Fuel "+amount));
                org.junit.Assert.fail("Contradictory direction accepted");}
            catch(IllegalArgumentException expected){org.junit.Assert.assertTrue(expected.getMessage().contains("direction"));}
        }
    }
    @Test public void signedAmountsAndBalanceStaySeparate(){
        String data=PdfStatement.toCsv(Arrays.asList(
            "NZD statement","Date Description Amount Balance",
            "12/10/2026 Countdown Matamata -52.30 1,125.70",
            "13/10/2026 Salary +1,000.00 2,125.70"));
        java.util.List<BankCsv.Entry> entries=BankCsv.parse(data,"Everyday");
        assertEquals(2,entries.size());
        assertEquals(5230L,entries.get(0).cents);
        assertEquals("Expense",entries.get(0).kind);
        assertEquals(100000L,entries.get(1).cents);
        assertEquals("Income",entries.get(1).kind);
    }
    @Test public void drCrOnlyWhenExplicit(){
        String data=PdfStatement.toCsv(Arrays.asList(
            "Date Description Debit Credit Balance",
            "12/10/2026 Fuel 42.10 DR 1,100.50",
            "13/10/2026 Salary 1000.00 CR 2,100.50"));
        assertEquals("Expense",BankCsv.parse(data,"Everyday").get(0).kind);
        assertEquals("Income",BankCsv.parse(data,"Everyday").get(1).kind);
    }
    @Test public void rejectsUncertainRowsInsteadOfImportingPartially(){
        java.util.List<java.util.List<String>> samples=Arrays.asList(
          Arrays.asList("Date Description Debit Credit Balance","12/10/2026 Grocery 30.20 130.00"),
          Arrays.asList("Date Description Amount Balance","12/10/2026 Grocery 30.20 130.00"),
          Arrays.asList("Date Description Amount","12/10/2026 Grocery -20.00","13/10/2026 Invalid text"),
          Arrays.asList("Date Description Amount","12/10/2026 Pending -20.00"),
          Arrays.asList("Statement AUD","Date Description Amount","12/10/2026 Fuel -20.00")
        );
        for(java.util.List<String> sample:samples)try{PdfStatement.toCsv(sample);fail("Unclear PDF must not import");}
        catch(IllegalArgumentException expected){}
    }
}
