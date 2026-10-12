package nz.moneymate.app;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class AnzStatementTest {
    private final List<String> statement=Arrays.asList(
        "ANZ Bank New Zealand Limited",
        "Statement period 10 Sep 2024 - 09 Dec 2024",
        "Date Transaction type and details Withdrawals Deposits Balance",
        "10 Sep Opening balance 9,656.67",
        "10 Sep AP Transfer from family 5.00 9,661.67",
        "10 Sep EP Countdown NZ 7.00 9,654.67",
        "11 Sep Fuel BP 7.95 9,646.72"
    );
    @Test public void anzNzSignedByBalancesNotMerchantText() {
        String csv=PdfStatement.toCsv(statement);
        List<BankCsv.Entry> rows=BankCsv.parse(csv,"ANZ Everyday");
        assertEquals(3,rows.size());
        assertEquals("Income",rows.get(0).kind);
        assertEquals(500L,rows.get(0).cents);
        assertEquals("2024-09-10",rows.get(0).date);
        assertEquals("Expense",rows.get(1).kind);
        assertEquals(700L,rows.get(1).cents);
        assertEquals(795L,rows.get(2).cents);
    }
    @Test public void multipleYearRolloverSafe() {
        String csv=AnzStatement.toCsv(Arrays.asList(
            "Statement period 01 Dec 2025 - 31 Jan 2026",
            "Date Transaction type and details Deposits Withdrawals Balance",
            "01 Dec Opening balance 100.00",
            "31 Dec Fuel 20.00 80.00",
            "01 Jan Salary 50.00 130.00"));
        List<BankCsv.Entry> rows=BankCsv.parse(csv,"ANZ Everyday");
        assertEquals("2025-12-31",rows.get(0).date);
        assertEquals("2026-01-01",rows.get(1).date);
    }
    @Test public void rejectsBalanceMismatchOrMissingPeriod() {
        List<List<String>> bad=Arrays.asList(
            Arrays.asList("Date Transaction type and details Withdrawals Deposits Balance","01 Sep Opening balance 100.00","02 Sep Coffee 5.00 95.00"),
            Arrays.asList("Statement period 01 Sep 2024 - 30 Sep 2024","Date Transaction type and details Withdrawals Deposits Balance","02 Sep Coffee 5.00 95.00"),
            Arrays.asList("Statement period 01 Sep 2024 - 30 Sep 2024","Date Transaction type and details Withdrawals Deposits Balance","01 Sep Opening balance 100.00","02 Sep Coffee 5.00 90.00")
        );
        for(List<String> lines:bad)try{PdfStatement.toCsv(lines);fail("Balance or period should be rejected");}
        catch(IllegalArgumentException expected){}
    }
}
